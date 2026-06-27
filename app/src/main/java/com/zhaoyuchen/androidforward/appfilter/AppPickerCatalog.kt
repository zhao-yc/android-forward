package com.zhaoyuchen.androidforward.appfilter

import java.util.Locale

/**
 * 应用过滤选择器展示的轻量应用信息。
 *
 * 图标由界面层按包名加载，避免把 Android Drawable 引入可单元测试的目录逻辑。
 */
data class AppCandidate(
    val packageName: String,
    val name: String
)

/** 应用选择器的两个展示分组。 */
data class AppPickerSections(
    val recent: List<AppCandidate>,
    val other: List<AppCandidate>
)

/**
 * 负责应用选择器的分组、排除、去重和搜索，保持界面层只处理展示状态。
 */
object AppPickerCatalog {
    /** 最近通知应用保持传入顺序，其他应用按名称排序。 */
    fun build(
        recentApps: List<AppCandidate>,
        launcherApps: List<AppCandidate>,
        excludedPackages: Set<String>
    ): AppPickerSections {
        val recent = recentApps
            .distinctBy(AppCandidate::packageName)
            .filterNot { it.packageName in excludedPackages }
        val recentPackages = recent.mapTo(mutableSetOf(), AppCandidate::packageName)
        val other = launcherApps
            .distinctBy(AppCandidate::packageName)
            .filterNot { it.packageName in excludedPackages || it.packageName in recentPackages }
            .sortedWith(compareBy<AppCandidate> { it.name.lowercase(Locale.getDefault()) }
                .thenBy(AppCandidate::packageName))
        return AppPickerSections(recent = recent, other = other)
    }

    /** 搜索同时匹配应用显示名称和包名，不区分大小写。 */
    fun search(sections: AppPickerSections, query: String): AppPickerSections {
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        if (normalizedQuery.isBlank()) return sections

        fun List<AppCandidate>.matching(): List<AppCandidate> {
            return filter { app ->
                app.name.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                    app.packageName.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
        }
        return AppPickerSections(
            recent = sections.recent.matching(),
            other = sections.other.matching()
        )
    }

    /** 根据“查看更多应用”开关返回当前应该展示的分组。 */
    fun visibleSections(sections: AppPickerSections, showAllApps: Boolean): AppPickerSections {
        return if (showAllApps) sections else sections.copy(other = emptyList())
    }

    /** 判断当前搜索结果里是否还有被“查看更多应用”折叠起来的其他应用。 */
    fun hasHiddenOtherApps(sections: AppPickerSections, showAllApps: Boolean): Boolean {
        return !showAllApps && sections.other.isNotEmpty()
    }
}
