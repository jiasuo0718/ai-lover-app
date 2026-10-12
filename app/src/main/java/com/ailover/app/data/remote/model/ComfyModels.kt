package com.ailover.app.data.remote.model

import com.google.gson.JsonObject

/**
 * ComfyUI 发送任务请求体。
 * 对应 POST /prompt 的 {"prompt": {...}, "client_id": "xxx"}。
 *
 * prompt 用 JsonObject 而非 Map<String,Any>：
 * Gson 把 JsonObject 转 Map 时会把所有数字解析成 Double（1024→1024.0），
 * ComfyUI 对 INT 字段（width/height/steps/seed/连接索引）严格校验，
 * 收到 float 会返回 400。直接用 JsonObject 能保留原始数字类型。
 */
data class ComfyRequest(
    val prompt: JsonObject,
    val client_id: String
)

/**
 * ComfyUI POST /prompt 的响应。
 * 成功时返回 {"prompt_id": "xxx", "number": N, "node_errors": {}}。
 */
data class ComfyPromptResponse(
    val prompt_id: String? = null,
    val number: Int? = null,
    val node_errors: Map<String, Any>? = null
)

/**
 * ComfyUI POST /upload/image 的响应。
 * 返回 {"name": "xxx.png", "subfolder": "", "type": "input"}。
 */
data class ComfyUploadResponse(
    val name: String? = null,
    val subfolder: String? = null,
    val type: String? = null
)

/**
 * ComfyUI GET /history/{prompt_id} 的响应结构（简化版）。
 * 实际返回是 Map<prompt_id, HistoryEntry>，这里只取需要的字段。
 */
data class ComfyHistoryEntry(
    val status: ComfyStatus? = null,
    val outputs: Map<String, ComfyOutput>? = null
)

data class ComfyStatus(
    val status_str: String? = null,
    val completed: Boolean? = null
)

data class ComfyOutput(
    val images: List<ComfyImage>? = null
)

data class ComfyImage(
    val filename: String? = null,
    val subfolder: String? = null,
    val type: String? = null
)
