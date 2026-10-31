package com.ailover.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * 设置仓库，用 DataStore 持久化多套 API 配置。
 * api_profiles 存 JSON 列表，active_api_profile_id 存当前默认套。
 * 旧用户的 4 个单 key 惰性迁移为一套 profile。
 */
class SettingsRepository(private val context: Context) {

    private val gson = Gson()

    // 新 key
    private val KEY_API_PROFILES = stringPreferencesKey("api_profiles")
    private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_api_profile_id")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_SELECTED_RULE = stringPreferencesKey("selected_rule")
    private val KEY_AUTO_ROTATE_RULE = booleanPreferencesKey("auto_rotate_rule")
    private val KEY_CURRENT_RULE_INDEX = intPreferencesKey("current_rule_index")
    private val KEY_NOVEL_MODE = booleanPreferencesKey("novel_mode")
    private val KEY_GLOBAL_SYSTEM_PROMPT = stringPreferencesKey("global_system_prompt")

    // 旧 key（迁移用，迁移后保留不删）
    private val KEY_OLD_BASE_URL = stringPreferencesKey("api_base_url")
    private val KEY_OLD_API_KEY = stringPreferencesKey("api_key")
    private val KEY_OLD_MODEL_NAME = stringPreferencesKey("model_name")
    private val KEY_OLD_PLATFORM = stringPreferencesKey("platform")

    // 内存兜底（列表为空时使用，不写入 DataStore）
    private val fallbackProfile = ApiProfile(
        id = "fallback",
        name = "默认",
        platform = "deepseek",
        baseUrl = ApiPlatforms.DEEPSEEK.defaultBaseUrl,
        apiKey = "",
        modelName = ApiPlatforms.DEEPSEEK.defaultModel
    )

    val apiProfiles: Flow<List<ApiProfile>> = context.dataStore.data
        .map { prefs -> parseProfiles(prefs[KEY_API_PROFILES]) }
        .onStart { ensureMigrated() }

    val activeApiProfile: Flow<ApiProfile> = context.dataStore.data
        .map { prefs ->
            val profiles = parseProfiles(prefs[KEY_API_PROFILES])
            val activeId = prefs[KEY_ACTIVE_PROFILE_ID]
            profiles.firstOrNull { it.id == activeId }
                ?: profiles.firstOrNull()
                ?: fallbackProfile
        }
        .onStart { ensureMigrated() }

    suspend fun getActiveProfileOnce(): ApiProfile = activeApiProfile.first()

    // 深色模式：system / light / dark，默认 system
    val themeMode: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_THEME_MODE] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    // 对话规则：none / A / B / C / D，默认 none
    val selectedRule: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_SELECTED_RULE] ?: "none" }

    suspend fun setSelectedRule(ruleId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_RULE] = ruleId
        }
    }

    // 自动轮换开关，默认 false
    val autoRotateRule: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_AUTO_ROTATE_RULE] ?: false }

    suspend fun setAutoRotateRule(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_ROTATE_RULE] = enabled
        }
    }

    // 当前轮换到第几套（自动轮换用），默认 0
    val currentRuleIndex: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_CURRENT_RULE_INDEX] ?: 0 }

    suspend fun setCurrentRuleIndex(index: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_RULE_INDEX] = index
        }
    }

    suspend fun getSelectedRuleOnce(): String = selectedRule.first()
    suspend fun getAutoRotateOnce(): Boolean = autoRotateRule.first()
    suspend fun getCurrentRuleIndexOnce(): Int = currentRuleIndex.first()

    // 小说模式，默认 false
    val novelMode: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_NOVEL_MODE] ?: false }

    suspend fun setNovelMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOVEL_MODE] = enabled
        }
    }

    suspend fun getNovelModeOnce(): Boolean = novelMode.first()

    // 全局系统提示词：用户自定义的前置提示词，本地持久化（DataStore）
    // 读取时为空字符串表示未设置
    suspend fun getGlobalSystemPrompt(): String {
        return context.dataStore.data.first()[KEY_GLOBAL_SYSTEM_PROMPT] ?: ""
    }

    suspend fun saveGlobalSystemPrompt(content: String) {
        context.dataStore.edit { prefs ->
            if (content.isBlank()) {
                prefs.remove(KEY_GLOBAL_SYSTEM_PROMPT)
            } else {
                prefs[KEY_GLOBAL_SYSTEM_PROMPT] = content
            }
        }
    }

    suspend fun getProfilesOnce(): List<ApiProfile> = apiProfiles.first()

    suspend fun addProfile(profile: ApiProfile) {
        context.dataStore.edit { prefs ->
            val list = parseProfiles(prefs[KEY_API_PROFILES]).toMutableList()
            list.add(profile)
            prefs[KEY_API_PROFILES] = gson.toJson(list)
            if (prefs[KEY_ACTIVE_PROFILE_ID] == null) {
                prefs[KEY_ACTIVE_PROFILE_ID] = profile.id
            }
        }
    }

    suspend fun updateProfile(profile: ApiProfile) {
        context.dataStore.edit { prefs ->
            val list = parseProfiles(prefs[KEY_API_PROFILES]).toMutableList()
            val idx = list.indexOfFirst { it.id == profile.id }
            if (idx >= 0) {
                list[idx] = profile
                prefs[KEY_API_PROFILES] = gson.toJson(list)
            }
        }
    }

    suspend fun deleteProfile(id: String) {
        context.dataStore.edit { prefs ->
            val list = parseProfiles(prefs[KEY_API_PROFILES]).toMutableList()
            list.removeAll { it.id == id }
            prefs[KEY_API_PROFILES] = gson.toJson(list)
            // 如果删的是 active，自动切到列表第一个
            if (prefs[KEY_ACTIVE_PROFILE_ID] == id) {
                list.firstOrNull()?.id?.let { prefs[KEY_ACTIVE_PROFILE_ID] = it }
                    ?: prefs.remove(KEY_ACTIVE_PROFILE_ID)
            }
        }
    }

    suspend fun setActiveProfile(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PROFILE_ID] = id
        }
    }

    /**
     * 更新当前 active profile 的内容（SettingsScreen 保存用）。
     * 如果 active 不存在（列表为空），则新增一套并设为 active。
     */
    suspend fun updateActiveProfile(profile: ApiProfile) {
        context.dataStore.edit { prefs ->
            val list = parseProfiles(prefs[KEY_API_PROFILES]).toMutableList()
            val activeId = prefs[KEY_ACTIVE_PROFILE_ID]
            val idx = list.indexOfFirst { it.id == activeId }
            if (idx >= 0) {
                list[idx] = profile.copy(id = list[idx].id)
            } else {
                list.add(profile)
                prefs[KEY_ACTIVE_PROFILE_ID] = profile.id
            }
            prefs[KEY_API_PROFILES] = gson.toJson(list)
        }
    }

    private fun parseProfiles(json: String?): List<ApiProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ApiProfile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 惰性迁移：旧的 4 个单 key → 新的 JSON 列表。
     * 幂等：api_profiles 已有值则跳过。
     */
    private suspend fun ensureMigrated() {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_API_PROFILES] != null) return@edit
            val oldApiKey = prefs[KEY_OLD_API_KEY]
            if (!oldApiKey.isNullOrBlank()) {
                val profile = ApiProfile(
                    id = UUID.randomUUID().toString(),
                    name = "默认配置",
                    platform = prefs[KEY_OLD_PLATFORM] ?: "deepseek",
                    baseUrl = prefs[KEY_OLD_BASE_URL] ?: ApiPlatforms.DEEPSEEK.defaultBaseUrl,
                    apiKey = oldApiKey,
                    modelName = prefs[KEY_OLD_MODEL_NAME] ?: ApiPlatforms.DEEPSEEK.defaultModel
                )
                prefs[KEY_API_PROFILES] = gson.toJson(listOf(profile))
                prefs[KEY_ACTIVE_PROFILE_ID] = profile.id
            }
            // 旧 key 保留不删
        }
    }
}
