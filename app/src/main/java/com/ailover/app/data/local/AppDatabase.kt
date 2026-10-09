package com.ailover.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 4,
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE characters ADD COLUMN apiProfileId TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN sendStatus INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 清理旧版本（2107f8f及之前）残留的错误 AI 消息和空占位消息。
        // 旧版本失败时把错误提示词写进了 AI 消息 content，导致升级后这些消息被当作正常 AI 回复渲染。
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    DELETE FROM messages 
                    WHERE senderType = 'AI' 
                    AND content IN (
                        '',
                        'API Key 无效，请到设置中检查',
                        '余额不足，请充值',
                        '请求太频繁，请稍后再试',
                        '服务器繁忙，请稍后再试',
                        '请求失败，请重试',
                        '网络超时，请检查网络后重试',
                        '网络连接失败，请检查网络'
                    )
                    """
                )
                // 清理旧版本 Key 空时插入的 SYSTEM 提示消息
                database.execSQL(
                    """
                    DELETE FROM messages 
                    WHERE senderType = 'SYSTEM' 
                    AND content IN (
                        '请先在设置中配置 API Key 和模型名称',
                        '请先在设置中配置 API Key'
                    )
                    """
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ailover_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
