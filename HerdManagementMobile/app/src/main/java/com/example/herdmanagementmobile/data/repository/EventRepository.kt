package com.example.herdmanagementmobile.data.repository

import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.entity.EventEntity
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao,
) {
    fun observeForAnimal(animalId: Long): Flow<List<AnimalEvent>> =
        eventDao.observeForAnimal(animalId).map { list -> list.map(EventEntity::toDomain) }

    fun observePending(): Flow<List<AnimalEvent>> =
        eventDao.observePending().map { list -> list.map(EventEntity::toDomain) }

    suspend fun add(event: AnimalEvent): Long = eventDao.insert(
        EventEntity(
            id = event.id,
            animalId = event.animalId,
            type = event.type,
            eventDate = event.eventDate,
            notes = event.notes,
            irzStatus = event.irzStatus,
            irzSubmittedAt = event.irzSubmittedAt,
        ),
    )
}
