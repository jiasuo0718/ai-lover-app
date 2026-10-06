package com.ailover.app.data.repository

import com.ailover.app.data.remote.model.ChatChunk
import com.ailover.app.data.remote.model.ChatRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 聊天 API 仓库，封装 OpenAI Chat Completions 兼容格式的流式调用。
 * 使用 OkHttp 直接处理 SSE（Server-Sent Events），逐块 emit 文本内容。
 */
class ChatRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    /**
     * 流式聊天，返回 Flow，每个 emit 是 API 返回的一个文本片段。
     *
     * @param baseUrl API 基础地址，如 https://api.deepseek.com
     * @param apiKey API 密钥
     * @param request 请求体（stream 字段会被强制设为 true）
     * @return 文本片段的 Flow
     * @throws Exception 当 HTTP 状态码非 2xx 或网络错误时抛出
     */
    fun streamChat(
        baseUrl: String,
        apiKey: String,
        request: ChatRequest
    ): Flow<String> = flow {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val json = gson.toJson(request.copy(stream = true))

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "无错误详情"
                throw Exception("API 请求失败 (HTTP ${response.code}): $errorBody")
            }

            val body = response.body
                ?: throw Exception("API 返回空响应体")

            body.charStream().buffered().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue

                    // SSE 格式: "data: {json}" 或 "data: [DONE]"
                    if (currentLine.startsWith("data:")) {
                        val data = currentLine.removePrefix("data:").trim()

                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue

                        try {
                            val chunk = gson.fromJson(data, ChatChunk::class.java)
                            val content = chunk.choices.firstOrNull()?.delta?.content
                            if (content != null && content.isNotEmpty()) {
                                emit(content)
                            }
                        } catch (e: Exception) {
                            // 忽略无法解析的块（可能是心跳或其他格式）
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
