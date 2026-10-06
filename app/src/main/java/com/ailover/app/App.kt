package com.ailover.app

import android.app.Application
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.local.entity.ConversationEntity
import com.ailover.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // 初始化依赖注入容器
        AppContainer.init(this)

        // 全局崩溃捕获
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this, defaultHandler))

        // 首次启动插入默认角色和会话
        CoroutineScope(Dispatchers.IO).launch {
            val charRepo = AppContainer.characterRepository()
            val convRepo = AppContainer.conversationRepository()
            if (charRepo.getCharacterCount() == 0) {
                val charId = charRepo.insertCharacter(
                    CharacterEntity(
                        name = "小艾",
                        personality = "你是一个温柔体贴、善解人意的AI恋人。你会用温暖的语气和用户聊天，关心用户的生活，倾听用户的烦恼，给予情感支持。"
                    )
                )
                convRepo.insertConversation(
                    ConversationEntity(
                        characterId = charId,
                        title = "小艾",
                        lastMessage = "你好呀，今天过得怎么样？"
                    )
                )
            }
        }
    }
}
