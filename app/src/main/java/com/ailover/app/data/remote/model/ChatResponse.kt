package com.ailover.app.data.remote.model

/**
 * 非流式响应（备用，当前主要用流式）
 */
data class ChatResponse(
    val id: String? = null,
    val choices: List<ResponseChoice> = emptyList()
)

data class ResponseChoice(
    val message: ChatMessage? = null,
    val finish_reason: String? = null,
    val index: Int = 0
)
