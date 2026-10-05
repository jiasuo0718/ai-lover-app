package com.ailover.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ailover.app.data.local.converter.Converters
import com.ailover.app.data.local.dao.CharacterDao
import com.ailover.app.data.local.dao.ConversationDao
import com.ailover.app.data.local.dao.MessageDao
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.local.entity.ConversationEntity
import com.ailover.app.data.local.entity.MessageEntity

@Database(
    entities = [
        CharacterEntity::class,
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ailover_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
