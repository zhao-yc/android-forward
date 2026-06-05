package com.zhaoyuchen.androidforward.receiver

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhaoyuchen.androidforward.bluetooth.BluetoothSilenceManager
import com.zhaoyuchen.androidforward.forward.RetryQueue

/**
 * 蓝牙连接状态变化后刷新静默判断缓存；断开时顺手触发一次重试队列。
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            BluetoothDevice.ACTION_BOND_STATE_CHANGED,
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                BluetoothSilenceManager.refreshConnectedDeviceCacheAsync(context)
                if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                    RetryQueue.flushAsync(context)
                }
            }
        }
    }
}
