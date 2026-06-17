package com.zhaoyuchen.androidforward.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.Intent.ACTION_LOCKED_BOOT_COMPLETED
import com.zhaoyuchen.androidforward.bluetooth.BluetoothSilenceManager
import com.zhaoyuchen.androidforward.forward.RetryQueue
import com.zhaoyuchen.androidforward.service.KeepAliveService
import com.zhaoyuchen.androidforward.service.PhoneMonitorService

/**
 * 开机后恢复电话监听和失败重试。通知监听由系统根据权限自动唤起。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BOOT_COMPLETED && intent.action != ACTION_LOCKED_BOOT_COMPLETED) return
        BluetoothSilenceManager.refreshConnectedDeviceCacheAsync(context)
        KeepAliveService.startIfNeeded(context)
        PhoneMonitorService.startIfNeeded(context)
        RetryQueue.flushAsync(context)
    }
}
