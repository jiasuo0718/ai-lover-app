package com.ailover.app.data.repository

import com.ailover.app.data.local.dao.CharacterDao
import com.ailover.app.data.local.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow

class CharacterRepository(private val characterDao: CharacterDao) {
    fun getAllCharacters(): Flow<List<CharacterEntity>> = characterDao.getAllCharacters()

    suspend fun getCharacterById(id: Long): CharacterEntity? = characterDao.getCharacterById(id)

    suspend fun insertCharacter(character: CharacterEntity): Long =
        characterDao.insertCharacter(character)

    suspend fun updateCharacter(character: CharacterEntity) =
        characterDao.updateCharacter(character.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteCharacter(character: CharacterEntity) =
        characterDao.deleteCharacter(character)

    suspend fun deleteCharacterById(id: Long) = characterDao.deleteCharacterById(id)

    suspend fun getCharacterCount(): Int = characterDao.getCharacterCount()
}
