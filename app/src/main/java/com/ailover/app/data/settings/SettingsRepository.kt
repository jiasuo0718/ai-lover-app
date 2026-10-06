package com.ailover.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * 设置仓库，用 DataStore 持久化 API 配置。
 * api_key 只存本地，不上传、不硬编码。
 */
class SettingsRepository(private val context: Context) {

    val apiSettings: Flow<ApiSettings> = context.dataStore.data.map { prefs ->
        ApiSettings(
            baseUrl = prefs[KEY_BASE_URL] ?: ApiPlatforms.DEEPSEEK.defaultBaseUrl,
            apiKey = prefs[KEY_API_KEY] ?: "",
            modelName = prefs[KEY_MODEL_NAME] ?: ApiPlatforms.DEEPSEEK.defaultModel,
            platform = prefs[KEY_PLATFORM] ?: "deepseek"
        )
    }

    suspend fun saveSettings(settings: ApiSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = settings.baseUrl
            prefs[KEY_API_KEY] = settings.apiKey
            prefs[KEY_MODEL_NAME] = settings.modelName
            prefs[KEY_PLATFORM] = settings.platform
        }
    }

    suspend fun getSettingsOnce(): ApiSettings = apiSettings.first()

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("api_base_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL_NAME = stringPreferencesKey("model_name")
        private val KEY_PLATFORM = stringPreferencesKey("platform")
    }
}
