package com.ailover.app.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.local.entity.ConversationEntity
import com.ailover.app.data.local.relation.ConversationWithCharacter
import com.ailover.app.data.repository.CharacterRepository
import com.ailover.app.data.repository.ConversationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ConversationViewModel(
    private val conversationRepository: ConversationRepository,
    private val characterRepository: CharacterRepository
) : ViewModel() {

    val conversations: StateFlow<List<ConversationWithCharacter>> =
        conversationRepository.getConversationsWithCharacters()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val characters: StateFlow<List<CharacterEntity>> =
        characterRepository.getAllCharacters()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * 用指定角色创建新会话，返回新会话 ID。
     */
    suspend fun createConversation(character: CharacterEntity): Long {
        val conversation = ConversationEntity(
            characterId = character.id,
            title = character.name,
            lastMessage = "开始聊天吧"
        )
        return conversationRepository.insertConversation(conversation)
    }
}

class ConversationViewModelFactory(
    private val conversationRepository: ConversationRepository,
    private val characterRepository: CharacterRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ConversationViewModel(
            conversationRepository,
            characterRepository
        ) as T
    }
}
