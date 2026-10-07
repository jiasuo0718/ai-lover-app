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

private val Context.userProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")

/**
 * 用户个人资料仓库，用 DataStore 持久化用户头像和昵称。
 */
class UserProfileRepository(private val context: Context) {

    data class UserProfile(
        val nickname: String = "我",
        val avatarUri: String? = null
    )

    val userProfile: Flow<UserProfile> = context.userProfileDataStore.data.map { prefs ->
        UserProfile(
            nickname = prefs[KEY_NICKNAME] ?: "我",
            avatarUri = prefs[KEY_AVATAR_URI]
        )
    }

    suspend fun saveNickname(nickname: String) {
        context.userProfileDataStore.edit { prefs ->
            prefs[KEY_NICKNAME] = nickname
        }
    }

    suspend fun saveAvatarUri(avatarUri: String?) {
        context.userProfileDataStore.edit { prefs ->
            if (avatarUri != null) {
                prefs[KEY_AVATAR_URI] = avatarUri
            } else {
                prefs.remove(KEY_AVATAR_URI)
            }
        }
    }

    suspend fun saveProfile(nickname: String, avatarUri: String?) {
        context.userProfileDataStore.edit { prefs ->
            prefs[KEY_NICKNAME] = nickname
            if (avatarUri != null) {
                prefs[KEY_AVATAR_URI] = avatarUri
            } else {
                prefs.remove(KEY_AVATAR_URI)
            }
        }
    }

    suspend fun getProfileOnce(): UserProfile = userProfile.first()

    companion object {
        private val KEY_NICKNAME = stringPreferencesKey("user_nickname")
        private val KEY_AVATAR_URI = stringPreferencesKey("user_avatar_uri")
    }
}
