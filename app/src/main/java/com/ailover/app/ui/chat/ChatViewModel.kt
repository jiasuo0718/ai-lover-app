package com.ailover.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailover.app.data.local.converter.MessageType
import com.ailover.app.data.local.converter.SenderType
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

        viewModelScope.launch {
            // 1. 保存用户消息
            val userMessage = MessageEntity(
                conversationId = conversationId,
                senderType = SenderType.USER,
                messageType = MessageType.TEXT,
                content = text
            )
            messageRepository.insertMessage(userMessage)
            _inputText.value = ""
            _errorMessage.value = null

            // 2. 更新会话最后消息
            updateConversationLastMessage(text)

            // 3. 获取 API 配置
            val settings = settingsRepository.getSettingsOnce()
            if (!settings.isConfigured()) {
                _errorMessage.value = "请先在设置中配置 API Key"
                // 插入一条提示消息
                messageRepository.insertMessage(
                    MessageEntity(
                        conversationId = conversationId,
                        senderType = SenderType.SYSTEM,
                        messageType = MessageType.TEXT,
                        content = "请先在设置中配置 API Key 和模型名称"
                    )
                )
                return@launch
            }

            // 4. 获取角色人设（system prompt）
            val conversation = conversationRepository.getConversationById(conversationId)
            val character = conversation?.let {
                characterRepository.getCharacterById(it.characterId)
            }
            val systemPrompt = character?.personality?.takeIf { it.isNotBlank() }

            // 5. 创建 AI 回复占位消息
            val aiMessageId = messageRepository.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    senderType = SenderType.AI,
                    messageType = MessageType.TEXT,
                    content = ""
                )
            )
            var aiMessage = messageRepository.getMessageById(aiMessageId) ?: return@launch

            // 6. 构建历史消息列表（排除刚创建的 AI 占位）
            val history = messageRepository.getMessagesByConversationOnce(conversationId)
                .filter { it.id != aiMessageId && it.senderType != SenderType.SYSTEM }
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
                    request = ChatRequest(
                        model = settings.modelName,
                        messages = requestMessages,
                        stream = true
                    )
                ).collect { chunk ->
                    fullContent.append(chunk)
                    // 实时更新数据库，Room Flow 会自动刷新 UI
                    aiMessage = aiMessage.copy(content = fullContent.toString())
                    messageRepository.updateMessage(aiMessage)
                }
            } catch (e: ChatApiException) {
                // ChatApiException 已携带用户友好提示，原始错误已在 Repository 层写 logcat
                aiMessage = aiMessage.copy(content = e.userMessage)
                messageRepository.updateMessage(aiMessage)
                _errorMessage.value = e.userMessage
            } catch (e: Exception) {
                val errorText = "请求失败，请重试"
                aiMessage = aiMessage.copy(content = errorText)
                messageRepository.updateMessage(aiMessage)
                _errorMessage.value = errorText
            } finally {
                _isStreaming.value = false
                // 更新会话最后消息为 AI 回复（或错误信息）
                if (fullContent.isNotBlank()) {
                    updateConversationLastMessage(fullContent.toString())
                }
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
