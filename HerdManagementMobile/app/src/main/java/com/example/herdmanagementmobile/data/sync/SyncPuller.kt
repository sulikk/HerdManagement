package com.example.herdmanagementmobile.data.sync

import com.example.herdmanagementmobile.data.local.dao.AnimalDao
import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.dao.SyncDao
import com.example.herdmanagementmobile.data.local.dao.TreatmentDao
import com.example.herdmanagementmobile.data.local.entity.AnimalEntity
import com.example.herdmanagementmobile.data.local.entity.EventEntity
import com.example.herdmanagementmobile.data.local.entity.IdMappingEntity
import com.example.herdmanagementmobile.data.local.entity.TreatmentEntity
import com.example.herdmanagementmobile.data.remote.HerdApiService
import com.example.herdmanagementmobile.data.remote.dto.AnimalDto
import com.example.herdmanagementmobile.data.remote.dto.DeletionDto
import com.example.herdmanagementmobile.data.remote.dto.EventDto
import com.example.herdmanagementmobile.data.remote.dto.TreatmentDto
import com.example.herdmanagementmobile.data.remote.dto.toDomain
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pobiera z backendu zmiany, których telefon sam nie wprowadził, i wgrywa je do Room.
 *
 * Bez tego kroku synchronizacja jest jednokierunkowa: aplikacja wypycha swoje wpisy,
 * ale nie widzi niczego, co powstało po drugiej stronie — a po drugiej stronie zapisuje
 * choćby asystent LLM (narzędzia `dodaj_zwierze`, `dodaj_zdarzenie`, `dodaj_leczenie`
 * działają na bazie backendu). Hodowca podejmuje decyzje na podstawie ekranu telefonu,
 * więc rozjazd między telefonem a serwerem jest błędem, nie niedogodnością.
 *
 * **Polityka konfliktów.** Pobieranie rusza dopiero, gdy kolejka wysyłkowa jest pusta.
 * Dopóki cokolwiek czeka na wysłanie, lokalna wersja jest nowsza i to ona wygrywa;
 * gdy kolejka się opróżni, wersja z serwera nadpisuje lokalną. Dzięki temu nigdy nie
 * kasujemy zmiany, której użytkownik nie zdążył wypchnąć, a stan i tak się zbiega.
 */
@Singleton
class SyncPuller @Inject constructor(
    private val api: HerdApiService,
    private val syncDao: SyncDao,
    private val animalDao: AnimalDao,
    private val eventDao: EventDao,
    private val treatmentDao: TreatmentDao,
    private val syncState: SyncMarkerStore,
) {

    sealed interface Result {
        /** Kolejka wysyłkowa nie jest pusta — pobieranie odłożone do następnego razu. */
        data object Skipped : Result

        data class Applied(
            val animals: Int,
            val events: Int,
            val treatments: Int,
            val deletions: Int,
            val deferred: Int,
        ) : Result {
            val total: Int get() = animals + events + treatments + deletions
        }
    }

    suspend fun pull(): Result {
        if (syncDao.unsentCount() > 0) return Result.Skipped

        val since = syncState.lastPullMarker()
        val changes = api.getChanges(since)
        if (changes.isEmpty) {
            syncState.saveLastPullMarker(changes.serverTime)
            return Result.Applied(0, 0, 0, 0, 0)
        }

        var deferred = 0
        // Kolejność ma znaczenie: zdarzenie i leczenie wskazują zwierzę, więc
        // zwierzęta muszą trafić do bazy (i do mapy identyfikatorów) pierwsze.
        changes.animals.forEach { applyAnimal(it) }
        changes.events.forEach { if (!applyEvent(it)) deferred++ }
        changes.treatments.forEach { if (!applyTreatment(it)) deferred++ }
        changes.deletions.forEach { applyDeletion(it) }

        // Gdy czegoś nie dało się wpiąć (brak zwierzęcia-rodzica), nie przesuwamy
        // znacznika — następne pobranie przyniesie tę porcję jeszcze raz. Zapis jest
        // idempotentny, więc powtórka niczego nie psuje.
        if (deferred == 0) {
            syncState.saveLastPullMarker(changes.serverTime)
        }

        return Result.Applied(
            animals = changes.animals.size,
            events = changes.events.size,
            treatments = changes.treatments.size,
            deletions = changes.deletions.size,
            deferred = deferred,
        )
    }

    // --- Zwierzęta ------------------------------------------------------

    private suspend fun applyAnimal(dto: AnimalDto) {
        val domain = dto.toDomain()
        val mappedId = syncDao.localIdFor(SyncContract.Entity.ANIMAL, dto.id)
        val existing = mappedId?.let { animalDao.getById(it) }
            // Zwierzę mogło powstać lokalnie i stracić mapowanie (np. po
            // reinstalacji). Kolczyk jest unikalny, więc można je rozpoznać po nim
            // — bez tego insert wywaliłby się na UNIQUE.
            ?: animalDao.getByEarTag(dto.earTag)

        if (existing != null) {
            animalDao.update(AnimalEntity.fromDomain(domain).copy(id = existing.id))
            rememberMapping(SyncContract.Entity.ANIMAL, existing.id, dto.id)
        } else {
            val localId = animalDao.insert(AnimalEntity.fromDomain(domain).copy(id = 0L))
            rememberMapping(SyncContract.Entity.ANIMAL, localId, dto.id)
        }
    }

    // --- Zdarzenia ------------------------------------------------------

    /** `false`, gdy nie ma jeszcze lokalnego zwierzęcia, do którego należy zdarzenie. */
    private suspend fun applyEvent(dto: EventDto): Boolean {
        val animalLocalId = syncDao.localIdFor(SyncContract.Entity.ANIMAL, dto.animalId)
            ?: return false

        val domain = dto.toDomain()
        val localId = syncDao.localIdFor(SyncContract.Entity.EVENT, dto.id)
        val entity = EventEntity(
            id = localId ?: 0L,
            animalId = animalLocalId,
            type = domain.type,
            eventDate = domain.eventDate,
            notes = domain.notes,
            partnerHoldingNumber = domain.partnerHoldingNumber,
            irzStatus = domain.irzStatus,
            irzSubmittedAt = domain.irzSubmittedAt,
            irzMessage = domain.irzMessage,
            irzDocumentNumber = domain.irzDocumentNumber,
        )
        // EventDao.insert działa w trybie REPLACE, więc z ustawionym id jest upsertem.
        val newId = eventDao.insert(entity)
        rememberMapping(SyncContract.Entity.EVENT, localId ?: newId, dto.id)
        return true
    }

    // --- Leczenia -------------------------------------------------------

    private suspend fun applyTreatment(dto: TreatmentDto): Boolean {
        val animalLocalId = syncDao.localIdFor(SyncContract.Entity.ANIMAL, dto.animalId)
            ?: return false

        val localId = syncDao.localIdFor(SyncContract.Entity.TREATMENT, dto.id)
        val entity = TreatmentEntity.fromDomain(dto.toDomain()).copy(
            id = localId ?: 0L,
            animalId = animalLocalId,
        )
        val newId = treatmentDao.insert(entity)
        rememberMapping(SyncContract.Entity.TREATMENT, localId ?: newId, dto.id)
        return true
    }

    // --- Usunięcia ------------------------------------------------------

    private suspend fun applyDeletion(dto: DeletionDto) {
        val localId = syncDao.localIdFor(dto.entity, dto.entityId) ?: return
        when (dto.entity) {
            // Kasowanie zwierzęcia pociąga w Room kaskadę na zdarzenia i leczenia.
            SyncContract.Entity.ANIMAL -> animalDao.deleteById(localId)
            SyncContract.Entity.EVENT -> eventDao.deleteById(localId)
            SyncContract.Entity.TREATMENT -> treatmentDao.deleteById(localId)
            else -> return
        }
        syncDao.deleteMapping(dto.entity, localId)
    }

    private suspend fun rememberMapping(entity: String, localId: Long, remoteId: Long) {
        syncDao.putMapping(IdMappingEntity(entity = entity, localId = localId, remoteId = remoteId))
    }
}
