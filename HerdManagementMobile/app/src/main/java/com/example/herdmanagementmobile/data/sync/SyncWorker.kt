package com.example.herdmanagementmobile.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.dao.SyncDao
import com.example.herdmanagementmobile.data.local.entity.IdMappingEntity
import com.example.herdmanagementmobile.data.local.entity.SyncQueueEntity
import com.example.herdmanagementmobile.data.remote.HerdApiService
import com.example.herdmanagementmobile.data.remote.dto.AnimalDto
import com.example.herdmanagementmobile.data.remote.dto.EventDto
import com.example.herdmanagementmobile.data.remote.dto.TreatmentDto
import com.example.herdmanagementmobile.domain.model.IrzStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.LocalDateTime

/**
 * Drenuje kolejkę synchronizacji (FIFO) i wysyła operacje do backendu.
 * Mapuje lokalne id zwierząt na zdalne, aby rekordy zależne (zdarzenia, leczenia)
 * trafiały pod właściwe zwierzę. Wpisy bez spełnionej zależności zostają PENDING
 * i powodują ponowienie zadania.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncDao: SyncDao,
    private val eventDao: EventDao,
    private val api: HerdApiService,
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

    private suspend fun process(entry: SyncQueueEntity): Outcome = when (entry.entity) {
        SyncContract.Entity.ANIMAL -> pushAnimal(entry)
        SyncContract.Entity.EVENT -> pushEvent(entry)
        SyncContract.Entity.TREATMENT -> pushTreatment(entry)
        else -> Outcome.FAILED
    }

    private suspend fun pushAnimal(entry: SyncQueueEntity): Outcome {
        val dto = json.decodeFromString<AnimalDto>(entry.payload)
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
        return Outcome.DONE
    }

    private suspend fun pushEvent(entry: SyncQueueEntity): Outcome {
        val dto = json.decodeFromString<EventDto>(entry.payload)
        val remoteAnimalId = syncDao.remoteIdFor(SyncContract.Entity.ANIMAL, dto.animalId)
            ?: return Outcome.RETRY // zwierzę jeszcze niezsynchronizowane — spróbuj później
        val created = api.createEvent(remoteAnimalId, dto.copy(animalId = remoteAnimalId))
        syncDao.putMapping(IdMappingEntity(SyncContract.Entity.EVENT, dto.id, created.id))
        eventDao.updateIrzStatus(dto.id, IrzStatus.SUBMITTED.name)
        return Outcome.DONE
    }

    private suspend fun pushTreatment(entry: SyncQueueEntity): Outcome {
        val dto = json.decodeFromString<TreatmentDto>(entry.payload)
        val remoteAnimalId = syncDao.remoteIdFor(SyncContract.Entity.ANIMAL, dto.animalId)
            ?: return Outcome.RETRY
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
