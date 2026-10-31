package com.ailover.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarUri: String? = null,
    val personality: String,
    val apiProfileId: String? = null,
    val relationshipScore: Int = 0,
    val lastScoreDate: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // ===== 角色卡 v2.0 新增字段（中性叙事升级，默认空串） =====
    val background: String = "",
    val speechStyle: String = "",
    val behaviorRules: String = "",
    val relationshipSetup: String = "",
    val greeting: String = "",
    val exampleDialogues: String = "",
    val narrativeStyle: String = "",
    val characterTags: String = ""
)
