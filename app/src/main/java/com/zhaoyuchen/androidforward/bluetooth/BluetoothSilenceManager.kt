package com.zhaoyuchen.androidforward.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.zhaoyuchen.androidforward.data.AppSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 蓝牙静默规则：连接到用户选中的蓝牙设备时，自动转发不再发送 Bark。
 */
object BluetoothSilenceManager {
    private const val PREFS_NAME = "android_forward_bluetooth"
    private const val KEY_CONNECTED_ADDRESSES = "connected_addresses"
    private const val PROFILE_QUERY_TIMEOUT_MS = 1_200L

    /** Android 12 以后读取蓝牙设备信息需要运行时 BLUETOOTH_CONNECT 权限。 */
    fun hasBluetoothPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    /** 设置页读取已配对设备；没有权限时返回空列表，由 UI 引导用户授权。 */
    fun listBondedDevices(context: Context): List<BluetoothDeviceInfo> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val devices = getBondedDevices(context)
        val connectedAddresses = queryConnectedAddresses(context, allowProfileQuery = false)
        return devices
            .map { device ->
                BluetoothDeviceInfo(
                    name = readDeviceName(device),
                    address = readDeviceAddress(device),
                    connected = connectedAddresses.contains(readDeviceAddress(device))
                )
            }
            .filter { it.address.isNotBlank() }
            .sortedWith(compareByDescending<BluetoothDeviceInfo> { it.connected }.thenBy { it.name })
    }

    /** 判断当前是否应该静默，并返回命中的设备名称。 */
    fun findConnectedMutedDevice(context: Context, settings: AppSettings): String? {
        if (!settings.bluetoothSilenceEnabled) return null
        if (settings.mutedBluetoothAddresses.isEmpty()) return null
        if (!hasBluetoothPermission(context)) return null

        val connectedAddresses = queryConnectedAddresses(context, allowProfileQuery = true)
        val mutedAddress = settings.mutedBluetoothAddresses.firstOrNull { address ->
            connectedAddresses.contains(address)
        } ?: return null

        return getBondedDevices(context)
            .firstOrNull { readDeviceAddress(it) == mutedAddress }
            ?.let { readDeviceName(it) }
            ?: mutedAddress
    }

    /** 连接状态变化后异步刷新缓存，避免广播接收器阻塞主线程。 */
    fun refreshConnectedDeviceCacheAsync(context: Context) {
        val appContext = context.applicationContext
        Thread {
            queryConnectedAddresses(appContext, allowProfileQuery = true)
        }.start()
    }

    /** 查询当前连接地址，反射读取直连状态，必要时补充 A2DP/Headset/Profile 查询。 */
    private fun queryConnectedAddresses(context: Context, allowProfileQuery: Boolean): Set<String> {
        if (!hasBluetoothPermission(context)) return emptySet()
        val directAddresses = getBondedDevices(context)
            .filter { isConnectedByReflection(it) }
            .map { readDeviceAddress(it) }
            .filter { it.isNotBlank() }
            .toSet()
        val cachedAddresses = loadCachedConnectedAddresses(context)
        val profileAddresses = if (allowProfileQuery) {
            queryProfileConnectedAddresses(context)
        } else {
            emptySet()
        }
        val result = directAddresses + cachedAddresses + profileAddresses
        saveConnectedAddresses(context, result)
        return result
    }

    /** 读取已配对设备，所有异常都降级为空列表，避免权限变化导致崩溃。 */
    private fun getBondedDevices(context: Context): Set<BluetoothDevice> {
        return runCatching {
            getAdapter(context)?.bondedDevices ?: emptySet()
        }.getOrDefault(emptySet())
    }

    private fun getAdapter(context: Context): BluetoothAdapter? {
        return context.applicationContext
            .getSystemService(BluetoothManager::class.java)
            ?.adapter
    }

    /** 使用蓝牙 Profile 查询已连接设备，覆盖反射不可用的系统。 */
    private fun queryProfileConnectedAddresses(context: Context): Set<String> {
        val adapter = getAdapter(context) ?: return emptySet()
        val profiles = buildList {
            add(BluetoothProfile.A2DP)
            add(BluetoothProfile.HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(BluetoothProfile.HEARING_AID)
            }
        }
        val latch = CountDownLatch(profiles.size)
        val addresses = linkedSetOf<String>()
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                runCatching {
                    proxy.connectedDevices
                        .map { readDeviceAddress(it) }
                        .filter { it.isNotBlank() }
                        .forEach { address -> synchronized(addresses) { addresses.add(address) } }
                    adapter.closeProfileProxy(profile, proxy)
                }
                latch.countDown()
            }

            override fun onServiceDisconnected(profile: Int) {
                latch.countDown()
            }
        }

        profiles.forEach { profile ->
            val accepted = runCatching {
                adapter.getProfileProxy(context.applicationContext, listener, profile)
            }.getOrDefault(false)
            if (!accepted) latch.countDown()
        }
        latch.await(PROFILE_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return synchronized(addresses) { addresses.toSet() }
    }

    /** 通过隐藏方法读取连接状态，失败时交给 Profile 查询和缓存兜底。 */
    private fun isConnectedByReflection(device: BluetoothDevice): Boolean {
        return runCatching {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun readDeviceName(device: BluetoothDevice): String {
        return runCatching { device.name?.ifBlank { null } }
            .getOrNull()
            ?: "未知蓝牙设备"
    }

    private fun readDeviceAddress(device: BluetoothDevice): String {
        return runCatching { device.address.orEmpty() }.getOrDefault("")
    }

    private fun loadCachedConnectedAddresses(context: Context): Set<String> {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_CONNECTED_ADDRESSES, emptySet())
            ?: emptySet()
    }

    private fun saveConnectedAddresses(context: Context, addresses: Set<String>) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CONNECTED_ADDRESSES, addresses)
            .apply()
    }
}
