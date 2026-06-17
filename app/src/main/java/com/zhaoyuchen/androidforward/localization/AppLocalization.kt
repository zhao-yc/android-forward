package com.zhaoyuchen.androidforward.localization

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * 为后台服务和广播创建遵循应用内语言选择的 Context。
 *
 * AppCompat 会负责 Activity 的语言切换；后台入口显式使用此方法，保证 Android 12
 * 及以下系统也能读取到同一套应用语言资源。
 */
fun Context.localizedContext(): Context {
    val appLocales = AppCompatDelegate.getApplicationLocales()
    if (appLocales.isEmpty) return this
    val locale = appLocales[0] ?: return this
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}

/** 读取当前应用语言下的字符串资源。 */
fun Context.localizedString(@StringRes resId: Int, vararg formatArgs: Any): String {
    return localizedContext().getString(resId, *formatArgs)
}

/** 返回当前应用语言使用的 Locale，用于日期时间格式化。 */
fun Context.localizedLocale(): Locale {
    return localizedContext().resources.configuration.locales[0] ?: Locale.getDefault()
}
