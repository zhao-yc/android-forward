package com.zhaoyuchen.androidforward.appfilter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.zhaoyuchen.androidforward.data.ForwardLogItem

/**
 * 查询应用过滤选择器需要的应用列表。
 *
 * 这里只查询具有桌面启动入口的应用，不申请 QUERY_ALL_PACKAGES。
 */
object InstalledAppRepository {
    /** 从最近状态提取发生过通知的应用，保持日志中的最近优先顺序。 */
    fun recentFromLogs(context: Context, logs: List<ForwardLogItem>): List<AppCandidate> {
        return logs.mapNotNull { item ->
            item.sourcePackage?.takeIf(String::isNotBlank)?.let { packageName ->
                AppCandidate(packageName, resolveApplicationName(context, packageName))
            }
        }.distinctBy(AppCandidate::packageName)
    }

    /** 查询具有桌面启动入口的常用应用。调用方应在后台线程执行。 */
    fun listLauncherApps(context: Context): List<AppCandidate> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        return activities.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(packageManager).toString() }
                .getOrDefault(packageName)
            AppCandidate(packageName = packageName, name = label)
        }.distinctBy(AppCandidate::packageName)
    }

    /** 根据包名解析应用名称；应用已卸载或查询失败时回退显示包名。 */
    fun resolveApplicationName(context: Context, packageName: String): String {
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }
}
