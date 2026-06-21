package com.zhaoyuchen.androidforward.forward

import android.content.Context
import com.zhaoyuchen.androidforward.R
import com.zhaoyuchen.androidforward.bluetooth.BluetoothSilenceManager
import com.zhaoyuchen.androidforward.data.AppSettingsRepository
import com.zhaoyuchen.androidforward.data.ForwardLogRepository
import com.zhaoyuchen.androidforward.localization.localizedLocale
import com.zhaoyuchen.androidforward.localization.localizedString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * 所有监听入口统一调用这里，保证开关、Bark Key、日志和重试策略一致。
 */
object ForwardDispatcher {
    private val sendExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "android-forward-sender")
    }

    /** 设置页的测试推送，不进入失败重试队列，便于用户立即看到真实结果。 */
    fun forwardTest(context: Context): ForwardResult {
        val settings = AppSettingsRepository(context).load()
        val now = formatNow(context)
        val settingsSource = context.localizedString(R.string.forward_source_settings)
        val displaySource = ForwardSourceFormatter.combineDeviceAndSource(settings.deviceName, settingsSource)
        val body = buildString {
            appendLine(context.localizedString(R.string.forward_label_source, displaySource))
            append(context.localizedString(R.string.forward_test_body, now))
        }
        val payload = ForwardPayload(
            type = ForwardType.TEST,
            source = settingsSource,
            title = ForwardType.TEST.title(context),
            body = body,
            group = context.localizedString(R.string.forward_group)
        )
        return send(context, payload, allowRetry = false)
    }

    /** 转发系统通知。Bark 标题承载原通知标题，正文保留来源、内容和时间。 */
    fun forwardNotification(
        context: Context,
        appName: String,
        packageName: String,
        title: String,
        text: String
    ) {
        val settings = AppSettingsRepository(context).load()
        val now = formatNow(context)
        val content = NotificationForwardContentFormatter.build(
            deviceName = settings.deviceName,
            appName = appName,
            notificationTitle = title,
            notificationText = text,
            now = now,
            sourceLine = { context.localizedString(R.string.forward_label_source, it) },
            contentLine = { context.localizedString(R.string.forward_label_content, it) },
            timeLine = { context.localizedString(R.string.forward_label_time, it) }
        )
        val payload = ForwardPayload(
            type = ForwardType.NOTIFICATION,
            source = appName,
            title = content.displayTitle,
            body = content.body,
            sourcePackage = packageName,
            group = context.localizedString(R.string.forward_group)
        )
        sendAsync(context, payload, allowRetry = true)
    }

    /** 转发来电或未接来电；拿不到号码时会降级显示未知号码。 */
    fun forwardCall(context: Context, missed: Boolean, number: String?) {
        val settings = AppSettingsRepository(context).load()
        val displayNumber = number?.takeIf { it.isNotBlank() }
            ?: context.localizedString(R.string.forward_unknown_number)
        val type = if (missed) ForwardType.MISSED_CALL else ForwardType.INCOMING_CALL
        val now = formatNow(context)
        val body = CallForwardContentFormatter.buildBody(
            deviceName = settings.deviceName,
            displayNumber = displayNumber,
            now = now,
            sourceLine = { context.localizedString(R.string.forward_label_source, it) },
            numberLine = { context.localizedString(R.string.forward_label_number, it) },
            timeLine = { context.localizedString(R.string.forward_label_time, it) }
        )
        val payload = ForwardPayload(
            type = type,
            source = displayNumber,
            title = context.localizedString(R.string.forward_call_title, type.title(context), displayNumber),
            body = body,
            group = context.localizedString(R.string.forward_group)
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
                val result = ForwardResult(
                    true,
                    appContext.localizedString(R.string.forward_bluetooth_silenced, mutedDeviceName)
                )
                logs.add(
                    payload.type.title(appContext),
                    payload.source,
                    true,
                    result.detail,
                    payload.sourcePackage
                )
                return result
            }
        }

        if (barkKey.isBlank()) {
            val result = ForwardResult(false, appContext.localizedString(R.string.forward_bark_key_empty))
            logs.add(
                payload.type.title(appContext),
                payload.source,
                false,
                result.detail,
                payload.sourcePackage
            )
            return result
        }

        val result = BarkClient.send(appContext, settings.barkServerUrl, barkKey, payload)
        logs.add(
            payload.type.title(appContext),
            payload.source,
            result.success,
            result.detail,
            payload.sourcePackage
        )

        if (!result.success && allowRetry && settings.retryEnabled) {
            RetryQueue.enqueue(appContext, payload)
            return result.copy(
                detail = appContext.localizedString(R.string.forward_added_to_retry, result.detail)
            )
        }

        if (result.success && settings.retryEnabled) {
            RetryQueue.flushAsync(appContext)
        }

        return result
    }

    /** 按当前应用语言格式化时间，避免固定使用中文 Locale。 */
    private fun formatNow(context: Context): String {
        val format = SimpleDateFormat(
            context.localizedString(R.string.date_time_pattern),
            context.localizedLocale()
        )
        return format.format(Date())
    }
}
