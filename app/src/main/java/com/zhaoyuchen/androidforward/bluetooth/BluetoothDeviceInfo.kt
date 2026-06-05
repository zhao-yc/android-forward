package com.zhaoyuchen.androidforward.bluetooth

/**
 * 设置页展示的蓝牙设备信息。address 只存在本机配置里，不会写进转发日志。
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val connected: Boolean
)
