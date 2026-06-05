package com.zhaoyuchen.androidforward.forward

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Bark HTTP 客户端。使用系统 HttpURLConnection，避免为了一个 POST 请求引入额外网络库。
 */
object BarkClient {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    /** 发送 Bark JSON 推送，成功标准是 HTTP 2xx 且 Bark 响应 code 不是错误。 */
    fun send(baseUrl: String, barkKey: String, payload: ForwardPayload): ForwardResult {
        if (barkKey.isBlank()) {
            return ForwardResult(false, "Bark Key 为空")
        }

        return runCatching {
            val endpoint = buildEndpoint(baseUrl, barkKey)
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val body = JSONObject()
                .put("title", payload.title)
                .put("body", payload.body)
                .put("group", payload.group)
                .put("level", payload.level)
                .toString()

            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val responseText = readResponse(connection)
            connection.disconnect()

            if (code in 200..299 && isBarkSuccess(responseText)) {
                ForwardResult(true, "Bark 已接收")
            } else {
                ForwardResult(false, "Bark 返回 HTTP $code：${responseText.take(120)}")
            }
        }.getOrElse { error ->
            ForwardResult(false, error.message ?: error.javaClass.simpleName)
        }
    }

    /** Bark 支持 POST JSON 到 https://api.day.app/{key}。 */
    private fun buildEndpoint(baseUrl: String, barkKey: String): URL {
        val normalizedBase = baseUrl.trim().trimEnd('/').ifBlank { "https://api.day.app" }
        val encodedKey = URLEncoder.encode(barkKey.trim(), "UTF-8")
        return URL("$normalizedBase/$encodedKey")
    }

    /** 读取正常或错误响应，便于日志里留下一点失败原因。 */
    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..399) {
            connection.inputStream
        } else {
            connection.errorStream ?: return ""
        }
        return stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
    }

    /** Bark 常见响应是 {"code":200,...}；非 JSON 响应只要 HTTP 成功就按成功处理。 */
    private fun isBarkSuccess(responseText: String): Boolean {
        if (responseText.isBlank()) return true
        return runCatching {
            val json = JSONObject(responseText)
            json.optInt("code", 200) == 200
        }.getOrDefault(true)
    }
}
