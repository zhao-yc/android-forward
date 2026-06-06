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
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit

/**
 * 蓝牙静默规则：连接到用户选中的蓝牙设备时，自动转发不再发送 Bark。
 */
object BluetoothSilenceManager {
    private const val PREFS_NAME = "android_forward_bluetooth"
    private const val KEY_CONNECTED_ADDRESSES = "connected_addresses"
    private const val PROFILE_QUERY_TIMEOUT_MS = 1_200L
    private val connectionStateListeners = CopyOnWriteArraySet<() -> Unit>()

    /** 设置页可见期间注册监听器，连接快照变化后用于实时刷新界面。 */
    fun addConnectionStateListener(listener: () -> Unit) {
        connectionStateListeners.add(listener)
    }

    /** 设置页离开时移除监听器，避免持有已经销毁的 Compose 状态。 */
    fun removeConnectionStateListener(listener: () -> Unit) {
        connectionStateListeners.remove(listener)
    }

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
        val connectedAddresses = queryConnectedAddresses(context, allowProfileQuery = false)
        return buildDeviceInfoList(context, connectedAddresses)
    }

    /**
     * 设置页手动刷新时使用。调用方必须在后台线程执行，等待 Profile 查询完成后返回最新快照。
     */
    fun refreshAndListBondedDevices(context: Context): List<BluetoothDeviceInfo> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val connectedAddresses = queryConnectedAddresses(context, allowProfileQuery = true)
        return buildDeviceInfoList(context, connectedAddresses)
    }

    /** 把已配对设备与连接地址快照组合成设置页展示数据。 */
    private fun buildDeviceInfoList(
        context: Context,
        connectedAddresses: Set<String>
    ): List<BluetoothDeviceInfo> {
        return getBondedDevices(context)
            .map { device ->
                val address = readDeviceAddress(device)
                BluetoothDeviceInfo(
                    name = readDeviceName(device),
                    address = address,
                    connected = connectedAddresses.contains(address)
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

    /**
     * 连接状态变化后异步刷新缓存，避免广播接收器阻塞主线程。
     * 广播刚到达时系统 Profile 状态可能尚未同步，可通过 delayMillis 延迟查询。
     */
    fun refreshConnectedDeviceCacheAsync(context: Context, delayMillis: Long = 0L) {
        val appContext = context.applicationContext
        Thread {
            if (delayMillis > 0L) {
                runCatching { Thread.sleep(delayMillis) }
            }
            queryConnectedAddresses(appContext, allowProfileQuery = true)
        }.start()
    }

    /** 连接或断开广播到达时立即修正缓存，避免等待异步 Profile 查询期间继续使用旧状态。 */
    fun updateConnectedDeviceCacheFromBroadcast(
        context: Context,
        device: BluetoothDevice?,
        connected: Boolean
    ) {
        if (!hasBluetoothPermission(context) || device == null) return
        val address = readDeviceAddress(device)
        if (address.isBlank()) return

        val addresses = loadCachedConnectedAddresses(context).toMutableSet()
        if (connected) {
            addresses.add(address)
        } else {
            addresses.remove(address)
        }
        saveConnectedAddresses(context, addresses)
    }

    /** 蓝牙总开关关闭时立即清空连接缓存。 */
    fun clearConnectedDeviceCache(context: Context) {
        saveConnectedAddresses(context, emptySet())
    }

    /**
     * 查询当前连接地址。
     *
     * 完整 Profile 查询完成后，结果会覆盖旧缓存，而不是与旧缓存做并集；
     * 否则已经断开的设备会永久残留在“已连接”状态。
     */
    private fun queryConnectedAddresses(context: Context, allowProfileQuery: Boolean): Set<String> {
        if (!hasBluetoothPermission(context)) return emptySet()
        val directAddresses = getBondedDevices(context)
            .filter { isConnectedByReflection(it) }
            .map { readDeviceAddress(it) }
            .filter { it.isNotBlank() }
            .toSet()

        if (!allowProfileQuery) {
            return directAddresses + loadCachedConnectedAddresses(context)
        }

        val result = directAddresses + queryProfileConnectedAddresses(context)
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
            ?.toSet()
            ?: emptySet()
    }

    private fun saveConnectedAddresses(context: Context, addresses: Set<String>) {
        val normalizedAddresses = addresses.toSet()
        if (loadCachedConnectedAddresses(context) == normalizedAddresses) return

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CONNECTED_ADDRESSES, normalizedAddresses)
            .apply()
        notifyConnectionStateChanged()
    }

    /** 通知当前进程内的界面监听器，监听器自行切换到主线程更新 UI。 */
    private fun notifyConnectionStateChanged() {
        connectionStateListeners.forEach { listener ->
            runCatching { listener() }
        }
    }
}
