package com.ailover.app.data.remote.model

/**
 * ComfyUI 发送任务请求体。
 * 对应 POST /prompt 的 {"prompt": {...}, "client_id": "xxx"}。
 *
 * prompt 是一个 Map<String, Any>，key 是节点 ID，value 是节点配置 JSON。
 * 后续拿到真实工作流 JSON 后，只需替换其中正向/负面提示词节点的 text 字段。
 */
data class ComfyRequest(
    val prompt: Map<String, Any>,
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
