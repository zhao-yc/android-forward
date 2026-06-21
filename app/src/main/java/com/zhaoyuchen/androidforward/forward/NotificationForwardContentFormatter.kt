package com.zhaoyuchen.androidforward.forward

/**
 * 通知转发内容的纯逻辑格式化器，便于单元测试验证 Bark 标题和正文不会重复。
 */
internal object NotificationForwardContentFormatter {
    /**
     * Bark 顶部标题优先使用原通知标题；正文只保留来源、内容和时间。
     */
    fun build(
        deviceName: String,
        appName: String,
        notificationTitle: String,
        notificationText: String,
        now: String,
        sourceLine: (String) -> String,
        contentLine: (String) -> String,
        timeLine: (String) -> String
    ): NotificationForwardContent {
        val displayTitle = notificationTitle.takeIf { it.isNotBlank() } ?: appName
        val displaySource = ForwardSourceFormatter.combineDeviceAndSource(deviceName, appName)
        val body = buildString {
            appendLine(sourceLine(displaySource))
            if (notificationText.isNotBlank()) {
                appendLine(contentLine(notificationText))
            }
            append(timeLine(now))
        }
        return NotificationForwardContent(displayTitle, body)
    }
}

/**
 * 通知转发最终交给 Bark 的标题和正文。
 */
internal data class NotificationForwardContent(
    val displayTitle: String,
    val body: String
)
