package com.zhaoyuchen.androidforward.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhaoyuchen.androidforward.forward.RetryQueue

/**
 * AlarmManager 触发的重试入口。
 */
class RetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RetryQueue.flushAsync(context)
    }
}
