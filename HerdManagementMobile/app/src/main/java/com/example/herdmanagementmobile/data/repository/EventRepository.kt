package com.example.herdmanagementmobile.data.repository

import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.entity.EventEntity
import com.example.herdmanagementmobile.data.sync.SyncManager
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.ComplianceItem
import com.example.herdmanagementmobile.domain.model.IrzStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val syncManager: SyncManager,
) {
    fun observeForAnimal(animalId: Long): Flow<List<AnimalEvent>> =
        eventDao.observeForAnimal(animalId).map { list -> list.map(EventEntity::toDomain) }

    fun observePending(): Flow<List<AnimalEvent>> =
        eventDao.observePending().map { list -> list.map(EventEntity::toDomain) }

    /** Zdarzenia niepotwierdzone przez IRZplus, jako pozycje widoku zgodności 7-dniowej. */
    fun observeComplianceItems(): Flow<List<ComplianceItem>> =
        eventDao.observeUnconfirmed().map { rows ->
            rows.map { row ->
                ComplianceItem(
                    eventId = row.eventId,
                    animalId = row.animalId,
                    earTag = row.earTag,
                    animalName = row.animalName,
                    type = row.type,
                    eventDate = row.eventDate,
                    irzStatus = row.irzStatus,
                    irzMessage = row.irzMessage,
                )
            }
        }

    /**
     * Zapisuje zdarzenie lokalnie i kolejkuje jego zapis w backendzie.
     * [submitToIrz] = true tylko wtedy, gdy użytkownik jawnie potwierdził zgłoszenie (wymaganie F12).
     */
    suspend fun add(event: AnimalEvent, submitToIrz: Boolean): Long {
        val id = eventDao.insert(
            EventEntity(
                id = event.id,
                animalId = event.animalId,
                type = event.type,
                eventDate = event.eventDate,
                notes = event.notes,
                partnerHoldingNumber = event.partnerHoldingNumber,
                irzStatus = IrzStatus.PENDING,
                irzSubmittedAt = null,
            ),
        )
        syncManager.enqueueEventCreate(event.copy(id = id, irzStatus = IrzStatus.PENDING))
        if (submitToIrz) submitToIrz(id)
        return id
    }

    /** Zgłoszenie zdarzenia do IRZplus — wołać wyłącznie po potwierdzeniu przez użytkownika. */
    suspend fun submitToIrz(eventId: Long) {
        eventDao.updateIrzResult(
            id = eventId,
            status = IrzStatus.SUBMITTED.name,
            message = null,
            documentNumber = null,
            submittedAt = null,
        )
        syncManager.enqueueIrzSubmit(eventId)
    }
}
