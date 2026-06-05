package com.zhaoyuchen.androidforward.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.zhaoyuchen.androidforward.data.AppSettingsRepository
import com.zhaoyuchen.androidforward.forward.ForwardDispatcher

/**
 * 系统通知监听服务。用户必须在系统设置里手动授予通知使用权。
 */
class ForwardNotificationListener : NotificationListenerService() {
    private val recentFingerprints = LinkedHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val settings = AppSettingsRepository(this).load()
        if (!settings.notificationEnabled) return

        val packageName = sbn.packageName ?: return
        if (settings.filteredPackages.contains(packageName)) return

        val appName = resolveAppName(packageName)
        val title = readTitle(sbn.notification)
        val text = readText(sbn.notification)
        if (title.isBlank() && text.isBlank()) return

        if (isDuplicate(packageName, title, text)) return
        ForwardDispatcher.forwardNotification(this, appName, title, text)
    }

    /** 从通知 Extras 中读取标题，兼容普通标题和大标题。 */
    private fun readTitle(notification: Notification): String {
        val extras = notification.extras ?: return ""
        return extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ""
    }

    /** 优先读取大文本，其次读取普通文本和多行文本。 */
    private fun readText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if (bigText.isNotBlank()) return bigText

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!textLines.isNullOrEmpty()) {
            return textLines.joinToString(separator = "\n") { it.toString() }
        }

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        return listOf(text, subText).filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    /** 根据包名显示应用名，失败时退回包名。 */
    private fun resolveAppName(packageName: String): String {
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    /** 短时间内完全相同的通知只转发一次，避免系统重复回调刷屏。 */
    private fun isDuplicate(packageName: String, title: String, text: String): Boolean {
        val now = System.currentTimeMillis()
        val fingerprint = "$packageName|$title|$text"
        synchronized(recentFingerprints) {
            val lastSentAt = recentFingerprints[fingerprint]
            if (lastSentAt != null && now - lastSentAt < DEDUP_WINDOW_MS) {
                return true
            }
            recentFingerprints[fingerprint] = now
            if (recentFingerprints.size > MAX_RECENT_ITEMS) {
                val iterator = recentFingerprints.entries.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            return false
        }
    }

    companion object {
        private const val DEDUP_WINDOW_MS = 2_000L
        private const val MAX_RECENT_ITEMS = 80
    }
}
