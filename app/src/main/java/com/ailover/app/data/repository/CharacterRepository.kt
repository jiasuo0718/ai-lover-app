package com.ailover.app.data.repository

import com.ailover.app.data.local.dao.CharacterDao
import com.ailover.app.data.local.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap

class CharacterRepository(private val characterDao: CharacterDao) {

    // 内存缓存：角色 ID -> 角色实体
    // 会话列表/通讯录加载时填充，聊天页/资料页直接命中，避免进页面后再查数据库
    private val cache = ConcurrentHashMap<Long, CharacterEntity>()

    fun getAllCharacters(): Flow<List<CharacterEntity>> =
        characterDao.getAllCharacters().onEach { characters ->
            characters.forEach { cache[it.id] = it }
        }

    suspend fun getCharacterById(id: Long): CharacterEntity? {
        // 优先从内存缓存取，命中则不查数据库
        cache[id]?.let { return it }
        // 缓存未命中，查数据库并写入缓存
        val character = characterDao.getCharacterById(id)
        character?.let { cache[it.id] = it }
        return character
    }

    suspend fun insertCharacter(character: CharacterEntity): Long {
        val id = characterDao.insertCharacter(character)
        // 插入后更新缓存（用返回的 id）
        characterDao.getCharacterById(id)?.let { cache[it.id] = it }
        return id
    }

    suspend fun updateCharacter(character: CharacterEntity) {
        val updated = character.copy(updatedAt = System.currentTimeMillis())
        characterDao.updateCharacter(updated)
        cache[updated.id] = updated
    }

    suspend fun deleteCharacter(character: CharacterEntity) {
        characterDao.deleteCharacter(character)
        cache.remove(character.id)
    }

    suspend fun deleteCharacterById(id: Long) {
        characterDao.deleteCharacterById(id)
        cache.remove(id)
    }

    suspend fun getCharacterCount(): Int = characterDao.getCharacterCount()
}
