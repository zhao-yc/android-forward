package com.zhaoyuchen.androidforward.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.zhaoyuchen.androidforward.forward.PhoneStateTracker

/**
 * 接收电话状态广播。它和 TelephonyCallback 互为补充，提高不同系统上的兼容性。
 */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> return
        }
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        PhoneStateTracker.handleState(context, state, number)
    }
}
