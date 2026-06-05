package com.zhaoyuchen.androidforward.forward

import android.content.Context
import android.telephony.TelephonyManager
import com.zhaoyuchen.androidforward.data.AppSettingsRepository

/**
 * 电话状态去重与未接来电判断。
 *
 * 广播和 TelephonyCallback 可能同时上报同一通电话，所以状态需要落到 SharedPreferences。
 */
object PhoneStateTracker {
    private const val PREFS_NAME = "android_forward_phone_state"
    private const val KEY_LAST_STATE = "last_state"
    private const val KEY_RINGING_STARTED_AT = "ringing_started_at"
    private const val KEY_LAST_INCOMING_AT = "last_incoming_at"
    private const val KEY_ACTIVE_NUMBER = "active_number"
    private const val KEY_OFFHOOK_DURING_RING = "offhook_during_ring"
    private const val INCOMING_DEDUP_WINDOW_MS = 12_000L

    /** 处理电话状态变化，并在响铃/未接时触发转发。 */
    fun handleState(context: Context, state: Int, number: String?) {
        val appContext = context.applicationContext
        if (!AppSettingsRepository(appContext).load().phoneEnabled) return

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val previousState = prefs.getInt(KEY_LAST_STATE, TelephonyManager.CALL_STATE_IDLE)
        val previousNumber = prefs.getString(KEY_ACTIVE_NUMBER, null)
        val activeNumber = number?.takeIf { it.isNotBlank() } ?: previousNumber

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                val lastIncomingAt = prefs.getLong(KEY_LAST_INCOMING_AT, 0L)
                val shouldForward = previousState != TelephonyManager.CALL_STATE_RINGING ||
                    now - lastIncomingAt > INCOMING_DEDUP_WINDOW_MS

                prefs.edit()
                    .putInt(KEY_LAST_STATE, TelephonyManager.CALL_STATE_RINGING)
                    .putLong(KEY_RINGING_STARTED_AT, now)
                    .putLong(KEY_LAST_INCOMING_AT, if (shouldForward) now else lastIncomingAt)
                    .putString(KEY_ACTIVE_NUMBER, activeNumber)
                    .putBoolean(KEY_OFFHOOK_DURING_RING, false)
                    .apply()

                if (shouldForward) {
                    ForwardDispatcher.forwardCall(appContext, missed = false, number = activeNumber)
                }
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                prefs.edit()
                    .putInt(KEY_LAST_STATE, TelephonyManager.CALL_STATE_OFFHOOK)
                    .putBoolean(KEY_OFFHOOK_DURING_RING, true)
                    .putString(KEY_ACTIVE_NUMBER, activeNumber)
                    .apply()
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                val wasRinging = previousState == TelephonyManager.CALL_STATE_RINGING
                val wentOffhook = prefs.getBoolean(KEY_OFFHOOK_DURING_RING, false)
                prefs.edit()
                    .putInt(KEY_LAST_STATE, TelephonyManager.CALL_STATE_IDLE)
                    .remove(KEY_RINGING_STARTED_AT)
                    .remove(KEY_ACTIVE_NUMBER)
                    .putBoolean(KEY_OFFHOOK_DURING_RING, false)
                    .apply()

                if (wasRinging && !wentOffhook) {
                    ForwardDispatcher.forwardCall(appContext, missed = true, number = activeNumber)
                }
            }
        }
    }
}
