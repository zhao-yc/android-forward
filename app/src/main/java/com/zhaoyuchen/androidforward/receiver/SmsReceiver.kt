package com.zhaoyuchen.androidforward.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.zhaoyuchen.androidforward.data.AppSettingsRepository
import com.zhaoyuchen.androidforward.forward.ForwardDispatcher

/**
 * 接收系统短信广播，把多段短信合并后转发。
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settings = AppSettingsRepository(context).load()
        if (!settings.smsEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { message ->
            message.displayMessageBody ?: message.messageBody.orEmpty()
        }

        if (body.isNotBlank()) {
            ForwardDispatcher.forwardSms(context, sender, body)
        }
    }
}
