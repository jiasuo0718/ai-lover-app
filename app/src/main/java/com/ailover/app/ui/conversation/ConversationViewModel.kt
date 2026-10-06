package com.ailover.app.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailover.app.data.local.relation.ConversationWithCharacter
import com.ailover.app.data.repository.ConversationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ConversationViewModel(
    conversationRepository: ConversationRepository
) : ViewModel() {

    val conversations: StateFlow<List<ConversationWithCharacter>> =
        conversationRepository.getConversationsWithCharacters()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
}

class ConversationViewModelFactory(
    private val conversationRepository: ConversationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ConversationViewModel(conversationRepository) as T
    }
}
