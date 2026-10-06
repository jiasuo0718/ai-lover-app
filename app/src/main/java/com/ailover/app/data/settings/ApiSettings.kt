package com.ailover.app.data.settings

/**
 * API 设置数据类
 */
data class ApiSettings(
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val platform: String = "deepseek"
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()
}

/**
 * 支持的平台预设
 */
object ApiPlatforms {
    data class Platform(
        val id: String,
        val name: String,
        val defaultBaseUrl: String,
        val defaultModel: String,
        val supported: Boolean
    )

    val DEEPSEEK = Platform(
        id = "deepseek",
        name = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-chat",
        supported = true
    )

    val ALL = listOf(
        DEEPSEEK,
        Platform("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", supported = true),
        Platform("custom", "自定义", "", "", supported = true),
        // 以下平台暂不支持 OpenAI 兼容格式
        Platform("claude", "Claude (Anthropic)", "", "", supported = false),
        Platform("gemini", "Google Gemini", "", "", supported = false),
        Platform("ernie", "文心一言", "", "", supported = false),
        Platform("qwen", "通义千问", "", "", supported = false),
        Platform("doubao", "豆包", "", "", supported = false)
    )

    fun getById(id: String): Platform = ALL.firstOrNull { it.id == id } ?: DEEPSEEK
}
