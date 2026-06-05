package com.zhaoyuchen.androidforward.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhaoyuchen.androidforward.service.KeepAliveService

/**
 * 常驻通知自恢复入口。
 *
 * 它会响应两类事件：通知被系统/清理工具移除，以及定时 watchdog 唤醒。
 * 真正是否恢复服务仍由 KeepAliveService 按当前开关状态统一判断。
 */
class KeepAliveWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            KeepAliveService.ACTION_NOTIFICATION_DELETED,
            KeepAliveService.ACTION_WATCHDOG_TICK -> KeepAliveService.restartFromWatchdog(context)
        }
    }
}
