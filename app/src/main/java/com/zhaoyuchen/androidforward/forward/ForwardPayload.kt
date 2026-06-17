package com.zhaoyuchen.androidforward.forward

import org.json.JSONObject

/**
 * 待发送到 Bark 的完整内容。重试队列会加密保存这个对象。
 */
data class ForwardPayload(
    val type: ForwardType,
    val source: String,
    val title: String,
    val body: String,
    val sourcePackage: String? = null,
    val group: String,
    val level: String = "active"
) {
    /** 序列化用于加密重试队列。 */
    fun toJson(): JSONObject {
        val json = JSONObject()
            .put("type", type.name)
            .put("source", source)
            .put("title", title)
            .put("body", body)
            .put("group", group)
            .put("level", level)
        sourcePackage
            ?.takeIf { it.isNotBlank() }
            ?.let { json.put("sourcePackage", it) }
        return json
    }

    companion object {
        /** 从重试队列还原推送内容，解析失败时让调用方丢弃该条坏数据。 */
        fun fromJson(json: JSONObject): ForwardPayload {
            return ForwardPayload(
                type = ForwardType.valueOf(json.getString("type")),
                source = json.optString("source"),
                title = json.getString("title"),
                body = json.getString("body"),
                sourcePackage = json.optString("sourcePackage").takeIf { it.isNotBlank() },
                group = json.optString("group", "安卓转发"),
                level = json.optString("level", "active")
            )
        }
    }
}
