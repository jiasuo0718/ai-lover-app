package com.ailover.app.data.remote.model

/**
 * 流式响应（SSE）的单个 data 块
 * 格式: data: {"id":"...","choices":[{"delta":{"content":"..."}}]}
 */
data class ChatChunk(
    val id: String? = null,
    val choices: List<ChunkChoice> = emptyList()
)

data class ChunkChoice(
    val delta: ChunkDelta = ChunkDelta(),
    val finish_reason: String? = null,
    val index: Int = 0
)

data class ChunkDelta(
    val role: String? = null,
    val content: String? = null
)
