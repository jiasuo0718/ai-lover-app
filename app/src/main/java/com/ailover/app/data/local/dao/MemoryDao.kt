package com.ailover.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ailover.app.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE characterId = :characterId ORDER BY importance DESC, createdAt DESC")
    fun getMemoriesByCharacter(characterId: Long): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE characterId = :characterId ORDER BY importance DESC, createdAt DESC LIMIT :limit")
    suspend fun getMemoriesByCharacterOnce(characterId: Long, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memories WHERE characterId = :characterId")
    suspend fun deleteMemoriesByCharacter(characterId: Long)

    @Query("SELECT COUNT(*) FROM memories WHERE characterId = :characterId AND content = :content")
    suspend fun countByContent(characterId: Long, content: String): Int
}
