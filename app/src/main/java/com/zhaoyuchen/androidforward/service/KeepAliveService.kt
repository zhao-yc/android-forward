package com.zhaoyuchen.androidforward.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.zhaoyuchen.androidforward.MainActivity
import com.zhaoyuchen.androidforward.R
import com.zhaoyuchen.androidforward.data.AppSettingsRepository
import com.zhaoyuchen.androidforward.receiver.KeepAliveWatchdogReceiver

/**
 * 常驻状态通知服务。看到这条前台服务通知，说明应用进程仍在运行。
 *
 * 部分国产 ROM 或清理工具会把前台服务通知从通知栏移除。这里使用三层兜底：
 * 1. deleteIntent 捕获通知被移除的事件；
 * 2. 服务存活时定时重新发布前台通知；
 * 3. AlarmManager 定期唤醒 watchdog，尽量在进程被清理后重新拉起服务。
 */
class KeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var refreshLoopStarted = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isKeepAliveEnabled()) {
                stopSelf()
                return
            }
            showForegroundNotification()
            handler.postDelayed(this, NOTIFICATION_REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isKeepAliveEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        showForegroundNotification()
        startRefreshLoop()
        scheduleWatchdog(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isKeepAliveEnabled()) {
            scheduleWatchdog(this)
        }
    }

    override fun onDestroy() {
        stopRefreshLoop()
        if (isKeepAliveEnabled()) {
            scheduleWatchdog(this)
        } else {
            cancelWatchdog(this)
        }
        super.onDestroy()
    }

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

    /** 重新发布前台服务通知；系统短暂移除通知时靠它补回来。 */
    private fun showForegroundNotification() {
        runCatching {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    /** 构建可点击回到设置页的常驻通知。 */
    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deleteIntent = PendingIntent.getBroadcast(
            this,
            DELETE_REQUEST_CODE,
            Intent(this, KeepAliveWatchdogReceiver::class.java).setAction(ACTION_NOTIFICATION_DELETED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_forward)
            .setContentTitle(getString(R.string.keep_alive_notification_title))
            .setContentText(getString(R.string.keep_alive_notification_body))
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** 启动服务内自检循环，避免重复注册多个 Runnable。 */
    private fun startRefreshLoop() {
        if (refreshLoopStarted) return
        refreshLoopStarted = true
        handler.postDelayed(refreshRunnable, NOTIFICATION_REFRESH_INTERVAL_MS)
    }

    /** 停止服务内自检循环，防止服务销毁后继续回调。 */
    private fun stopRefreshLoop() {
        refreshLoopStarted = false
        handler.removeCallbacks(refreshRunnable)
    }

    /** 读取最新开关状态，避免用户关闭后 watchdog 继续拉起服务。 */
    private fun isKeepAliveEnabled(): Boolean {
        return AppSettingsRepository(this).load().keepAliveNotificationEnabled
    }

    companion object {
        private const val CHANNEL_ID = "android_forward_keep_alive"
        private const val NOTIFICATION_ID = 20260605
        private const val DELETE_REQUEST_CODE = 20260606
        private const val WATCHDOG_REQUEST_CODE = 20260607
        private const val NOTIFICATION_REFRESH_INTERVAL_MS = 30_000L
        private const val WATCHDOG_INTERVAL_MS = 60_000L

        const val ACTION_NOTIFICATION_DELETED = "com.zhaoyuchen.androidforward.action.KEEP_ALIVE_NOTIFICATION_DELETED"
        const val ACTION_WATCHDOG_TICK = "com.zhaoyuchen.androidforward.action.KEEP_ALIVE_WATCHDOG_TICK"

        /** 根据开关启动前台服务；后台启动受限时静默失败，用户打开应用后可再次开启。 */
        fun startIfNeeded(context: Context) {
            val appContext = context.applicationContext
            if (!AppSettingsRepository(appContext).load().keepAliveNotificationEnabled) return
            scheduleWatchdog(appContext)
            runCatching {
                val intent = Intent(appContext, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }
        }

        /** watchdog 或通知删除回调进入这里，统一按当前开关状态决定是否恢复。 */
        fun restartFromWatchdog(context: Context) {
            val appContext = context.applicationContext
            if (!AppSettingsRepository(appContext).load().keepAliveNotificationEnabled) {
                cancelWatchdog(appContext)
                return
            }
            startIfNeeded(appContext)
        }

        /** 注册下一次 watchdog 唤醒；使用非精确闹钟，避免额外申请精确闹钟权限。 */
        fun scheduleWatchdog(context: Context) {
            val appContext = context.applicationContext
            if (!AppSettingsRepository(appContext).load().keepAliveNotificationEnabled) {
                cancelWatchdog(appContext)
                return
            }
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            val triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS
            val pendingIntent = watchdogPendingIntent(
                appContext,
                PendingIntent.FLAG_UPDATE_CURRENT
            ) ?: return
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        }

        /** 关闭常驻功能时取消 watchdog，避免后台继续唤醒。 */
        private fun cancelWatchdog(context: Context) {
            val appContext = context.applicationContext
            val pendingIntent = watchdogPendingIntent(
                appContext,
                PendingIntent.FLAG_NO_CREATE
            ) ?: return
            runCatching {
                appContext.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            }
        }

        /** 构建 watchdog 使用的 PendingIntent，保证注册和取消拿到同一个目标。 */
        private fun watchdogPendingIntent(context: Context, flags: Int): PendingIntent? {
            return PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                Intent(context, KeepAliveWatchdogReceiver::class.java).setAction(ACTION_WATCHDOG_TICK),
                flags or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 关闭常驻通知。 */
        fun stop(context: Context) {
            val appContext = context.applicationContext
            cancelWatchdog(appContext)
            runCatching {
                appContext.stopService(Intent(appContext, KeepAliveService::class.java))
            }
        }
    }
}
