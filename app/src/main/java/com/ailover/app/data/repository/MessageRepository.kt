package com.ailover.app.data.repository

import com.ailover.app.data.local.dao.MessageDao
import com.ailover.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

class MessageRepository(private val messageDao: MessageDao) {
    fun getMessagesByConversation(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.getMessagesByConversation(conversationId)

    suspend fun getMessagesByConversationOnce(conversationId: Long): List<MessageEntity> =
        messageDao.getMessagesByConversationOnce(conversationId)

    suspend fun getMessageById(id: Long): MessageEntity? = messageDao.getMessageById(id)

    suspend fun insertMessage(message: MessageEntity): Long = messageDao.insertMessage(message)

    suspend fun updateMessage(message: MessageEntity) = messageDao.updateMessage(message)

    suspend fun deleteMessage(message: MessageEntity) = messageDao.deleteMessage(message)

    suspend fun deleteMessagesByConversation(conversationId: Long) =
        messageDao.deleteMessagesByConversation(conversationId)

    suspend fun getLatestMessage(conversationId: Long): MessageEntity? =
        messageDao.getLatestMessage(conversationId)
}
