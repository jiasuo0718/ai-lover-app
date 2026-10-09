package com.ailover.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ailover.app.data.local.converter.MessageType
import com.ailover.app.data.local.converter.SenderType

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val senderType: SenderType,
    val messageType: MessageType,
    val content: String,
    val voiceDuration: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = true,
    val sendStatus: Int = 0
) {
    companion object {
        const val SEND_STATUS_SUCCESS = 0
        const val SEND_STATUS_SENDING = 1
        const val SEND_STATUS_FAILED = 2
    }
}
