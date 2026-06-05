package com.zhaoyuchen.androidforward.forward

/**
 * 支持的转发类型。displayTitle 会直接作为 Bark 推送标题的一部分。
 */
enum class ForwardType(val displayTitle: String) {
    TEST("测试"),
    NOTIFICATION("通知"),
    SMS("短信"),
    INCOMING_CALL("来电"),
    MISSED_CALL("未接来电")
}
