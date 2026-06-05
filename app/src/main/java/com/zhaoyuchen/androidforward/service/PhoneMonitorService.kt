package com.zhaoyuchen.androidforward.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.zhaoyuchen.androidforward.data.AppSettingsRepository
import com.zhaoyuchen.androidforward.forward.PhoneStateTracker

/**
 * 主动注册电话状态回调。部分系统会限制后台服务，所以还保留了 PhoneStateReceiver 兜底。
 */
class PhoneMonitorService : Service() {
    private var telephonyCallback: TelephonyCallback? = null
    private var legacyListener: PhoneStateListener? = null

    override fun onCreate() {
        super.onCreate()
        registerCallStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        registerCallStateListener()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterCallStateListener()
        super.onDestroy()
    }

    /** 根据系统版本选择 TelephonyCallback 或旧版 PhoneStateListener。 */
    private fun registerCallStateListener() {
        if (!AppSettingsRepository(this).load().phoneEnabled) return
        val telephonyManager = getSystemService(TelephonyManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (telephonyCallback != null) return
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    PhoneStateTracker.handleState(this@PhoneMonitorService, state, number = null)
                }
            }
            telephonyCallback = callback
            runCatching {
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            }
        } else {
            if (legacyListener != null) return
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("旧系统回调仍需兼容")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    PhoneStateTracker.handleState(this@PhoneMonitorService, state, phoneNumber)
                }
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            runCatching {
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    /** 服务销毁时注销回调，避免系统继续持有无效引用。 */
    private fun unregisterCallStateListener() {
        val telephonyManager = getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { callback ->
                runCatching { telephonyManager.unregisterTelephonyCallback(callback) }
            }
            telephonyCallback = null
        } else {
            legacyListener?.let { listener ->
                @Suppress("DEPRECATION")
                runCatching { telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE) }
            }
            legacyListener = null
        }
    }

    companion object {
        /** 设置开启时尝试启动监听服务。失败时仍可依赖电话状态广播。 */
        fun startIfNeeded(context: Context) {
            val appContext = context.applicationContext
            if (!AppSettingsRepository(appContext).load().phoneEnabled) return
            runCatching {
                appContext.startService(Intent(appContext, PhoneMonitorService::class.java))
            }
        }

        /** 设置关闭时停止主动监听服务。 */
        fun stop(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                appContext.stopService(Intent(appContext, PhoneMonitorService::class.java))
            }
        }
    }
}
