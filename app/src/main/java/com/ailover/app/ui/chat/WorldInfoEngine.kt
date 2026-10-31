package com.ailover.app.ui.chat

import com.ailover.app.data.local.entity.MemoryEntity

/**
 * SillyTavern「World Info（世界书/动态记忆）」机制的轻量复刻。
 *
 * 核心思想（来自 ST）：聊天时不把所有设定一股脑发给 AI，而是扫描最近消息里的关键词，
 * 只激活（retrieve）与当前话题相关的背景注入 Prompt，并按相关度/顺序控制注入量。
 *
 * 我们项目 [MemoryEntity] 没有单独的 keywords 触发列（为规避 Room 迁移，不改数据库），
 * 因此用「关键词相关性打分」近似 ST 的 key 命中：
 *   - 从用户最新发言提取候选关键词（中文 2/3 字 n-gram + 按标点切出的词块）
 *   - 记忆 content 命中越多、越长关键词 → 相关度越高，importance 作 tiebreak
 *   - 按相关度取 Top N 注入；无命中则整体不注入（等价于 ST 的"未激活不注入"）
 *
 * 纯逻辑无 IO，便于单元测试与后续扩展（如接入真实分词、向量检索等）。
 */
object WorldInfoEngine {

    private const val CJK_START = '\u4e00'
    private const val CJK_END = '\u9fff'

    private val SPLIT_REGEX =
        Regex("[\\s，。！？、；：,.!?;:〜～…·\"'“”‘’（）()《》<>\\[\\]【】]+")

    /**
     * 从用户消息提取候选关键词。
     * 中文无空格分词：采用 2~3 字 CJK n-gram；含字母/数字的整块 token 整体作为关键词。
     */
    fun extractKeywords(text: String): Set<String> {
        val result = mutableSetOf<String>()
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return result

        // 按标点/空白切词：含字母/数字/混合的 token 整体作为关键词（长度 >= 2）
        cleaned.split(SPLIT_REGEX)
            .filter { it.isNotBlank() }
            .forEach { token ->
                if (token.any { it !in CJK_START..CJK_END } && token.length >= 2) {
                    result.add(token)
                }
            }

        // CJK 2/3 字 n-gram：覆盖中文短话题（如"猫""吵架""期末考试"）
        val chars = cleaned.filter { it.isLetterOrDigit() }
        for (i in 0 until chars.length - 1) {
            val bigram = chars.substring(i, i + 2)
            if (bigram.all { it in CJK_START..CJK_END }) result.add(bigram)
        }
        for (i in 0 until chars.length - 2) {
            val trigram = chars.substring(i, i + 3)
            if (trigram.all { it in CJK_START..CJK_END }) result.add(trigram)
        }
        return result
    }

    /**
     * 对候选记忆按与用户消息的相关度打分，返回命中的 Top N。
     * 规则近似 ST 的「更多 key 命中 → 更相关」：
     *   score = 命中关键词加权分（trigram 比 bigram 更具体、权重更高）*10 + importance
     * 返回按相关度降序，最多 [maxEntries] 条；无命中返回空列表。
     */
    fun rankRelevant(
        userMessage: String,
        memories: List<MemoryEntity>,
        maxEntries: Int = 6,
    ): List<MemoryEntity> {
        val keywords = extractKeywords(userMessage)
        if (keywords.isEmpty() || memories.isEmpty()) return emptyList()

        data class Scored(val memory: MemoryEntity, val score: Int)

        val scored = memories.mapNotNull { m ->
            val content = m.content
            if (content.isBlank()) return@mapNotNull null
            var hitScore = 0
            for (kw in keywords) {
                if (content.contains(kw)) {
                    hitScore += when {
                        kw.length >= 3 -> 3 // trigram / 长词块：更具体
                        kw.length == 2 -> 1 // bigram：一般
                        else -> 2
                    }
                }
            }
            if (hitScore > 0) Scored(m, hitScore * 10 + m.importance) else null
        }

        return scored
            .sortedByDescending { it.score }
            .take(maxEntries)
            .map { it.memory }
    }

    /**
     * 组装注入文本；无相关记忆时返回 null（对应 ST 的「该轮未激活任何条目」）。
     * 注入块以 system 消息插入，位于历史之前、与角色 System Prompt 分离。
     */
    fun buildInjection(
        userMessage: String,
        memories: List<MemoryEntity>,
        maxEntries: Int = 6,
    ): String? {
        val relevant = rankRelevant(userMessage, memories, maxEntries)
        if (relevant.isEmpty()) return null
        val text = relevant.joinToString("\n") { "- [${it.type}] ${it.content}" }
        return "你记得以下关于用户的相关信息（与当前话题相关，请据此自然回应，但不要生硬复述）：\n$text"
    }
}
