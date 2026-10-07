package com.ailover.app.di

import android.content.Context
import com.ailover.app.data.local.AppDatabase
import com.ailover.app.data.repository.CharacterRepository
import com.ailover.app.data.repository.ChatRepository
import com.ailover.app.data.repository.ConversationRepository
import com.ailover.app.data.repository.MessageRepository
import com.ailover.app.data.settings.SettingsRepository
import com.ailover.app.data.settings.UserProfileRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object AppContainer {
    @Volatile
    private var database: AppDatabase? = null
    private var characterRepo: CharacterRepository? = null
    private var conversationRepo: ConversationRepository? = null
    private var messageRepo: MessageRepository? = null
    private var chatRepo: ChatRepository? = null
    private var settingsRepo: SettingsRepository? = null
    private var userProfileRepo: UserProfileRepository? = null
    private var okHttpClient: OkHttpClient? = null
    private var gson: Gson? = null

    fun init(context: Context) {
        if (database == null) {
            synchronized(this) {
                if (database == null) {
                    val appContext = context.applicationContext
                    val db = AppDatabase.getDatabase(appContext)
                    database = db
                    characterRepo = CharacterRepository(db.characterDao())
                    conversationRepo = ConversationRepository(db.conversationDao())
                    messageRepo = MessageRepository(db.messageDao())

                    // 网络层
                    val logging = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                    okHttpClient = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .addInterceptor(logging)
                        .build()
                    gson = GsonBuilder().create()
                    chatRepo = ChatRepository(okHttpClient!!, gson!!)

                    // 设置
                    settingsRepo = SettingsRepository(appContext)
                    userProfileRepo = UserProfileRepository(appContext)
                }
            }
        }
    }

    fun characterRepository(): CharacterRepository = characterRepo
        ?: error("AppContainer not initialized")

    fun conversationRepository(): ConversationRepository = conversationRepo
        ?: error("AppContainer not initialized")

    fun messageRepository(): MessageRepository = messageRepo
        ?: error("AppContainer not initialized")

    fun chatRepository(): ChatRepository = chatRepo
        ?: error("AppContainer not initialized")

    fun settingsRepository(): SettingsRepository = settingsRepo
        ?: error("AppContainer not initialized")

    fun userProfileRepository(): UserProfileRepository = userProfileRepo
        ?: error("AppContainer not initialized")
}
