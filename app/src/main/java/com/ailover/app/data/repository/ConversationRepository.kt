package com.ailover.app.data.repository

import com.ailover.app.data.local.dao.ConversationDao
import com.ailover.app.data.local.entity.ConversationEntity
import com.ailover.app.data.local.relation.ConversationWithCharacter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap

class ConversationRepository(private val conversationDao: ConversationDao) {

    // 内存缓存：会话 ID -> 会话实体
    private val cache = ConcurrentHashMap<Long, ConversationEntity>()

    fun getAllConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getAllConversations().onEach { conversations ->
            conversations.forEach { cache[it.id] = it }
        }

    fun getConversationsByCharacter(characterId: Long): Flow<List<ConversationEntity>> =
        conversationDao.getConversationsByCharacter(characterId).onEach { conversations ->
            conversations.forEach { cache[it.id] = it }
        }

    suspend fun getConversationById(id: Long): ConversationEntity? {
        cache[id]?.let { return it }
        val conversation = conversationDao.getConversationById(id)
        conversation?.let { cache[it.id] = it }
        return conversation
    }

    suspend fun insertConversation(conversation: ConversationEntity): Long {
        val id = conversationDao.insertConversation(conversation)
        conversationDao.getConversationById(id)?.let { cache[it.id] = it }
        return id
    }

    suspend fun updateConversation(conversation: ConversationEntity) {
        val updated = conversation.copy(updatedAt = System.currentTimeMillis())
        conversationDao.updateConversation(updated)
        cache[updated.id] = updated
    }

    suspend fun deleteConversation(conversation: ConversationEntity) {
        conversationDao.deleteConversation(conversation)
        cache.remove(conversation.id)
    }

    suspend fun deleteConversationById(id: Long) {
        conversationDao.deleteConversationById(id)
        cache.remove(id)
    }

    fun getConversationsWithCharacters(): Flow<List<ConversationWithCharacter>> =
        conversationDao.getConversationsWithCharacters()
}
