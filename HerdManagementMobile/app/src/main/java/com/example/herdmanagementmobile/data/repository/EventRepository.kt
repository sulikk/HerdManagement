package com.example.herdmanagementmobile.data.repository

import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.entity.EventEntity
import com.example.herdmanagementmobile.data.sync.SyncManager
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.ComplianceItem
import com.example.herdmanagementmobile.domain.model.IrzStatus
import com.example.herdmanagementmobile.domain.model.allowsRemoval
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

    /** Zwraca zdarzenia niepotwierdzone przez IRZplus jako pozycje widoku zgodności. */
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
     *
     * @param submitToIrz `true` wyłącznie po potwierdzeniu zgłoszenia przez użytkownika (wymaganie F12).
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

    /**
     * Usuwa zdarzenie niezapisane w rejestrze (zob. [allowsRemoval]) i kolejkuje jego
     * usunięcie w backendzie. Reguła jest weryfikowana w repozytorium, niezależnie
     * od warstwy interfejsu.
     *
     * @return `false`, gdy zdarzenia nie można usunąć.
     */
    suspend fun delete(eventId: Long): Boolean {
        val event = eventDao.getById(eventId) ?: return false
        if (!event.irzStatus.allowsRemoval) return false
        eventDao.deleteById(eventId)
        syncManager.enqueueEventDelete(eventId)
        return true
    }

    /** Zleca zgłoszenie zdarzenia do IRZplus; wywoływane wyłącznie po potwierdzeniu użytkownika. */
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
