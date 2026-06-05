package com.zhaoyuchen.androidforward.forward

import android.content.Context
import com.zhaoyuchen.androidforward.bluetooth.BluetoothSilenceManager
import com.zhaoyuchen.androidforward.data.AppSettingsRepository
import com.zhaoyuchen.androidforward.data.ForwardLogRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 所有监听入口统一调用这里，保证开关、Bark Key、日志和重试策略一致。
 */
object ForwardDispatcher {
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val sendExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "android-forward-sender")
    }

    /** 设置页的测试推送，不进入失败重试队列，便于用户立即看到真实结果。 */
    fun forwardTest(context: Context): ForwardResult {
        val payload = ForwardPayload(
            type = ForwardType.TEST,
            source = "设置页",
            title = "测试",
            body = "来自安卓「通知转发」的测试推送：${formatNow()}"
        )
        return send(context, payload, allowRetry = false)
    }

    /** 转发系统通知。正文包含应用名、通知标题和通知内容。 */
    fun forwardNotification(
        context: Context,
        appName: String,
        title: String,
        text: String
    ) {
        val body = buildString {
            appendLine("来源：$appName")
            if (title.isNotBlank()) appendLine("标题：$title")
            if (text.isNotBlank()) appendLine("内容：$text")
            append("时间：${formatNow()}")
        }
        val payload = ForwardPayload(
            type = ForwardType.NOTIFICATION,
            source = appName,
            title = "通知：$appName",
            body = body
        )
        sendAsync(context, payload, allowRetry = true)
    }

    /** 转发新短信。短信正文只进入 Bark 推送和加密重试队列，不写入状态日志。 */
    fun forwardSms(context: Context, sender: String, message: String) {
        val displaySender = sender.ifBlank { "未知号码" }
        val body = buildString {
            appendLine("发件人：$displaySender")
            appendLine("内容：$message")
            append("时间：${formatNow()}")
        }
        val payload = ForwardPayload(
            type = ForwardType.SMS,
            source = displaySender,
            title = "短信：$displaySender",
            body = body
        )
        sendAsync(context, payload, allowRetry = true)
    }

    /** 转发来电或未接来电；拿不到号码时会降级显示未知号码。 */
    fun forwardCall(context: Context, missed: Boolean, number: String?) {
        val displayNumber = number?.takeIf { it.isNotBlank() } ?: "未知号码"
        val type = if (missed) ForwardType.MISSED_CALL else ForwardType.INCOMING_CALL
        val body = buildString {
            appendLine("号码：$displayNumber")
            append("时间：${formatNow()}")
        }
        val payload = ForwardPayload(
            type = type,
            source = displayNumber,
            title = "${type.displayTitle}：$displayNumber",
            body = body
        )
        sendAsync(context, payload, allowRetry = true)
    }

    /** 监听器和广播都可能运行在主线程，网络请求必须交给后台队列执行。 */
    private fun sendAsync(context: Context, payload: ForwardPayload, allowRetry: Boolean) {
        val appContext = context.applicationContext
        sendExecutor.execute {
            send(appContext, payload, allowRetry)
        }
    }

    /** 发送并记录状态。失败且开启重试时，完整内容会进入加密队列。 */
    private fun send(context: Context, payload: ForwardPayload, allowRetry: Boolean): ForwardResult {
        val appContext = context.applicationContext
        val settingsRepository = AppSettingsRepository(appContext)
        val settings = settingsRepository.load()
        val logs = ForwardLogRepository(appContext)
        val barkKey = settingsRepository.getBarkKey()

        if (allowRetry) {
            val mutedDeviceName = BluetoothSilenceManager.findConnectedMutedDevice(appContext, settings)
            if (mutedDeviceName != null) {
                val result = ForwardResult(true, "蓝牙静默：$mutedDeviceName")
                logs.add(payload.type.displayTitle, payload.source, true, result.detail)
                return result
            }
        }

        if (barkKey.isBlank()) {
            val result = ForwardResult(false, "Bark Key 为空")
            logs.add(payload.type.displayTitle, payload.source, false, result.detail)
            return result
        }

        val result = BarkClient.send(settings.barkServerUrl, barkKey, payload)
        logs.add(payload.type.displayTitle, payload.source, result.success, result.detail)

        if (!result.success && allowRetry && settings.retryEnabled) {
            RetryQueue.enqueue(appContext, payload)
            return result.copy(detail = "${result.detail}，已加入重试")
        }

        if (result.success && settings.retryEnabled) {
            RetryQueue.flushAsync(appContext)
        }

        return result
    }

    private fun formatNow(): String = synchronized(timeFormat) {
        timeFormat.format(Date())
    }
}
