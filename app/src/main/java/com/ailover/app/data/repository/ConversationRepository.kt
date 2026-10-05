package com.ailover.app.data.repository

import com.ailover.app.data.local.dao.ConversationDao
import com.ailover.app.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

class ConversationRepository(private val conversationDao: ConversationDao) {
    fun getAllConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getAllConversations()

    fun getConversationsByCharacter(characterId: Long): Flow<List<ConversationEntity>> =
        conversationDao.getConversationsByCharacter(characterId)

    suspend fun getConversationById(id: Long): ConversationEntity? =
        conversationDao.getConversationById(id)

    suspend fun insertConversation(conversation: ConversationEntity): Long =
        conversationDao.insertConversation(conversation)

    suspend fun updateConversation(conversation: ConversationEntity) =
        conversationDao.updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteConversation(conversation: ConversationEntity) =
        conversationDao.deleteConversation(conversation)

    suspend fun deleteConversationById(id: Long) = conversationDao.deleteConversationById(id)
}
