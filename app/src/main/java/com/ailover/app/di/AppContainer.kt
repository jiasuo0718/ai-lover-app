package com.ailover.app.di

import android.content.Context
import com.ailover.app.data.local.AppDatabase
import com.ailover.app.data.repository.CharacterRepository
import com.ailover.app.data.repository.ConversationRepository
import com.ailover.app.data.repository.MessageRepository

object AppContainer {
    @Volatile
    private var database: AppDatabase? = null
    private var characterRepo: CharacterRepository? = null
    private var conversationRepo: ConversationRepository? = null
    private var messageRepo: MessageRepository? = null

    fun init(context: Context) {
        if (database == null) {
            synchronized(this) {
                if (database == null) {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    database = db
                    characterRepo = CharacterRepository(db.characterDao())
                    conversationRepo = ConversationRepository(db.conversationDao())
                    messageRepo = MessageRepository(db.messageDao())
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
}
