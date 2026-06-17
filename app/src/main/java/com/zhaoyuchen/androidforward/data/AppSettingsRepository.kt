package com.zhaoyuchen.androidforward.data

import android.content.Context

/**
 * 统一读写用户配置。Bark Key 单独走 Keystore 加密存储，其它开关使用普通偏好设置。
 */
class AppSettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取完整配置，所有监听入口都应该从这里取最新开关。 */
    fun load(): AppSettings {
        return AppSettings(
            barkServerUrl = prefs.getString(KEY_BARK_SERVER_URL, DEFAULT_BARK_SERVER_URL)
                ?.ifBlank { DEFAULT_BARK_SERVER_URL } ?: DEFAULT_BARK_SERVER_URL,
            notificationEnabled = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true),
            phoneEnabled = prefs.getBoolean(KEY_PHONE_ENABLED, true),
            retryEnabled = prefs.getBoolean(KEY_RETRY_ENABLED, true),
            bluetoothSilenceEnabled = prefs.getBoolean(KEY_BLUETOOTH_SILENCE_ENABLED, false),
            mutedBluetoothAddresses = prefs.getStringSet(KEY_MUTED_BLUETOOTH_ADDRESSES, emptySet())
                ?: emptySet(),
            keepAliveNotificationEnabled = prefs.getBoolean(KEY_KEEP_ALIVE_NOTIFICATION_ENABLED, false),
            filteredPackages = (
                prefs.getStringSet(KEY_FILTERED_PACKAGES, emptySet()) ?: emptySet()
            ) + BUILTIN_FILTERED_PACKAGES
        )
    }

    /** 保存设置页里的普通配置，不包含 Bark Key。 */
    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_BARK_SERVER_URL, settings.barkServerUrl.trim().ifBlank { DEFAULT_BARK_SERVER_URL })
            .putBoolean(KEY_NOTIFICATION_ENABLED, settings.notificationEnabled)
            .putBoolean(KEY_PHONE_ENABLED, settings.phoneEnabled)
            .putBoolean(KEY_RETRY_ENABLED, settings.retryEnabled)
            .putBoolean(KEY_BLUETOOTH_SILENCE_ENABLED, settings.bluetoothSilenceEnabled)
            .putStringSet(KEY_MUTED_BLUETOOTH_ADDRESSES, settings.mutedBluetoothAddresses)
            .putBoolean(KEY_KEEP_ALIVE_NOTIFICATION_ENABLED, settings.keepAliveNotificationEnabled)
            .putStringSet(KEY_FILTERED_PACKAGES, settings.filteredPackages + BUILTIN_FILTERED_PACKAGES)
            .apply()
    }

    /** Bark Key 是推送凭证，必须加密保存。 */
    fun saveBarkKey(value: String) {
        SecureStore.putString(appContext, KEY_BARK_KEY, value.trim())
    }

    /** 读取 Bark Key，解密失败时返回空字符串，避免异常打断监听流程。 */
    fun getBarkKey(): String {
        return SecureStore.getString(appContext, KEY_BARK_KEY)
    }

    companion object {
        private const val PREFS_NAME = "android_forward_settings"
        private const val KEY_BARK_KEY = "bark_key"
        private const val KEY_BARK_SERVER_URL = "bark_server_url"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_PHONE_ENABLED = "phone_enabled"
        private const val KEY_RETRY_ENABLED = "retry_enabled"
        private const val KEY_BLUETOOTH_SILENCE_ENABLED = "bluetooth_silence_enabled"
        private const val KEY_MUTED_BLUETOOTH_ADDRESSES = "muted_bluetooth_addresses"
        private const val KEY_KEEP_ALIVE_NOTIFICATION_ENABLED = "keep_alive_notification_enabled"
        private const val KEY_FILTERED_PACKAGES = "filtered_packages"

        const val DEFAULT_BARK_SERVER_URL = "https://api.day.app"

        /** 内置防循环过滤项始终生效，不允许用户从设置页移除。 */
        val BUILTIN_FILTERED_PACKAGES = setOf(
            "com.zhaoyuchen.androidforward",
            "me.fin.bark",
            "me.fin.bark.beta",
            "me.fin.bark.dev"
        )
    }
}
