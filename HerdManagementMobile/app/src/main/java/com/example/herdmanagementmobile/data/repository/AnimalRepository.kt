package com.example.herdmanagementmobile.data.repository

import com.example.herdmanagementmobile.data.local.dao.AnimalDao
import com.example.herdmanagementmobile.data.local.entity.AnimalEntity
import com.example.herdmanagementmobile.domain.model.Animal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimalRepository @Inject constructor(
    private val animalDao: AnimalDao,
) {
    fun observeAnimals(): Flow<List<Animal>> =
        animalDao.observeAll().map { list -> list.map(AnimalEntity::toDomain) }

    suspend fun getById(id: Long): Animal? = animalDao.getById(id)?.toDomain()

    suspend fun getByEarTag(earTag: String): Animal? = animalDao.getByEarTag(earTag)?.toDomain()

    suspend fun add(animal: Animal): Long = animalDao.insert(AnimalEntity.fromDomain(animal))

    suspend fun update(animal: Animal) = animalDao.update(AnimalEntity.fromDomain(animal))

    suspend fun delete(id: Long) = animalDao.deleteById(id)
}
