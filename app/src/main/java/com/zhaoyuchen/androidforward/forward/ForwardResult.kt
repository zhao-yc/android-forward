package com.zhaoyuchen.androidforward.forward

/**
 * 一次发送动作的结果，用于 UI 提示和状态日志。
 */
data class ForwardResult(
    val success: Boolean,
    val detail: String
)
