package com.example.herdmanagementmobile.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.herdmanagementmobile.data.local.dao.SyncDao
import com.example.herdmanagementmobile.data.local.entity.SyncQueueEntity
import com.example.herdmanagementmobile.data.remote.dto.toDto
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.Treatment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dodaje operacje do kolejki synchronizacji i planuje uruchomienie [SyncWorker].
 * Repozytoria wołają enqueue* zaraz po zapisie lokalnym (offline-first).
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncDao: SyncDao,
    private val json: Json,
) {
    fun observePendingCount(): Flow<Int> = syncDao.observePendingCount()

    suspend fun enqueueAnimalCreate(animal: Animal) {
        enqueue(SyncContract.Entity.ANIMAL, json.encodeToString(animal.toDto()))
    }

    suspend fun enqueueEventCreate(event: AnimalEvent) {
        enqueue(SyncContract.Entity.EVENT, json.encodeToString(event.toDto()))
    }

    suspend fun enqueueTreatmentCreate(treatment: Treatment) {
        enqueue(SyncContract.Entity.TREATMENT, json.encodeToString(treatment.toDto()))
    }

    private suspend fun enqueue(entity: String, payload: String) {
        val now = LocalDateTime.now()
        syncDao.enqueue(
            SyncQueueEntity(
                entity = entity,
                operation = SyncContract.Operation.CREATE,
                payload = payload,
                status = SyncContract.Status.PENDING,
                createdAt = now,
                updatedAt = now,
            ),
        )
        scheduleSync()
    }

    /** Uruchamia jednorazowe zadanie synchronizacji przy dostępnej sieci. */
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncContract.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
