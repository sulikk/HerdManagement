package com.example.herdmanagementmobile.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.herdmanagementmobile.data.local.dao.SyncDao
import com.example.herdmanagementmobile.data.local.entity.SyncQueueEntity
import com.example.herdmanagementmobile.data.remote.dto.toDto
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.Treatment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dodaje operacje do kolejki synchronizacji i planuje uruchomienie [SyncWorker].
 * Repozytoria wołają enqueue* zaraz po zapisie lokalnym (offline-first).
 *
 * Dwa niezależne zadania WorkManagera:
 * - [SyncContract.UNIQUE_WORK_NAME] — natychmiastowa synchronizacja po każdej zmianie,
 * - [SyncContract.RETRY_WORK_NAME] — opóźnione ponowienie wpisów, które się nie powiodły.
 * Dzięki temu nowe zmiany nie czekają w kolejce za zadaniem odczekującym backoff.
 * Oba uruchamiają ten sam [SyncWorker]; [mutex] gwarantuje, że kolejka jest drenowana
 * tylko przez jeden worker naraz.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncDao: SyncDao,
    private val json: Json,
) {
    val mutex = Mutex()

    fun observePendingCount(): Flow<Int> = syncDao.observePendingCount()

    fun observeFailedCount(): Flow<Int> = syncDao.observeFailedCount()

    suspend fun enqueueAnimalCreate(animal: Animal) {
        enqueue(SyncContract.Entity.ANIMAL, SyncContract.Operation.CREATE, json.encodeToString(animal.toDto()))
    }

    suspend fun enqueueAnimalUpdate(animal: Animal) {
        enqueue(SyncContract.Entity.ANIMAL, SyncContract.Operation.UPDATE, json.encodeToString(animal.toDto()))
    }

    suspend fun enqueueAnimalDelete(localId: Long) {
        enqueue(SyncContract.Entity.ANIMAL, SyncContract.Operation.DELETE, json.encodeToString(LocalIdPayload(localId)))
    }

    /** Zapis zdarzenia w backendzie aplikacji (bez zgłaszania do IRZplus). */
    suspend fun enqueueEventCreate(event: AnimalEvent) {
        enqueue(SyncContract.Entity.EVENT, SyncContract.Operation.CREATE, json.encodeToString(event.toDto()))
    }

    /** Zgłoszenie zdarzenia do IRZplus — wołać wyłącznie po jawnym potwierdzeniu użytkownika. */
    suspend fun enqueueIrzSubmit(eventId: Long) {
        enqueue(SyncContract.Entity.EVENT, SyncContract.Operation.IRZ_SUBMIT, json.encodeToString(LocalIdPayload(eventId)))
    }

    suspend fun enqueueIrzResult(eventId: Long) {
        enqueue(SyncContract.Entity.EVENT, SyncContract.Operation.IRZ_RESULT, json.encodeToString(LocalIdPayload(eventId)))
    }

    suspend fun enqueueTreatmentCreate(treatment: Treatment) {
        enqueue(SyncContract.Entity.TREATMENT, SyncContract.Operation.CREATE, json.encodeToString(treatment.toDto()))
    }

    suspend fun enqueueTreatmentDelete(localId: Long) {
        enqueue(SyncContract.Entity.TREATMENT, SyncContract.Operation.DELETE, json.encodeToString(LocalIdPayload(localId)))
    }

    /** Tryb manualnej synchronizacji: ponawia nieudane wpisy i uruchamia sync od razu. */
    suspend fun syncNow() {
        syncDao.resetFailed(LocalDateTime.now().toString())
        scheduleSync()
    }

    private suspend fun enqueue(entity: String, operation: String, payload: String) {
        val now = LocalDateTime.now()
        syncDao.enqueue(
            SyncQueueEntity(
                entity = entity,
                operation = operation,
                payload = payload,
                status = SyncContract.Status.PENDING,
                createdAt = now,
                updatedAt = now,
            ),
        )
        scheduleSync()
    }

    /**
     * Uruchamia synchronizację przy dostępnej sieci. APPEND_OR_REPLACE: jeśli worker
     * właśnie pracuje, nowe uruchomienie wykona się zaraz po nim (bez utraty zmian).
     */
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncContract.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    /**
     * Cykliczne odpytywanie backendu o zmiany (co [SyncContract.PERIODIC_MINUTES] min).
     *
     * Same zmiany lokalne nie wystarczą do uruchomienia synchronizacji: wpis dodany
     * przez asystenta LLM albo poprawka zrobiona w backendzie powstają poza telefonem
     * i nic by ich nie zaciągnęło. KEEP — ponowne wywołanie nie resetuje odliczania.
     */
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            SyncContract.PERIODIC_MINUTES, TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraint())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncContract.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Planuje ponowienie za [delay]; kolejne wywołanie zastępuje poprzednie planowanie. */
    fun scheduleRetry(delay: Duration) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint())
            .setInitialDelay(delay)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncContract.RETRY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun networkConstraint() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
