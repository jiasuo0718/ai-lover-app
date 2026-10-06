package com.ailover.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.local.entity.ConversationEntity

data class ConversationWithCharacter(
    @Embedded val conversation: ConversationEntity,
    @Relation(
        parentColumn = "characterId",
        entityColumn = "id"
    )
    val character: CharacterEntity?
)
