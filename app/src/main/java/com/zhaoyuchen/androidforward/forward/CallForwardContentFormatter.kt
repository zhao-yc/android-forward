package com.zhaoyuchen.androidforward.forward

/**
 * 来电转发正文格式化器，让通知、测试和来电共用同一套来源展示规则。
 */
internal object CallForwardContentFormatter {
    /** 构建来电或未接来电正文，首行固定展示手机名称来源。 */
    fun buildBody(
        deviceName: String,
        displayNumber: String,
        now: String,
        sourceLine: (String) -> String,
        numberLine: (String) -> String,
        timeLine: (String) -> String
    ): String {
        return buildString {
            appendLine(sourceLine(deviceName.trim()))
            appendLine(numberLine(displayNumber))
            append(timeLine(now))
        }
    }
}
