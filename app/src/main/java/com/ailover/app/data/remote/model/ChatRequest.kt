package com.ailover.app.data.remote.model

/**
 * OpenAI Chat Completions 兼容格式的请求体
 * 参考: https://platform.openai.com/docs/api-reference/chat/create
 * DeepSeek 兼容此格式: https://api-docs.deepseek.com/
 */
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double = 0.7
)

data class ChatMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String
)
