package com.ailover.app.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.repository.CharacterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ========== 角色列表 ViewModel ==========
class CharacterListViewModel(
    characterRepository: CharacterRepository
) : ViewModel() {

    val characters: StateFlow<List<CharacterEntity>> =
        characterRepository.getAllCharacters()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
}

class CharacterListViewModelFactory(
    private val characterRepository: CharacterRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CharacterListViewModel(characterRepository) as T
    }
}

// ========== 角色编辑 ViewModel ==========
class CharacterEditViewModel(
    private val characterId: Long?,
    private val characterRepository: CharacterRepository
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _personality = MutableStateFlow("")
    val personality: StateFlow<String> = _personality

    private val _avatarUri = MutableStateFlow<String?>(null)
    val avatarUri: StateFlow<String?> = _avatarUri

    private val _isEditMode = MutableStateFlow(characterId != null)
    val isEditMode: StateFlow<Boolean> = _isEditMode

    init {
        if (characterId != null) {
            viewModelScope.launch {
                characterRepository.getCharacterById(characterId)?.let { character ->
                    _name.value = character.name
                    _personality.value = character.personality
                    _avatarUri.value = character.avatarUri
                }
            }
        }
    }

    fun onNameChange(value: String) { _name.value = value }
    fun onPersonalityChange(value: String) { _personality.value = value }
    fun onAvatarChange(value: String?) { _avatarUri.value = value }

    suspend fun saveAndGetId(): Long? {
        val trimmedName = _name.value.trim()
        if (trimmedName.isEmpty()) return null

        val character = CharacterEntity(
            id = characterId ?: 0,
            name = trimmedName,
            avatarUri = _avatarUri.value,
            personality = _personality.value.trim()
        )
        return characterRepository.insertCharacter(character)
    }

    suspend fun delete() {
        if (characterId != null) {
            characterRepository.deleteCharacterById(characterId)
        }
    }
}

class CharacterEditViewModelFactory(
    private val characterId: Long?,
    private val characterRepository: CharacterRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CharacterEditViewModel(characterId, characterRepository) as T
    }
}
