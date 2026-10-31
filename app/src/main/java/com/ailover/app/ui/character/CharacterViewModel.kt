package com.ailover.app.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.repository.CharacterRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ========== 角色列表 ViewModel ==========
class CharacterListViewModel(
    characterRepository: CharacterRepository
) : ViewModel() {

    // 初始值用缓存数据：缓存命中则直接显示列表，避免进页面闪烁
    private val cachedCharacters = characterRepository.getCachedCharacters()

    // 加载中状态：缓存有数据则直接 false，不显示加载中
    private val _isLoading = MutableStateFlow(cachedCharacters.isEmpty())
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val characters: StateFlow<List<CharacterEntity>> =
        characterRepository.getAllCharacters()
            .onEach { _isLoading.value = false }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = cachedCharacters
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

    // ===== 角色卡 v2.0 新增字段 =====
    private val _background = MutableStateFlow("")
    val background: StateFlow<String> = _background
    private val _speechStyle = MutableStateFlow("")
    val speechStyle: StateFlow<String> = _speechStyle
    private val _behaviorRules = MutableStateFlow("")
    val behaviorRules: StateFlow<String> = _behaviorRules
    private val _relationshipSetup = MutableStateFlow("")
    val relationshipSetup: StateFlow<String> = _relationshipSetup
    private val _greeting = MutableStateFlow("")
    val greeting: StateFlow<String> = _greeting
    private val _exampleDialogues = MutableStateFlow("")
    val exampleDialogues: StateFlow<String> = _exampleDialogues
    private val _narrativeStyle = MutableStateFlow("")
    val narrativeStyle: StateFlow<String> = _narrativeStyle
    private val _characterTags = MutableStateFlow("")
    val characterTags: StateFlow<String> = _characterTags

    private val _isEditMode = MutableStateFlow(characterId != null)
    val isEditMode: StateFlow<Boolean> = _isEditMode

    // 保存/删除完成事件，UI 层在主线程收集后执行导航
    private val _operationCompleteEvent = MutableSharedFlow<Unit>()
    val operationCompleteEvent: SharedFlow<Unit> = _operationCompleteEvent.asSharedFlow()

    init {
        if (characterId != null) {
            viewModelScope.launch {
                characterRepository.getCharacterById(characterId)?.let { character ->
                    _name.value = character.name
                    _personality.value = character.personality
                    _avatarUri.value = character.avatarUri
                    _background.value = character.background
                    _speechStyle.value = character.speechStyle
                    _behaviorRules.value = character.behaviorRules
                    _relationshipSetup.value = character.relationshipSetup
                    _greeting.value = character.greeting
                    _exampleDialogues.value = character.exampleDialogues
                    _narrativeStyle.value = character.narrativeStyle
                    _characterTags.value = character.characterTags
                }
            }
        }
    }

    fun onNameChange(value: String) { _name.value = value }
    fun onPersonalityChange(value: String) { _personality.value = value }
    fun onAvatarChange(value: String?) { _avatarUri.value = value }
    fun onBackgroundChange(value: String) { _background.value = value }
    fun onSpeechStyleChange(value: String) { _speechStyle.value = value }
    fun onBehaviorRulesChange(value: String) { _behaviorRules.value = value }
    fun onRelationshipSetupChange(value: String) { _relationshipSetup.value = value }
    fun onGreetingChange(value: String) { _greeting.value = value }
    fun onExampleDialoguesChange(value: String) { _exampleDialogues.value = value }
    fun onNarrativeStyleChange(value: String) { _narrativeStyle.value = value }
    fun onCharacterTagsChange(value: String) { _characterTags.value = value }

    // 非 suspend 入口：在 viewModelScope 中执行，完成后发事件
    fun save() {
        viewModelScope.launch {
            val id = saveAndGetId()
            if (id != null) {
                _operationCompleteEvent.emit(Unit)
            }
        }
    }

    fun deleteCharacter() {
        viewModelScope.launch {
            delete()
            _operationCompleteEvent.emit(Unit)
        }
    }

    suspend fun saveAndGetId(): Long? {
        val trimmedName = _name.value.trim()
        if (trimmedName.isEmpty()) return null

        return if (characterId != null) {
            // 编辑模式：用 update，不能用 insert REPLACE（会先删后插，级联删除会话和消息）
            val character = CharacterEntity(
                id = characterId,
                name = trimmedName,
                avatarUri = _avatarUri.value,
                personality = _personality.value.trim(),
                background = _background.value.trim(),
                speechStyle = _speechStyle.value.trim(),
                behaviorRules = _behaviorRules.value.trim(),
                relationshipSetup = _relationshipSetup.value.trim(),
                greeting = _greeting.value.trim(),
                exampleDialogues = _exampleDialogues.value.trim(),
                narrativeStyle = _narrativeStyle.value.trim(),
                characterTags = _characterTags.value.trim()
            )
            characterRepository.updateCharacter(character)
            characterId
        } else {
            // 新建模式：用 insert
            val character = CharacterEntity(
                name = trimmedName,
                avatarUri = _avatarUri.value,
                personality = _personality.value.trim(),
                background = _background.value.trim(),
                speechStyle = _speechStyle.value.trim(),
                behaviorRules = _behaviorRules.value.trim(),
                relationshipSetup = _relationshipSetup.value.trim(),
                greeting = _greeting.value.trim(),
                exampleDialogues = _exampleDialogues.value.trim(),
                narrativeStyle = _narrativeStyle.value.trim(),
                characterTags = _characterTags.value.trim()
            )
            characterRepository.insertCharacter(character)
        }
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
