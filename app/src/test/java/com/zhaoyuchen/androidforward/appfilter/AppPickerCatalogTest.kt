package com.zhaoyuchen.androidforward.appfilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerCatalogTest {
    @Test
    fun `最近通知应用置顶并从其他应用去重`() {
        val recent = listOf(
            AppCandidate("com.example.chat", "Chat"),
            AppCandidate("com.example.mail", "Mail"),
            AppCandidate("com.example.chat", "Chat")
        )
        val launcherApps = listOf(
            AppCandidate("com.example.browser", "Browser"),
            AppCandidate("com.example.chat", "Chat"),
            AppCandidate("com.example.camera", "Camera")
        )

        val result = AppPickerCatalog.build(
            recentApps = recent,
            launcherApps = launcherApps,
            excludedPackages = emptySet()
        )

        assertEquals(
            listOf("com.example.chat", "com.example.mail"),
            result.recent.map { it.packageName }
        )
        assertEquals(
            listOf("com.example.browser", "com.example.camera"),
            result.other.map { it.packageName }
        )
    }

    @Test
    fun `已过滤和内置应用不会出现在选择器`() {
        val result = AppPickerCatalog.build(
            recentApps = listOf(
                AppCandidate("com.example.filtered", "Filtered"),
                AppCandidate("com.example.recent", "Recent")
            ),
            launcherApps = listOf(
                AppCandidate("com.example.filtered", "Filtered"),
                AppCandidate("com.example.builtin", "Builtin"),
                AppCandidate("com.example.other", "Other")
            ),
            excludedPackages = setOf("com.example.filtered", "com.example.builtin")
        )

        assertEquals(listOf("com.example.recent"), result.recent.map { it.packageName })
        assertEquals(listOf("com.example.other"), result.other.map { it.packageName })
    }

    @Test
    fun `搜索同时匹配应用名称和包名`() {
        val sections = AppPickerSections(
            recent = listOf(AppCandidate("com.tencent.mm", "WeChat")),
            other = listOf(
                AppCandidate("com.example.reader", "Reader"),
                AppCandidate("com.example.maps", "Maps")
            )
        )

        val byName = AppPickerCatalog.search(sections, "wechat")
        val byPackage = AppPickerCatalog.search(sections, "example.reader")

        assertEquals(listOf("com.tencent.mm"), byName.recent.map { it.packageName })
        assertEquals(listOf("com.example.reader"), byPackage.other.map { it.packageName })
    }

    @Test
    fun `默认只显示最近通知应用并在查看更多后显示其他应用`() {
        val sections = AppPickerSections(
            recent = listOf(AppCandidate("com.tencent.mm", "WeChat")),
            other = listOf(AppCandidate("com.example.reader", "Reader"))
        )

        val defaultVisible = AppPickerCatalog.visibleSections(
            sections = sections,
            showAllApps = false
        )
        val expandedVisible = AppPickerCatalog.visibleSections(
            sections = sections,
            showAllApps = true
        )

        assertEquals(listOf("com.tencent.mm"), defaultVisible.recent.map { it.packageName })
        assertEquals(emptyList<String>(), defaultVisible.other.map { it.packageName })
        assertTrue(AppPickerCatalog.hasHiddenOtherApps(sections, showAllApps = false))
        assertEquals(listOf("com.example.reader"), expandedVisible.other.map { it.packageName })
        assertFalse(AppPickerCatalog.hasHiddenOtherApps(sections, showAllApps = true))
    }
}
