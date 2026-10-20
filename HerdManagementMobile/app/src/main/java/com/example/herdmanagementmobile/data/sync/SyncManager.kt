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
import com.example.herdmanagementmobile.domain.model.Listing
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
 * Repozytoria wywołują metody `enqueue*` bezpośrednio po zapisie lokalnym (offline-first).
 *
 * Wykorzystywane są dwa niezależne zadania WorkManagera:
 * - [SyncContract.UNIQUE_WORK_NAME] — natychmiastowa synchronizacja po każdej zmianie,
 * - [SyncContract.RETRY_WORK_NAME] — opóźnione ponowienie nieudanych wpisów.
 *
 * Oba zadania uruchamiają [SyncWorker]; [mutex] zapewnia, że kolejka jest przetwarzana
 * przez co najwyżej jeden worker jednocześnie.
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

    /** Kolejkuje zapis zdarzenia w backendzie aplikacji, bez zgłaszania go do IRZplus. */
    suspend fun enqueueEventCreate(event: AnimalEvent) {
        enqueue(SyncContract.Entity.EVENT, SyncContract.Operation.CREATE, json.encodeToString(event.toDto()))
    }

    /** Kolejkuje zgłoszenie zdarzenia do IRZplus; wywoływane wyłącznie po potwierdzeniu użytkownika (wymaganie F12). */
    suspend fun enqueueIrzSubmit(eventId: Long) {
        enqueue(SyncContract.Entity.EVENT, SyncContract.Operation.IRZ_SUBMIT, json.encodeToString(LocalIdPayload(eventId)))
    }

    /**
     * Kolejkuje usunięcie zdarzenia. Wpis zawiera wyłącznie identyfikator lokalny;
     * identyfikator zdalny jest ustalany z mapy identyfikatorów w chwili wysyłki.
     */
    suspend fun enqueueEventDelete(localId: Long) {
        enqueue(SyncContract.Entity.EVENT, SyncContract.Operation.DELETE, json.encodeToString(LocalIdPayload(localId)))
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

    // Operacje na ogłoszeniach przenoszą wyłącznie identyfikator lokalny; aktualna treść
    // jest odczytywana z bazy w chwili wysyłki.
    suspend fun enqueueListingCreate(listing: Listing) {
        enqueueListingAction(SyncContract.Operation.CREATE, listing.id)
    }

    suspend fun enqueueListingUpdate(listing: Listing) {
        enqueueListingAction(SyncContract.Operation.UPDATE, listing.id)
    }

    /** Kolejkuje publikację ogłoszenia; wywoływane wyłącznie po potwierdzeniu użytkownika (wymaganie F12). */
    suspend fun enqueueListingPublish(localId: Long) {
        enqueueListingAction(SyncContract.Operation.PUBLISH, localId)
    }

    suspend fun enqueueListingWithdraw(localId: Long) {
        enqueueListingAction(SyncContract.Operation.WITHDRAW, localId)
    }

    suspend fun enqueueListingSold(localId: Long) {
        enqueueListingAction(SyncContract.Operation.MARK_SOLD, localId)
    }

    /** Kolejkuje weryfikację ogłoszenia w rejestrze IRZplus (wymaganie F14). */
    suspend fun enqueueListingVerify(localId: Long) {
        enqueueListingAction(SyncContract.Operation.VERIFY, localId)
    }

    suspend fun enqueueListingDelete(localId: Long) {
        enqueueListingAction(SyncContract.Operation.DELETE, localId)
    }

    private suspend fun enqueueListingAction(operation: String, localId: Long) {
        enqueue(SyncContract.Entity.LISTING, operation, json.encodeToString(LocalIdPayload(localId)))
    }

    /** Ręcznie wyzwala synchronizację, przywracając nieudane wpisy do kolejki. */
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
     * Planuje synchronizację przy dostępnej sieci. Polityka APPEND_OR_REPLACE sprawia,
     * że uruchomienie zlecone w trakcie pracy workera zostanie wykonane po jej zakończeniu.
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
     * Planuje cykliczne pobieranie zmian co [SyncContract.PERIODIC_MINUTES] minut, co
     * pozwala odebrać zmiany wprowadzone poza urządzeniem. Polityka KEEP zapobiega
     * resetowaniu harmonogramu przy ponownym wywołaniu.
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

    /** Planuje ponowienie po czasie [delay]; kolejne wywołanie zastępuje wcześniejszy plan. */
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
