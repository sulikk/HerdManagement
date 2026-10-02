package com.example.herdmanagementmobile.data.repository

import com.example.herdmanagementmobile.data.local.dao.AnimalDao
import com.example.herdmanagementmobile.data.local.dao.SyncDao
import com.example.herdmanagementmobile.data.local.entity.AnimalEntity
import com.example.herdmanagementmobile.data.sync.SyncManager
import com.example.herdmanagementmobile.domain.model.Animal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimalRepository @Inject constructor(
    private val animalDao: AnimalDao,
    private val syncDao: SyncDao,
    private val syncManager: SyncManager,
) {
    fun observeAnimals(): Flow<List<Animal>> =
        animalDao.observeAll().map { list -> list.map(AnimalEntity::toDomain) }

    fun observeById(id: Long): Flow<Animal?> =
        animalDao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: Long): Animal? = animalDao.getById(id)?.toDomain()

    suspend fun getByEarTag(earTag: String): Animal? = animalDao.getByEarTag(earTag)?.toDomain()

    suspend fun add(animal: Animal): Long {
        val id = animalDao.insert(AnimalEntity.fromDomain(animal))
        syncManager.enqueueAnimalCreate(animal.copy(id = id))
        return id
    }

    suspend fun update(animal: Animal) {
        animalDao.update(AnimalEntity.fromDomain(animal))
        syncManager.enqueueAnimalUpdate(animal)
    }

    /**
     * Usuwa zwierzę lokalnie (kaskadowo zdarzenia, leczenia i ogłoszenia) i kolejkuje
     * usunięcie na serwerze.
     *
     * Razem z nim muszą zniknąć powiązania identyfikatorów jego zdarzeń i leczeń —
     * kaskada Room ich nie obejmuje, bo `id_mapping` nie ma klucza obcego
     * (patrz [SyncDao.deleteChildMappingsForAnimal]). Kolejność jest istotna:
     * po kaskadzie nie da się już ustalić, które wiersze należały do tego zwierzęcia.
     */
    suspend fun delete(id: Long) {
        syncDao.deleteChildMappingsForAnimal(id)
        animalDao.deleteById(id)
        syncManager.enqueueAnimalDelete(id)
    }
}
