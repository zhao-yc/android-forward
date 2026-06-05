package com.zhaoyuchen.androidforward.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 只记录转发状态，不记录完整正文。这样能排查问题，又不会把短信/通知内容留在日志里。
 */
class ForwardLogRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 增加一条日志，并把总数限制在最近 100 条。 */
    fun add(type: String, source: String, success: Boolean, detail: String) {
        val current = loadJsonArray()
        val item = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("type", type)
            .put("source", source.take(80))
            .put("success", success)
            .put("detail", detail.take(160))

        val next = JSONArray().put(item)
        for (index in 0 until minOf(current.length(), MAX_LOG_ITEMS - 1)) {
            next.put(current.getJSONObject(index))
        }

        prefs.edit().putString(KEY_LOGS, next.toString()).apply()
    }

    /** 读取最近日志，设置页用它展示调试状态。 */
    fun list(): List<ForwardLogItem> {
        val array = loadJsonArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ForwardLogItem(
                        time = item.optLong("time"),
                        type = item.optString("type"),
                        source = item.optString("source"),
                        success = item.optBoolean("success"),
                        detail = item.optString("detail")
                    )
                )
            }
        }
    }

    /** 清空状态日志，不影响 Bark Key 和其它设置。 */
    fun clear() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private fun loadJsonArray(): JSONArray {
        return runCatching {
            JSONArray(prefs.getString(KEY_LOGS, "[]") ?: "[]")
        }.getOrDefault(JSONArray())
    }

    companion object {
        private const val PREFS_NAME = "android_forward_logs"
        private const val KEY_LOGS = "logs"
        private const val MAX_LOG_ITEMS = 100
    }
}

data class ForwardLogItem(
    val time: Long,
    val type: String,
    val source: String,
    val success: Boolean,
    val detail: String
)
