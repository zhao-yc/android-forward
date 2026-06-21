package com.zhaoyuchen.androidforward.forward

/**
 * 组合手机名称和原始来源，保证 Bark 正文里能看出是哪台备用机发出的消息。
 */
internal object ForwardSourceFormatter {
    /** 生成“手机名称 · 原来源”，空值和重复值会自动折叠。 */
    fun combineDeviceAndSource(deviceName: String, source: String): String {
        val cleanDeviceName = deviceName.trim()
        val cleanSource = source.trim()
        return when {
            cleanDeviceName.isBlank() -> cleanSource
            cleanSource.isBlank() -> cleanDeviceName
            cleanDeviceName == cleanSource -> cleanSource
            else -> "$cleanDeviceName · $cleanSource"
        }
    }
}
