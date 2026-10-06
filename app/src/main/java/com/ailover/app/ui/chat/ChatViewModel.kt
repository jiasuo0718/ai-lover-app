package com.ailover.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailover.app.data.local.converter.MessageType
import com.ailover.app.data.local.converter.SenderType
import com.ailover.app.data.local.entity.MessageEntity
import com.ailover.app.data.repository.ConversationRepository
import com.ailover.app.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val conversationId: Long,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    val messages: StateFlow<List<MessageEntity>> =
        messageRepository.getMessagesByConversation(conversationId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    fun onInputTextChange(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val message = MessageEntity(
                conversationId = conversationId,
                senderType = SenderType.USER,
                messageType = MessageType.TEXT,
                content = text
            )
            messageRepository.insertMessage(message)

            // 更新会话的最后消息和时间
            val conversation = conversationRepository.getConversationById(conversationId)
            if (conversation != null) {
                conversationRepository.updateConversation(
                    conversation.copy(lastMessage = text)
                )
            }

            _inputText.value = ""
        }
    }
}

class ChatViewModelFactory(
    private val conversationId: Long,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(conversationId, messageRepository, conversationRepository) as T
    }
}
