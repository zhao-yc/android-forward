package com.zhaoyuchen.androidforward.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zhaoyuchen.androidforward.MainActivity
import com.zhaoyuchen.androidforward.R
import com.zhaoyuchen.androidforward.data.AppSettingsRepository

/**
 * 常驻状态通知服务。看到这条前台服务通知，说明应用进程仍在运行。
 */
class KeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AppSettingsRepository(this).load().keepAliveNotificationEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 创建低优先级通知渠道，避免常驻通知发出声音。 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.keep_alive_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            description = getString(R.string.keep_alive_notification_body)
        }
        manager.createNotificationChannel(channel)
    }

    /** 构建可点击回到设置页的常驻通知。 */
    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_forward)
            .setContentTitle(getString(R.string.keep_alive_notification_title))
            .setContentText(getString(R.string.keep_alive_notification_body))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "android_forward_keep_alive"
        private const val NOTIFICATION_ID = 20260605

        /** 根据开关启动前台服务；后台启动受限时静默失败，用户打开应用后可再次开启。 */
        fun startIfNeeded(context: Context) {
            val appContext = context.applicationContext
            if (!AppSettingsRepository(appContext).load().keepAliveNotificationEnabled) return
            runCatching {
                val intent = Intent(appContext, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }
        }

        /** 关闭常驻通知。 */
        fun stop(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                appContext.stopService(Intent(appContext, KeepAliveService::class.java))
            }
        }
    }
}
