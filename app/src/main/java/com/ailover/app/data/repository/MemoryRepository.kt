package com.ailover.app.data.repository

import com.ailover.app.data.local.dao.MemoryDao
import com.ailover.app.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {

    fun getMemoriesByCharacter(characterId: Long): Flow<List<MemoryEntity>> =
        memoryDao.getMemoriesByCharacter(characterId)

    suspend fun getMemoriesByCharacterOnce(characterId: Long, limit: Int): List<MemoryEntity> =
        memoryDao.getMemoriesByCharacterOnce(characterId, limit)

    suspend fun getMemoryById(id: Long): MemoryEntity? = memoryDao.getMemoryById(id)

    suspend fun insertMemory(memory: MemoryEntity): Long = memoryDao.insertMemory(memory)

    suspend fun updateMemory(memory: MemoryEntity) = memoryDao.updateMemory(memory)

    suspend fun deleteMemory(memory: MemoryEntity) = memoryDao.deleteMemory(memory)

    suspend fun deleteMemoryById(id: Long) = memoryDao.deleteMemoryById(id)

    suspend fun deleteMemoriesByCharacter(characterId: Long) =
        memoryDao.deleteMemoriesByCharacter(characterId)

    suspend fun existsByContent(characterId: Long, content: String): Boolean =
        memoryDao.countByContent(characterId, content) > 0
}
