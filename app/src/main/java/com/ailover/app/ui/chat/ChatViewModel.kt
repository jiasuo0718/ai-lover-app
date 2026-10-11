package com.ailover.app.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailover.app.data.local.converter.MessageType
import com.ailover.app.data.local.converter.SenderType
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.local.entity.ConversationEntity
import com.ailover.app.data.local.entity.MemoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ailover.app.data.local.entity.MessageEntity
import com.ailover.app.data.remote.model.ChatMessage
import com.ailover.app.data.remote.model.ChatRequest
import com.ailover.app.data.repository.CharacterRepository
import com.ailover.app.data.repository.ChatApiException
import com.ailover.app.data.repository.ChatRepository
import com.ailover.app.data.repository.ConversationRepository
import com.ailover.app.data.repository.MemoryRepository
import com.ailover.app.data.repository.MessageRepository
import com.ailover.app.data.settings.RolePlayRules
import com.ailover.app.data.settings.RuleTemplate
import com.ailover.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val conversationId: Long,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val characterRepository: CharacterRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    val messages: StateFlow<List<MessageEntity>> =
        messageRepository.getMessagesByConversation(conversationId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _character = MutableStateFlow<CharacterEntity?>(null)
    val character: StateFlow<CharacterEntity?> = _character.asStateFlow()

    private val _conversation = MutableStateFlow<ConversationEntity?>(null)
    val conversation: StateFlow<ConversationEntity?> = _conversation.asStateFlow()

    init {
        viewModelScope.launch {
            val conversation = conversationRepository.getConversationById(conversationId)
            conversation?.let {
                _conversation.value = it
                _character.value = characterRepository.getCharacterById(it.characterId)
            }
        }
    }

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun onInputTextChange(text: String) {
        _inputText.value = text
        if (text.isNotBlank()) {
            _errorMessage.value = null
        }
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isStreaming.value) return
        _inputText.value = ""
        viewModelScope.launch { sendText(text) }
    }

    fun resendMessage(messageId: Long) {
        if (_isStreaming.value) return
        viewModelScope.launch {
            val msg = messageRepository.getMessageById(messageId) ?: return@launch
            if (msg.senderType != SenderType.USER) return@launch
            sendText(msg.content, existingMessageId = messageId)
        }
    }

    private suspend fun sendText(text: String, existingMessageId: Long? = null) {
        _errorMessage.value = null

        // 1. 用户消息：新插入 or 重发复用
        val userMessageId = if (existingMessageId != null) {
            val msg = messageRepository.getMessageById(existingMessageId) ?: return
            messageRepository.updateMessage(msg.copy(sendStatus = MessageEntity.SEND_STATUS_SENDING))
            existingMessageId
        } else {
            messageRepository.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    senderType = SenderType.USER,
                    messageType = MessageType.TEXT,
                    content = text,
                    sendStatus = MessageEntity.SEND_STATUS_SENDING
                )
            )
        }

        // 2. 更新会话最后消息
        updateConversationLastMessage(text)

        // 3. 获取角色和 API 配置
        val conversation = conversationRepository.getConversationById(conversationId)
        val character = conversation?.let {
            characterRepository.getCharacterById(it.characterId)
        }
        val settings = resolveApiProfile(character)

        // 获取对话规则配置
        val selectedRuleId = settingsRepository.getSelectedRuleOnce()
        val autoRotate = settingsRepository.getAutoRotateOnce()
        val currentRuleIndex = settingsRepository.getCurrentRuleIndexOnce()
        val novelMode = settingsRepository.getNovelModeOnce()

        // 4. Key 空：标记用户消息失败，不创建 AI 气泡
        if (!settings.isConfigured()) {
            _errorMessage.value = "「${settings.name}」缺少 API Key，请先在设置中配置"
            messageRepository.getMessageById(userMessageId)?.let {
                messageRepository.updateMessage(it.copy(sendStatus = MessageEntity.SEND_STATUS_FAILED))
            }
            return
        }

        // 5. 创建 AI 回复占位消息
        val aiMessageId = messageRepository.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                senderType = SenderType.AI,
                messageType = MessageType.TEXT,
                content = ""
            )
        )
        var aiMessage = messageRepository.getMessageById(aiMessageId) ?: return

        // 6. 构建历史消息（排除 AI 占位、SYSTEM、失败消息，只带最近20条）
        val history = messageRepository.getMessagesByConversationOnce(conversationId)
            .filter {
                it.id != aiMessageId &&
                it.senderType != SenderType.SYSTEM &&
                it.sendStatus != MessageEntity.SEND_STATUS_FAILED
            }
            .takeLast(20)
            .map { msg ->
                ChatMessage(
                    role = when (msg.senderType) {
                        SenderType.USER -> "user"
                        SenderType.AI -> "assistant"
                        SenderType.SYSTEM -> "system"
                    },
                    content = msg.content
                )
            }

        // 6.5 跨会话记忆：该角色其他会话的最近10条消息（按时间正序）
        val crossSessionHistory = character?.let { c ->
            messageRepository.getRecentMessagesByCharacter(c.id, conversationId, 10)
                .reversed()
                .map { msg ->
                    ChatMessage(
                        role = when (msg.senderType) {
                            SenderType.USER -> "user"
                            SenderType.AI -> "assistant"
                            SenderType.SYSTEM -> "system"
                        },
                        content = msg.content
                    )
                }
        } ?: emptyList()

        // 6.6 记忆库：该角色的关键信息记忆（最多15条，按重要性+时间降序）
        val memories = character?.let { c ->
            memoryRepository.getMemoriesByCharacterOnce(c.id, 15)
        } ?: emptyList()

        // 6.7 会话摘要：该角色所有会话的摘要（最多5个）
        val conversationSummaries = character?.let { c ->
            conversationRepository.getConversationsWithSummaryByCharacter(c.id, 5)
                .mapNotNull { it.summary }
        } ?: emptyList()

        // 构建除 system 外的消息（记忆、摘要、跨会话历史、当前会话历史）
        val otherMessages = buildList {
            if (memories.isNotEmpty()) {
                val memoryText = memories.joinToString("\n") { "- [${it.type}] ${it.content}" }
                Log.d("SummaryDebug", "拼入记忆: 共${memories.size}条\n$memoryText")
                add(ChatMessage("system", "你记得以下关于用户的信息：\n$memoryText"))
            }
            if (conversationSummaries.isNotEmpty()) {
                val summaryText = conversationSummaries.joinToString("\n") { "- $it" }
                Log.d("SummaryDebug", "拼入摘要: $summaryText")
                add(ChatMessage("system", "你记得以下关于用户的信息（来自之前的会话）：\n$summaryText"))
            }
            if (crossSessionHistory.isNotEmpty()) {
                add(ChatMessage("system", "以下是你和用户之前的对话记录（来自之前的会话），供参考："))
                addAll(crossSessionHistory)
            }
            addAll(history)
        }

        // 7. 流式调用 API（支持规则轮换 + 被拒重试一次）
        _isStreaming.value = true
        var attempt = 0
        var finalContent = ""
        var sendSuccess = false

        while (attempt < 2) {
            // 本次尝试用的规则
            val ruleTemplate = if (autoRotate) {
                RolePlayRules.ROTATABLE[(currentRuleIndex + attempt) % RolePlayRules.ROTATABLE.size]
            } else {
                RolePlayRules.getById(selectedRuleId)
            }

            val systemPrompt = character?.let { buildSystemPrompt(it, ruleTemplate, novelMode, conversation?.mood ?: "平淡", conversation?.reaction ?: "平静") }
            val requestMessages = buildList {
                systemPrompt?.let { add(ChatMessage("system", it)) }
                addAll(otherMessages)
            }

            // 重试时清空上一次的部分内容
            if (attempt > 0) {
                aiMessage = messageRepository.getMessageById(aiMessageId)?.copy(content = "") ?: aiMessage
                messageRepository.updateMessage(aiMessage)
            }

            val fullContent = StringBuilder()
            try {
                chatRepository.streamChat(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    profileName = settings.name,
                    request = ChatRequest(
                        model = settings.modelName,
                        messages = requestMessages,
                        stream = true
                    )
                ).collect { chunk ->
                    fullContent.append(chunk)
                    aiMessage = aiMessage.copy(content = fullContent.toString())
                    messageRepository.updateMessage(aiMessage)
                }
                // 成功：标记用户消息发送成功
                messageRepository.getMessageById(userMessageId)?.let {
                    messageRepository.updateMessage(it.copy(sendStatus = MessageEntity.SEND_STATUS_SUCCESS))
                }
                finalContent = fullContent.toString()
                sendSuccess = true
                // 自动轮换：更新到下一套
                if (autoRotate) {
                    settingsRepository.setCurrentRuleIndex(
                        (currentRuleIndex + attempt + 1) % RolePlayRules.ROTATABLE.size
                    )
                }
                // 关系值+1（每天上限20）
                character?.let { c ->
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val (lastDate, todayGain) = parseScoreDate(c.lastScoreDate)
                    if (lastDate != today) {
                        // 新的一天，重置计数并+1
                        characterRepository.updateRelationshipScore(c.id, c.relationshipScore + 1, "$today:1")
                    } else if (todayGain < 20) {
                        // 今天还没到上限，+1
                        characterRepository.updateRelationshipScore(c.id, c.relationshipScore + 1, "$today:${todayGain + 1}")
                    }
                }
                break
            } catch (e: Exception) {
                // 判断是否可以重试：第一次尝试 + 开启自动轮换 + 不是最后一套
                val canRetry = attempt == 0 && autoRotate &&
                    (currentRuleIndex + attempt) < RolePlayRules.ROTATABLE.size - 1
                if (canRetry) {
                    attempt++
                    continue
                }
                // 失败：删除 AI 占位，标记用户消息失败，顶部提示
                messageRepository.deleteMessage(aiMessage)
                _errorMessage.value = when (e) {
                    is ChatApiException -> e.userMessage
                    else -> "请求失败，请重试"
                }
                messageRepository.getMessageById(userMessageId)?.let {
                    messageRepository.updateMessage(it.copy(sendStatus = MessageEntity.SEND_STATUS_FAILED))
                }
                break
            }
        }

        _isStreaming.value = false
        if (sendSuccess && finalContent.isNotBlank()) {
            updateConversationLastMessage(finalContent)
            generateSummaryIfNeeded()
            updateEmotionIfNeeded()
        }
    }

    /**
     * 解析 lastScoreDate：格式 "yyyy-MM-dd:count"，返回 (日期, 今天已涨分数)。
     */
    private fun parseScoreDate(lastScoreDate: String?): Pair<String, Int> {
        if (lastScoreDate.isNullOrBlank()) return "" to 0
        val parts = lastScoreDate.split(":")
        return if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else lastScoreDate to 0
    }

    /**
     * 关系阶段：5档，带5分缓冲（升档到阈值才升，降档留5分缓冲）。
     */
    data class RelationshipStage(val name: String, val description: String, val minScore: Int)

    private fun getRelationshipStage(score: Int): RelationshipStage {
        return when {
            score >= 200 -> RelationshipStage("挚友", "你们已经是灵魂伴侣，无话不谈，深度信任", 200)
            score >= 100 -> RelationshipStage("亲密", "你们很亲近，可以撒娇、依赖、分享情绪", 100)
            score >= 50 -> RelationshipStage("朋友", "你们很熟了，会吐槽、关心日常", 50)
            score >= 20 -> RelationshipStage("熟悉", "你们开始熟悉，会开玩笑，偶尔主动找话题", 20)
            else -> RelationshipStage("陌生", "你们刚认识，客气礼貌，有距离感", 0)
        }
    }

    /**
     * 构建 system prompt：角色人设 + 关系阶段 + 情绪 + 说话风格 + 规则模板 + 小说模式。
     * 规则在后，不覆盖人设，只补充规则。
     */
    private fun buildSystemPrompt(character: CharacterEntity, ruleTemplate: RuleTemplate, novelMode: Boolean, mood: String, reaction: String): String {
        val personalityPart = character.personality?.takeIf { it.isNotBlank() }?.let { "你的人设：$it\n" } ?: ""
        val stage = getRelationshipStage(character.relationshipScore)
        val relationshipPart = """
你和用户的关系阶段：${stage.name}（${stage.description}）。
请用【${stage.name}】的态度和语气回复，但保持角色人设，别过度演绎。"""
        val emotionPart = """
你现在的心情基调是【$mood】，刚才的事让你【$reaction】。
用这种状态回复，保持人设，别过度演绎。"""
        val rulePart = if (ruleTemplate.content.isNotBlank()) "\n\n${ruleTemplate.content}" else ""
        val novelPart = if (novelMode) """

【小说模式】
- 第一人称视角，用户是"我"
- 回复用小说文体：场景/氛围 + 角色动作神态（用括号） + 对白
- 有画面感，像在演故事/写小说，但别过度冗长
- 用户主导行动（用户说"我做什么"，你接着写场景和角色反应）
- 保持角色人设""" else ""
        return """你是${character.name}。${personalityPart}$relationshipPart
$emotionPart
你正在和用户微信聊天，请：
- 用日常口语，像真人发消息一样，别书面、别舞台腔
- 回复可以很短，只说一两个词也行（"哈哈""哦""嗯"），不用每句都完整回应或总结
- 语气词（呢/啦/呀）偶尔用就行，别每句都堆
- 不用每句都表达关心或情绪，自然点
- 不要说"作为AI""我是人工智能"之类
- 称呼用户为"你"$rulePart$novelPart"""
    }

    /**
     * 异步生成会话摘要，不阻塞聊天。
     * 每10条消息生成一次（10, 20, 30...），覆盖更新。
     * 失败不影响聊天。
     */
    private fun generateSummaryIfNeeded() {
        viewModelScope.launch {
            try {
                val conversation = conversationRepository.getConversationById(conversationId)
                    ?: return@launch
                val character = characterRepository.getCharacterById(conversation.characterId)
                    ?: return@launch
                val settings = resolveApiProfile(character)
                if (!settings.isConfigured()) return@launch

                // 统计有效消息数
                val allMessages = messageRepository.getMessagesByConversationOnce(conversationId)
                    .filter {
                        it.senderType != SenderType.SYSTEM &&
                        it.sendStatus == MessageEntity.SEND_STATUS_SUCCESS &&
                        it.content.isNotBlank()
                    }
                val shouldGenerate = allMessages.size >= 10 && allMessages.size % 10 == 0
                Log.d("SummaryDebug", "当前消息数=${allMessages.size}, 是否触发=$shouldGenerate")
                // 每10条生成一次
                if (!shouldGenerate) return@launch

                // 取最近20条消息
                val recentMessages = allMessages.takeLast(20).joinToString("\n") { msg ->
                    val role = if (msg.senderType == SenderType.USER) "用户" else "你"
                    "$role: ${msg.content}"
                }

                // 只取用户消息（用于记忆提取，不提取AI说的/角色设定）
                val userOnlyMessages = allMessages.takeLast(20)
                    .filter { it.senderType == SenderType.USER }
                    .joinToString("\n") { msg -> "用户: ${msg.content}" }

                val summaryPrompt = """
                    请从以下对话中提炼关键信息（用户的名字、喜好、重要事件、关系等），用简短的几句话总结。
                    如果没有值得记住的信息，只回复"无"。
                    对话：
                    $recentMessages
                """.trimIndent()

                val summary = StringBuilder()
                chatRepository.streamChat(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    profileName = settings.name,
                    request = ChatRequest(
                        model = settings.modelName,
                        messages = listOf(ChatMessage("system", summaryPrompt)),
                        stream = true
                    )
                ).collect { chunk -> summary.append(chunk) }

                val summaryText = summary.toString().trim()
                if (summaryText.isNotEmpty() && summaryText != "无") {
                    Log.d("SummaryDebug", "生成摘要: 会话ID=$conversationId, 内容=$summaryText")
                    conversationRepository.updateConversation(
                        conversation.copy(summary = summaryText.take(300))
                    )
                }

                // ===== 第3步：提取关键信息存记忆库 =====
                extractAndSaveMemories(character.id, conversation.id, userOnlyMessages, settings)
            } catch (_: Exception) {
                // 摘要生成失败不影响聊天
            }
        }
    }

    /**
     * 异步判断角色当前情绪（底色 mood + 即时反应 reaction）。
     * 每次 AI 回复成功后调用，不阻塞聊天。
     * - 底色（mood）：慢变，有惯性（50%概率保持旧值）
     * 即时反应（reaction）：快变，直接更新
     */
    private fun updateEmotionIfNeeded() {
        viewModelScope.launch {
            try {
                val conversation = conversationRepository.getConversationById(conversationId)
                    ?: return@launch
                val character = characterRepository.getCharacterById(conversation.characterId)
                    ?: return@launch
                val settings = resolveApiProfile(character)
                if (!settings.isConfigured()) return@launch

                // 取最近6条对话
                val allMessages = messageRepository.getMessagesByConversationOnce(conversationId)
                    .filter {
                        it.senderType != SenderType.SYSTEM &&
                        it.sendStatus == MessageEntity.SEND_STATUS_SUCCESS &&
                        it.content.isNotBlank()
                    }
                    .takeLast(6)
                if (allMessages.isEmpty()) return@launch

                val recentDialog = allMessages.joinToString("\n") { msg ->
                    val role = if (msg.senderType == SenderType.USER) "用户" else "你"
                    "$role: ${msg.content}"
                }

                val oldMood = conversation.mood
                val emotionPrompt = """
                    根据以下最近对话，判断角色（${character.name}）当前的情绪状态。

                    【角色当前心情底色】：$oldMood
                    【最近对话】：
                    $recentDialog

                    【底色】（四选一，变化慢，没有明显情绪变化就保持原值）：
                    放松 / 平淡 / 低落 / 亢奋

                    【即时反应】（五选一，针对最近一句/几句的直接反应）：
                    平静 / 被逗乐 / 被戳到 / 被暖到 / 被惹毛

                    只输出两行，格式：
                    底色：X
                    即时反应：Y
                    不要其他内容。
                """.trimIndent()

                val result = StringBuilder()
                chatRepository.streamChat(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    profileName = settings.name,
                    request = ChatRequest(
                        model = settings.modelName,
                        messages = listOf(ChatMessage("system", emotionPrompt)),
                        stream = true
                    )
                ).collect { chunk -> result.append(chunk) }

                // 解析输出
                val raw = result.toString().trim()
                var newMood = oldMood
                var newReaction = conversation.reaction

                for (line in raw.lines()) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("底色") || trimmed.startsWith("心情底色") -> {
                            val value = trimmed.substringAfter("：").substringAfter(":").trim()
                            if (value in setOf("放松", "平淡", "低落", "亢奋")) newMood = value
                        }
                        trimmed.startsWith("即时反应") || trimmed.startsWith("反应") -> {
                            val value = trimmed.substringAfter("：").substringAfter(":").trim()
                            if (value in setOf("平静", "被逗乐", "被戳到", "被暖到", "被惹毛")) newReaction = value
                        }
                    }
                }

                // 底色惯性：如果新底色 != 旧底色，50%概率保持旧底色（慢变）
                if (newMood != oldMood && kotlin.random.Random.nextBoolean()) {
                    newMood = oldMood
                }

                // 只有变化了才更新
                if (newMood != conversation.mood || newReaction != conversation.reaction) {
                    conversationRepository.updateMoodAndReaction(conversationId, newMood, newReaction)
                    _conversation.value = conversationRepository.getConversationById(conversationId)
                    Log.d("EmotionDebug", "情绪更新: 底色=${conversation.mood}->$newMood, 反应=${conversation.reaction}->$newReaction")
                }
            } catch (_: Exception) {
                // 情绪判断失败不影响聊天
            }
        }
    }

    /**
     * 从用户消息中提取关键信息，存入记忆库。
     * 两步走：第一步列候选，第二步严格审核（判断该不该记、类型、和已有关系）。
     * 宽松解析，格式不对就跳过，不崩。
     */
    private suspend fun extractAndSaveMemories(
        characterId: Long,
        sourceConversationId: Long,
        userOnlyMessages: String,
        settings: com.ailover.app.data.settings.ApiProfile
    ) {
        try {
            if (userOnlyMessages.isBlank()) return

            // ===== 第一步：列候选 =====
            val candidatePrompt = """
                从以下用户说的话中，列出所有可能值得记住的候选信息，每条一行，格式：[类型] 内容
                类型只能是：名字、喜好、事件、关系、其他
                不要判断该不该记，先全部列出来（包括可能的）。没有候选就回复"无"。
                用户说的话：
                $userOnlyMessages
            """.trimIndent()

            val candidateResult = StringBuilder()
            chatRepository.streamChat(
                baseUrl = settings.baseUrl,
                apiKey = settings.apiKey,
                profileName = settings.name,
                request = ChatRequest(
                    model = settings.modelName,
                    messages = listOf(ChatMessage("system", candidatePrompt)),
                    stream = true
                )
            ).collect { chunk -> candidateResult.append(chunk) }

            val candidateRaw = candidateResult.toString().trim()
            if (candidateRaw.isEmpty() || candidateRaw == "无") return

            // 解析候选
            val validTypes = setOf("名字", "喜好", "事件", "关系", "其他")
            val candidates = candidateRaw.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val m = Regex("^\\[(.+?)\\]\\s*(.+)$").find(line)
                    if (m != null) {
                        val t = m.groupValues[1].trim()
                        val c = m.groupValues[2].trim()
                        if (t in validTypes && c.isNotEmpty()) t to c else null
                    } else null
                }
            if (candidates.isEmpty()) return

            // ===== 第二步：严格审核 =====
            val existingMemories = memoryRepository.getMemoriesByCharacterOnce(characterId, 50)
            val existingListText = if (existingMemories.isEmpty()) {
                "（暂无已有记忆）"
            } else {
                existingMemories.mapIndexed { index, mem ->
                    "${index + 1}. [${mem.type}] ${mem.content}"
                }.joinToString("\n")
            }
            val candidateListText = candidates.mapIndexed { i, (t, c) ->
                "${i + 1}. [$t] $c"
            }.joinToString("\n")

            val reviewPrompt = """
                你是一个严格的记忆审核员。以下是候选记忆和已有记忆。

                【已有记忆】
                $existingListText

                【候选记忆】
                $candidateListText

                请严格审核每条候选：
                【不该记的】未来计划、当下动作、感慨、玩笑、关于角色的、提问、推断、无信息量
                【该记的】关于用户的明确事实（名字、喜好、长期经历、关系状态）

                和已有记忆比对：
                - 重复/包含 → 合并（保留最全的，更新已有）
                - 新事实 → 新增
                - 不该记 → 跳过

                输出格式（每条一行）：
                [新增] [类型] 内容
                [更新] 已有序号 → [类型] 新内容
                [跳过]
                没有需要处理的就回复"无"。
            """.trimIndent()

            val reviewResult = StringBuilder()
            chatRepository.streamChat(
                baseUrl = settings.baseUrl,
                apiKey = settings.apiKey,
                profileName = settings.name,
                request = ChatRequest(
                    model = settings.modelName,
                    messages = listOf(ChatMessage("system", reviewPrompt)),
                    stream = true
                )
            ).collect { chunk -> reviewResult.append(chunk) }

            val reviewRaw = reviewResult.toString().trim()
            if (reviewRaw.isEmpty() || reviewRaw == "无") return

            // 解析审核结果，执行操作
            val lines = reviewRaw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            for (line in lines) {
                // [新增] [类型] 内容
                val addMatch = Regex("^\\[新增\\]\\s*\\[(.+?)\\]\\s*(.+)$").find(line)
                if (addMatch != null) {
                    val type = addMatch.groupValues[1].trim()
                    val content = addMatch.groupValues[2].trim()
                    if (type in validTypes && content.isNotEmpty()) {
                        val importance = when (type) {
                            "名字" -> 5; "关系" -> 4; "喜好" -> 4; "事件" -> 3; else -> 2
                        }
                        memoryRepository.insertMemory(
                            MemoryEntity(
                                characterId = characterId, type = type, content = content,
                                importance = importance, sourceConversationId = sourceConversationId
                            )
                        )
                        Log.d("SummaryDebug", "两步走-新增: [$type] $content")
                    }
                    continue
                }
                // [更新] 已有序号 → [类型] 新内容
                val updateMatch = Regex("^\\[更新\\]\\s*(\\d+)\\s*[→\\-:]\\s*\\[(.+?)\\]\\s*(.+)$").find(line)
                if (updateMatch != null) {
                    val idx = updateMatch.groupValues[1].toIntOrNull() ?: 0
                    val type = updateMatch.groupValues[2].trim()
                    val content = updateMatch.groupValues[3].trim()
                    if (idx in 1..existingMemories.size && type in validTypes && content.isNotEmpty()) {
                        val target = existingMemories[idx - 1]
                        val importance = when (type) {
                            "名字" -> 5; "关系" -> 4; "喜好" -> 4; "事件" -> 3; else -> 2
                        }
                        memoryRepository.updateMemory(
                            target.copy(
                                type = type, content = content, importance = importance,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        Log.d("SummaryDebug", "两步走-更新#$idx: [$type] $content")
                    }
                    continue
                }
                // [跳过] 或其他 → 忽略
            }
        } catch (_: Exception) {
            // 提取失败不影响聊天
        }
    }

    /**
     * 归一化用于去重：去掉"用户""我"等前缀、去空格标点、小写。
     */
    private fun normalizeForDedup(text: String): String {
        return text
            .replace(Regex("^(用户|我|你|他|她)"), "")
            .replace(Regex("[\\s，。、！？；：,.!?;:\"'\"']"), "")
            .lowercase()
    }

    /**
     * 发送表情消息（纯表情，不触发 AI 请求）。
     */
    fun sendEmojiMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val userMessage = MessageEntity(
                conversationId = conversationId,
                senderType = SenderType.USER,
                messageType = MessageType.TEXT,
                content = text
            )
            messageRepository.insertMessage(userMessage)
            _inputText.value = ""
            _errorMessage.value = null
            updateConversationLastMessage(text)
        }
    }

    /**
     * 发送语音消息。
     * @param filePath 语音文件路径
     * @param duration 时长（秒）
     */
    fun sendVoiceMessage(filePath: String, duration: Int) {
        viewModelScope.launch {
            val voiceMessage = MessageEntity(
                conversationId = conversationId,
                senderType = SenderType.USER,
                messageType = MessageType.VOICE,
                content = filePath,
                voiceDuration = duration
            )
            messageRepository.insertMessage(voiceMessage)
            updateConversationLastMessage("[语音] ${duration}″")
        }
    }

    private suspend fun updateConversationLastMessage(text: String) {
        val conversation = conversationRepository.getConversationById(conversationId)
        if (conversation != null) {
            conversationRepository.updateConversation(
                conversation.copy(lastMessage = text)
            )
        }
    }

    /**
     * 解析当前聊天应该用哪套 API 配置。
     * 优先级：角色绑定的 apiProfileId → 全局 active → 内存兜底默认。
     * 如果角色绑定的配置已被删除，静默 fallback 到全局 active。
     */
    private suspend fun resolveApiProfile(character: CharacterEntity?): com.ailover.app.data.settings.ApiProfile {
        val boundId = character?.apiProfileId
        if (!boundId.isNullOrBlank()) {
            val profiles = settingsRepository.getProfilesOnce()
            val bound = profiles.firstOrNull { it.id == boundId }
            if (bound != null) return bound
            // 角色绑定的配置被删了，fallthrough 到全局
        }
        return settingsRepository.getActiveProfileOnce()
    }
}

class ChatViewModelFactory(
    private val conversationId: Long,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val characterRepository: CharacterRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val memoryRepository: MemoryRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(
            conversationId,
            messageRepository,
            conversationRepository,
            characterRepository,
            chatRepository,
            settingsRepository,
            memoryRepository
        ) as T
    }
}
