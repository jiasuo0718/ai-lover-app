package com.ailover.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailover.app.data.settings.ApiPlatforms
import com.ailover.app.data.settings.ApiProfile
import com.ailover.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(
        ApiProfile(
            id = UUID.randomUUID().toString(),
            name = "默认配置",
            platform = "deepseek",
            baseUrl = ApiPlatforms.DEEPSEEK.defaultBaseUrl,
            apiKey = "",
            modelName = ApiPlatforms.DEEPSEEK.defaultModel
        )
    )
    val settings: StateFlow<ApiProfile> = _settings.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settingsRepository.getActiveProfileOnce()
            _settings.value = saved
        }
    }

    fun onPlatformChange(platformId: String) {
        val platform = ApiPlatforms.getById(platformId)
        _settings.value = _settings.value.copy(
            platform = platform.id,
            baseUrl = if (platform.defaultBaseUrl.isNotBlank()) platform.defaultBaseUrl else _settings.value.baseUrl,
            modelName = if (platform.defaultModel.isNotBlank()) platform.defaultModel else _settings.value.modelName
        )
    }

    fun onBaseUrlChange(value: String) {
        _settings.value = _settings.value.copy(baseUrl = value)
    }

    fun onApiKeyChange(value: String) {
        _settings.value = _settings.value.copy(apiKey = value)
    }

    fun onModelNameChange(value: String) {
        _settings.value = _settings.value.copy(modelName = value)
    }

    fun save() {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                settingsRepository.updateActiveProfile(_settings.value)
                _saveState.value = SaveState.Saved
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "保存失败")
            }
        }
    }

    sealed class SaveState {
        object Idle : SaveState()
        object Saving : SaveState()
        object Saved : SaveState()
        data class Error(val message: String) : SaveState()
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(settingsRepository) as T
    }
}
