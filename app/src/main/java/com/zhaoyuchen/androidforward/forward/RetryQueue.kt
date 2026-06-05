package com.zhaoyuchen.androidforward.forward

import android.content.Context
import com.zhaoyuchen.androidforward.data.AppSettingsRepository
import com.zhaoyuchen.androidforward.data.ForwardLogRepository
import com.zhaoyuchen.androidforward.data.SecureStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 加密保存失败推送，并按固定次数重试。
 */
object RetryQueue {
    private const val KEY_QUEUE = "retry_queue"
    private const val MAX_ITEMS = 50
    private const val MAX_ATTEMPTS = 5

    private val lock = Any()

    /** 把失败消息加入队列。队列内容包含正文，所以通过 SecureStore 加密保存。 */
    fun enqueue(context: Context, payload: ForwardPayload) {
        synchronized(lock) {
            val items = load(context).toMutableList()
            items.add(0, RetryItem(UUID.randomUUID().toString(), payload, attempts = 0))
            save(context, items.take(MAX_ITEMS))
        }
        RetryScheduler.schedule(context)
    }

    /** 后台线程执行重试，避免广播接收器阻塞主线程。 */
    fun flushAsync(context: Context) {
        val appContext = context.applicationContext
        Thread {
            flush(appContext)
        }.start()
    }

    /** 立即重试队列。发送成功的条目会被删除，失败条目达到上限后丢弃并写日志。 */
    fun flush(context: Context) {
        synchronized(lock) {
            val repository = AppSettingsRepository(context)
            val settings = repository.load()
            if (!settings.retryEnabled) return

            val barkKey = repository.getBarkKey()
            if (barkKey.isBlank()) return

            val remaining = mutableListOf<RetryItem>()
            val logs = ForwardLogRepository(context)

            for (item in load(context)) {
                val result = BarkClient.send(settings.barkServerUrl, barkKey, item.payload)
                if (result.success) {
                    logs.add(item.payload.type.displayTitle, item.payload.source, true, "重试成功")
                } else {
                    val nextAttempts = item.attempts + 1
                    if (nextAttempts < MAX_ATTEMPTS) {
                        remaining.add(item.copy(attempts = nextAttempts))
                    } else {
                        logs.add(
                            item.payload.type.displayTitle,
                            item.payload.source,
                            false,
                            "重试超过上限：${result.detail}"
                        )
                    }
                }
            }

            save(context, remaining)
            if (remaining.isNotEmpty()) {
                RetryScheduler.schedule(context)
            }
        }
    }

    private fun load(context: Context): List<RetryItem> {
        val raw = SecureStore.getString(context, KEY_QUEUE)
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val payloadJson = json.optJSONObject("payload") ?: continue
                    add(
                        RetryItem(
                            id = json.optString("id"),
                            payload = ForwardPayload.fromJson(payloadJson),
                            attempts = json.optInt("attempts", 0)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, items: List<RetryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("attempts", item.attempts)
                    .put("payload", item.payload.toJson())
            )
        }
        SecureStore.putString(context, KEY_QUEUE, array.toString())
    }

    private data class RetryItem(
        val id: String,
        val payload: ForwardPayload,
        val attempts: Int
    )
}
