package com.ailover.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailover.app.data.local.converter.MessageType
import com.ailover.app.data.local.converter.SenderType
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.local.entity.MessageEntity
import com.ailover.app.data.remote.model.ChatMessage
import com.ailover.app.data.remote.model.ChatRequest
import com.ailover.app.data.repository.CharacterRepository
import com.ailover.app.data.repository.ChatApiException
import com.ailover.app.data.repository.ChatRepository
import com.ailover.app.data.repository.ConversationRepository
import com.ailover.app.data.repository.MessageRepository
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
    private val settingsRepository: SettingsRepository
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
        val systemPrompt = character?.personality?.takeIf { it.isNotBlank() }
        val settings = resolveApiProfile(character)

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

        // 6. 构建历史消息（排除 AI 占位、SYSTEM、失败消息）
        val history = messageRepository.getMessagesByConversationOnce(conversationId)
            .filter {
                it.id != aiMessageId &&
                it.senderType != SenderType.SYSTEM &&
                it.sendStatus != MessageEntity.SEND_STATUS_FAILED
            }
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

        val requestMessages = buildList {
            systemPrompt?.let { add(ChatMessage("system", it)) }
            addAll(history)
        }

        // 7. 流式调用 API
        _isStreaming.value = true
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
        } catch (e: ChatApiException) {
            // 失败：删除 AI 占位，标记用户消息失败，顶部提示
            messageRepository.deleteMessage(aiMessage)
            _errorMessage.value = e.userMessage
            messageRepository.getMessageById(userMessageId)?.let {
                messageRepository.updateMessage(it.copy(sendStatus = MessageEntity.SEND_STATUS_FAILED))
            }
        } catch (e: Exception) {
            messageRepository.deleteMessage(aiMessage)
            _errorMessage.value = "请求失败，请重试"
            messageRepository.getMessageById(userMessageId)?.let {
                messageRepository.updateMessage(it.copy(sendStatus = MessageEntity.SEND_STATUS_FAILED))
            }
        } finally {
            _isStreaming.value = false
            if (fullContent.isNotBlank()) {
                updateConversationLastMessage(fullContent.toString())
            }
        }
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
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(
            conversationId,
            messageRepository,
            conversationRepository,
            characterRepository,
            chatRepository,
            settingsRepository
        ) as T
    }
}
