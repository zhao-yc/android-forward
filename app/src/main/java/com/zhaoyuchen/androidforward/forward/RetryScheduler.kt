package com.zhaoyuchen.androidforward.forward

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.zhaoyuchen.androidforward.receiver.RetryReceiver

/**
 * 用 AlarmManager 做轻量失败重试，不引入 WorkManager。
 */
object RetryScheduler {
    private const val REQUEST_CODE = 20260604
    private const val DEFAULT_DELAY_MS = 60_000L

    /** 安排下一次重试；使用非精确闹钟，减少系统权限要求。 */
    fun schedule(context: Context, delayMs: Long = DEFAULT_DELAY_MS) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, RetryReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMs,
            pendingIntent
        )
    }
}
