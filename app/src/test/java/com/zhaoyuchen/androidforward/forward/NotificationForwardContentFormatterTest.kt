package com.zhaoyuchen.androidforward.forward

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationForwardContentFormatterTest {
    @Test
    fun `通知标题用于 Bark 顶部且正文不重复标题`() {
        val content = NotificationForwardContentFormatter.build(
            deviceName = "备用机A",
            appName = "微信",
            notificationTitle = "验证码",
            notificationText = "123456",
            now = "2026-06-21 10:30",
            sourceLine = { "来源：$it" },
            contentLine = { "内容：$it" },
            timeLine = { "时间：$it" }
        )

        assertEquals("验证码", content.displayTitle)
        assertEquals("来源：备用机A · 微信\n内容：123456\n时间：2026-06-21 10:30", content.body)
    }

    @Test
    fun `无通知标题时 Bark 顶部回退应用名`() {
        val content = NotificationForwardContentFormatter.build(
            deviceName = "备用机A",
            appName = "邮箱",
            notificationTitle = "",
            notificationText = "",
            now = "2026-06-21 10:31",
            sourceLine = { "来源：$it" },
            contentLine = { "内容：$it" },
            timeLine = { "时间：$it" }
        )

        assertEquals("邮箱", content.displayTitle)
        assertEquals("来源：备用机A · 邮箱\n时间：2026-06-21 10:31", content.body)
    }

    @Test
    fun `来电正文包含手机名称号码和时间`() {
        val body = CallForwardContentFormatter.buildBody(
            deviceName = "备用机A",
            displayNumber = "13800138000",
            now = "2026-06-21 10:32",
            sourceLine = { "来源：$it" },
            numberLine = { "号码：$it" },
            timeLine = { "时间：$it" }
        )

        assertEquals("来源：备用机A\n号码：13800138000\n时间：2026-06-21 10:32", body)
    }
}
