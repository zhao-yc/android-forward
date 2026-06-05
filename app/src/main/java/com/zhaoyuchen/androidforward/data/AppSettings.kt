package com.zhaoyuchen.androidforward.data

/**
 * 应用运行配置。这里集中保存开关状态，避免各个监听器自己解释 SharedPreferences。
 */
data class AppSettings(
    val barkServerUrl: String = AppSettingsRepository.DEFAULT_BARK_SERVER_URL,
    val notificationEnabled: Boolean = true,
    val smsEnabled: Boolean = true,
    val phoneEnabled: Boolean = true,
    val retryEnabled: Boolean = true,
    val filteredPackages: Set<String> = AppSettingsRepository.DEFAULT_FILTERED_PACKAGES
)
