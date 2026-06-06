package com.zhaoyuchen.androidforward.receiver

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.zhaoyuchen.androidforward.bluetooth.BluetoothSilenceManager
import com.zhaoyuchen.androidforward.forward.RetryQueue

/**
 * 蓝牙连接状态变化后刷新静默判断缓存；断开时顺手触发一次重试队列。
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                BluetoothSilenceManager.updateConnectedDeviceCacheFromBroadcast(
                    context,
                    readBluetoothDevice(intent),
                    connected = true
                )
                BluetoothSilenceManager.refreshConnectedDeviceCacheAsync(context, PROFILE_SETTLE_DELAY_MS)
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                BluetoothSilenceManager.updateConnectedDeviceCacheFromBroadcast(
                    context,
                    readBluetoothDevice(intent),
                    connected = false
                )
                BluetoothSilenceManager.refreshConnectedDeviceCacheAsync(context, PROFILE_SETTLE_DELAY_MS)
                RetryQueue.flushAsync(context)
            }

            ACTION_A2DP_CONNECTION_STATE_CHANGED,
            ACTION_HEADSET_CONNECTION_STATE_CHANGED,
            ACTION_HEARING_AID_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    BluetoothSilenceManager.updateConnectedDeviceCacheFromBroadcast(
                        context,
                        readBluetoothDevice(intent),
                        connected = true
                    )
                }
                // 单个 Profile 断开不代表设备完全断开，延迟完整查询后再覆盖缓存。
                BluetoothSilenceManager.refreshConnectedDeviceCacheAsync(context, PROFILE_SETTLE_DELAY_MS)
                if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    RetryQueue.flushAsync(context)
                }
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    BluetoothSilenceManager.clearConnectedDeviceCache(context)
                } else {
                    BluetoothSilenceManager.refreshConnectedDeviceCacheAsync(context, PROFILE_SETTLE_DELAY_MS)
                }
            }

            BluetoothDevice.ACTION_BOND_STATE_CHANGED ->
                BluetoothSilenceManager.refreshConnectedDeviceCacheAsync(context, PROFILE_SETTLE_DELAY_MS)
        }
    }

    /** 按系统版本读取广播中的蓝牙设备对象。 */
    private fun readBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    companion object {
        /** 给系统一点时间完成 Profile 状态切换，避免刚断开时又被旧状态写回缓存。 */
        private const val PROFILE_SETTLE_DELAY_MS = 1_000L
        private const val ACTION_A2DP_CONNECTION_STATE_CHANGED =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        private const val ACTION_HEADSET_CONNECTION_STATE_CHANGED =
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
        private const val ACTION_HEARING_AID_CONNECTION_STATE_CHANGED =
            "android.bluetooth.hearingaid.profile.action.CONNECTION_STATE_CHANGED"
    }
}
