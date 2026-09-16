package com.example.herdmanagementmobile.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.herdmanagementmobile.data.local.dao.AnimalDao
import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.dao.SyncDao
import com.example.herdmanagementmobile.data.local.entity.IdMappingEntity
import com.example.herdmanagementmobile.data.local.entity.SyncQueueEntity
import com.example.herdmanagementmobile.data.remote.HerdApiService
import com.example.herdmanagementmobile.data.remote.dto.AnimalDto
import com.example.herdmanagementmobile.data.remote.dto.EventDto
import com.example.herdmanagementmobile.data.remote.dto.TreatmentDto
import com.example.herdmanagementmobile.data.remote.dto.toDomain
import com.example.herdmanagementmobile.data.remote.irzplus.IrzplusClient
import com.example.herdmanagementmobile.domain.model.IrzStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.LocalDateTime

/**
 * Drenuje kolejkę synchronizacji (FIFO):
 * - zwierzęta i leczenia trafiają do backendu aplikacji (z mapowaniem lokalne id -> zdalne);
 *   zwierzęta: tworzenie, edycja, usunięcie; leczenia: tworzenie, usunięcie,
 * - zdarzenia są zgłaszane do Portalu IRZplus, a ich [IrzStatus] jest aktualizowany
 *   na CONFIRMED (przyjęto, nadano numer dokumentu) lub REJECTED (błędy walidacji).
 * Wpisy z zależnością jeszcze niespełnioną zostają PENDING i powodują ponowienie.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncDao: SyncDao,
    private val eventDao: EventDao,
    private val animalDao: AnimalDao,
    private val api: HerdApiService,
    private val irzplus: IrzplusClient,
    private val json: Json,
) : CoroutineWorker(context, params) {

    /** Wynik przetworzenia jednego wpisu kolejki. */
    private enum class Outcome { DONE, RETRY, FAILED }

    override suspend fun doWork(): Result {
        var anyRetry = false

        for (entry in syncDao.pending()) {
            val outcome = runCatching { process(entry) }.getOrElse { error ->
                markRetryOrFailed(entry, error.message)
                if (entry.retries + 1 >= SyncContract.MAX_RETRIES) Outcome.FAILED else Outcome.RETRY
            }
            when (outcome) {
                Outcome.DONE -> markDone(entry)
                Outcome.RETRY -> anyRetry = true
                Outcome.FAILED -> { /* już oznaczone jako FAILED w markRetryOrFailed */ }
            }
        }

        return if (anyRetry) Result.retry() else Result.success()
    }

    private suspend fun process(entry: SyncQueueEntity): Outcome = when (entry.entity to entry.operation) {
        SyncContract.Entity.ANIMAL to SyncContract.Operation.CREATE -> pushAnimal(entry)
        SyncContract.Entity.ANIMAL to SyncContract.Operation.UPDATE -> updateAnimal(entry)
        SyncContract.Entity.ANIMAL to SyncContract.Operation.DELETE ->
            deleteRemote(entry, SyncContract.Entity.ANIMAL) { api.deleteAnimal(it) }
        SyncContract.Entity.EVENT to SyncContract.Operation.CREATE -> pushEvent(entry)
        SyncContract.Entity.TREATMENT to SyncContract.Operation.CREATE -> pushTreatment(entry)
        SyncContract.Entity.TREATMENT to SyncContract.Operation.DELETE ->
            deleteRemote(entry, SyncContract.Entity.TREATMENT) { api.deleteTreatment(it) }
        else -> Outcome.FAILED
    }

    private suspend fun pushAnimal(entry: SyncQueueEntity): Outcome {
        val dto = json.decodeFromString<AnimalDto>(entry.payload)
        createAnimalRemote(dto)
        return Outcome.DONE
    }

    private suspend fun createAnimalRemote(dto: AnimalDto) {
        val remote = try {
            api.createAnimal(dto)
        } catch (e: HttpException) {
            // 409 = zwierzę o tym kolczyku już istnieje na serwerze — odczytaj jego zdalne id.
            if (e.code() == 409) {
                api.listAnimals().firstOrNull { it.earTag == dto.earTag }
                    ?: throw e
            } else {
                throw e
            }
        }
        syncDao.putMapping(IdMappingEntity(SyncContract.Entity.ANIMAL, dto.id, remote.id))
    }

    private suspend fun updateAnimal(entry: SyncQueueEntity): Outcome {
        val dto = json.decodeFromString<AnimalDto>(entry.payload)
        val remoteId = syncDao.remoteIdFor(SyncContract.Entity.ANIMAL, dto.id)
            ?: return if (animalDao.getById(dto.id) == null) Outcome.DONE else Outcome.RETRY
        try {
            api.updateAnimal(remoteId, dto.copy(id = remoteId))
        } catch (e: HttpException) {
            // Serwer nie zna już tego zwierzęcia (np. wyczyszczona baza) — załóż je ponownie.
            if (e.code() != 404) throw e
            createAnimalRemote(dto)
        }
        return Outcome.DONE
    }

    /**
     * Usunięcie po stronie serwera. Brak mapowania = obiekt nigdy nie dotarł na serwer
     * (wcześniejszy CREATE przetwarzany jest przed nami, FIFO), więc nie ma czego usuwać.
     * 404 traktujemy jako sukces — stan docelowy jest osiągnięty.
     */
    private suspend fun deleteRemote(
        entry: SyncQueueEntity,
        entity: String,
        call: suspend (Long) -> Unit,
    ): Outcome {
        val payload = json.decodeFromString<DeletePayload>(entry.payload)
        val remoteId = syncDao.remoteIdFor(entity, payload.localId) ?: return Outcome.DONE
        try {
            call(remoteId)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
        }
        syncDao.deleteMapping(entity, payload.localId)
        return Outcome.DONE
    }

    private suspend fun pushEvent(entry: SyncQueueEntity): Outcome {
        val dto = json.decodeFromString<EventDto>(entry.payload)
        // Zwierzę usunięte lokalnie (razem z tym zdarzeniem) — nie ma czego zgłaszać.
        val animal = animalDao.getById(dto.animalId)?.toDomain()
            ?: return Outcome.DONE
        val event = dto.toDomain()

        val response = irzplus.zglosZdarzenie(event, animal)
        val accepted = response.numerDokumentu != null && response.bledy.isEmpty()
        val status = if (accepted) IrzStatus.CONFIRMED else IrzStatus.REJECTED
        val message = if (accepted) {
            response.komunikat.ifBlank { "Przyjęto przez IRZplus." }
        } else {
            // Złóż czytelny powód odrzucenia z komunikatu i opisów błędów walidacji.
            val powody = response.bledy.joinToString("; ") { it.opis }
            listOf(response.komunikat, powody).filter { it.isNotBlank() }.joinToString(" ")
                .ifBlank { "Zgłoszenie odrzucone przez IRZplus." }
        }
        eventDao.updateIrzResult(dto.id, status.name, message, response.numerDokumentu)
        return Outcome.DONE // odrzucenie walidacyjne nie naprawi się przez ponawianie
    }

    private suspend fun pushTreatment(entry: SyncQueueEntity): Outcome {
        val dto = json.decodeFromString<TreatmentDto>(entry.payload)
        val remoteAnimalId = syncDao.remoteIdFor(SyncContract.Entity.ANIMAL, dto.animalId)
            ?: return if (animalDao.getById(dto.animalId) == null) Outcome.DONE else Outcome.RETRY
        val created = api.createTreatment(remoteAnimalId, dto.copy(animalId = remoteAnimalId))
        syncDao.putMapping(IdMappingEntity(SyncContract.Entity.TREATMENT, dto.id, created.id))
        return Outcome.DONE
    }

    private suspend fun markDone(entry: SyncQueueEntity) {
        syncDao.updateEntry(
            id = entry.id,
            status = SyncContract.Status.DONE,
            retries = entry.retries,
            error = null,
            updatedAt = LocalDateTime.now().toString(),
        )
    }

    private suspend fun markRetryOrFailed(entry: SyncQueueEntity, error: String?) {
        val retries = entry.retries + 1
        val status = if (retries >= SyncContract.MAX_RETRIES) {
            SyncContract.Status.FAILED
        } else {
            SyncContract.Status.PENDING
        }
        syncDao.updateEntry(
            id = entry.id,
            status = status,
            retries = retries,
            error = error,
            updatedAt = LocalDateTime.now().toString(),
        )
    }
}
