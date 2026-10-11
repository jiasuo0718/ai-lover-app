package com.ailover.app.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailover.app.data.local.converter.MessageType
import com.ailover.app.data.local.converter.SenderType
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.local.entity.MemoryEntity
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

    init {
        viewModelScope.launch {
            val conversation = conversationRepository.getConversationById(conversationId)
            conversation?.let {
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

            val systemPrompt = character?.let { buildSystemPrompt(it, ruleTemplate, novelMode) }
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
        }
    }

    /**
     * 构建 system prompt：角色人设 + 说话风格 + 规则模板 + 小说模式。
     * 规则在后，不覆盖人设，只补充规则。
     */
    private fun buildSystemPrompt(character: CharacterEntity, ruleTemplate: RuleTemplate, novelMode: Boolean): String {
        val personalityPart = character.personality?.takeIf { it.isNotBlank() }?.let { "你的人设：$it\n" } ?: ""
        val rulePart = if (ruleTemplate.content.isNotBlank()) "\n\n${ruleTemplate.content}" else ""
        val novelPart = if (novelMode) """

【小说模式】
- 第一人称视角，用户是"我"
- 回复用小说文体：场景/氛围 + 角色动作神态（用括号） + 对白
- 有画面感，像在演故事/写小说，但别过度冗长
- 用户主导行动（用户说"我做什么"，你接着写场景和角色反应）
- 保持角色人设""" else ""
        return """你是${character.name}。${personalityPart}你正在和用户微信聊天，请：
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
     * 从用户消息中提取关键信息，存入记忆库。
     * 只提取用户说的，不提取AI/角色说的。
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

            val extractPrompt = """
                从以下用户说的话中提取关于用户的【事实性信息】，每条一行，格式：[类型] 内容
                类型定义：
                - 名字：用户的名字
                - 喜好：用户喜欢/爱好的事物
                - 事件：用户经历的事（养猫、去某地、面试、生病等）
                - 关系：用户和你的关系（如"我们是朋友""用户说喜欢我"）
                - 其他：以上都不是的事实性信息

                【该记的】关于用户的事实：名字、喜好、经历、关系（如"我养了仓鼠叫团子""我叫小白""我下周三面试"）
                【不该记的】用户的寒暄、提问、感叹、无信息量的对话（如"你好""好吧""你在干嘛""哈哈哈""在吗""团子是谁"）
                只提取用户明确说的事实，不要推测，不要提取你（AI）说的话。没有值得记的事实就回复"无"。
                用户说的话：
                $userOnlyMessages
            """.trimIndent()

            val extractResult = StringBuilder()
            chatRepository.streamChat(
                baseUrl = settings.baseUrl,
                apiKey = settings.apiKey,
                profileName = settings.name,
                request = ChatRequest(
                    model = settings.modelName,
                    messages = listOf(ChatMessage("system", extractPrompt)),
                    stream = true
                )
            ).collect { chunk -> extractResult.append(chunk) }

            val raw = extractResult.toString().trim()
            if (raw.isEmpty() || raw == "无") return

            // 宽松解析：按行分割，每行尝试匹配 [类型] 内容 或 类型：内容
            val validTypes = setOf("名字", "喜好", "事件", "关系", "其他")
            val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

            // 查已有记忆（可变列表，方便添加新存入的供后续比较）
            val existingMemories = memoryRepository.getMemoriesByCharacterOnce(characterId, 200).toMutableList()

            for (line in lines) {
                var type = ""
                var content = ""

                // 匹配 [类型] 内容 或 [类型]内容
                val bracketMatch = Regex("^\\[(.+?)\\]\\s*(.+)$").find(line)
                if (bracketMatch != null) {
                    type = bracketMatch.groupValues[1].trim()
                    content = bracketMatch.groupValues[2].trim()
                } else {
                    // 匹配 类型：内容 或 类型:内容
                    val colonMatch = Regex("^(.+?)[：:]\\s*(.+)$").find(line)
                    if (colonMatch != null) {
                        type = colonMatch.groupValues[1].trim()
                        content = colonMatch.groupValues[2].trim()
                    }
                }

                // 类型校验 + 内容非空
                if (type !in validTypes || content.isEmpty()) continue

                // 去重：归一化后比较（完全相同 + 包含/被包含关系）
                val normalized = normalizeForDedup(content)
                var skip = false
                var memoryToUpdate: MemoryEntity? = null

                for (existing in existingMemories) {
                    val existingNormalized = normalizeForDedup(existing.content)
                    // 完全相同 → 跳过
                    if (normalized == existingNormalized) {
                        skip = true
                        break
                    }
                    // 太短的内容不做包含检查（避免"金鱼"匹配所有含金鱼的）
                    if (normalized.length >= 4 && existingNormalized.length >= 4) {
                        // 新内容是已有内容的子集（已有更全）→ 跳过
                        if (existingNormalized.contains(normalized)) {
                            skip = true
                            break
                        }
                        // 新内容包含已有内容（新更全）→ 更新已有
                        if (normalized.contains(existingNormalized)) {
                            memoryToUpdate = existing
                            break
                        }
                    }
                }

                if (skip) continue

                // 重要性按类型默认
                val importance = when (type) {
                    "名字" -> 5
                    "关系" -> 4
                    "喜好" -> 4
                    "事件" -> 3
                    else -> 2
                }

                if (memoryToUpdate != null) {
                    // 合并：更新已有记忆为更全的内容
                    memoryRepository.updateMemory(
                        memoryToUpdate.copy(
                            content = content,
                            type = type,
                            importance = importance,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    // 更新已有列表中的内容，供后续比较
                    val idx = existingMemories.indexOfFirst { it.id == memoryToUpdate.id }
                    if (idx >= 0) {
                        existingMemories[idx] = existingMemories[idx].copy(
                            content = content, type = type, importance = importance
                        )
                    }
                    Log.d("SummaryDebug", "合并记忆: [$type] $content (替换旧记忆)")
                } else {
                    // 存新记忆
                    val newId = memoryRepository.insertMemory(
                        MemoryEntity(
                            characterId = characterId,
                            type = type,
                            content = content,
                            importance = importance,
                            sourceConversationId = sourceConversationId
                        )
                    )
                    // 加入已有列表，供后续条目比较
                    existingMemories.add(
                        MemoryEntity(
                            id = newId,
                            characterId = characterId,
                            type = type,
                            content = content,
                            importance = importance
                        )
                    )
                    Log.d("SummaryDebug", "存入记忆: [$type] $content")
                }
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
