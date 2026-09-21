package com.example.herdmanagementmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.herdmanagementmobile.data.local.entity.IdMappingEntity
import com.example.herdmanagementmobile.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {

    // --- Kolejka synchronizacji -------------------------------------

    @Insert
    suspend fun enqueue(entry: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY id")
    suspend fun pending(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED' AND operation != 'IRZ_SUBMIT'")
    fun observeFailedCount(): Flow<Int>

    /**
     * Ręczne ponowienie nieudanych wpisów. Zgłoszenia do IRZ są pomijane — po porażce
     * wracają do stanu „Nie zgłoszono” i wymagają ponownego potwierdzenia użytkownika.
     */
    @Query(
        "UPDATE sync_queue SET status = 'PENDING', retries = 0, updatedAt = :updatedAt " +
            "WHERE status = 'FAILED' AND operation != 'IRZ_SUBMIT'",
    )
    suspend fun resetFailed(updatedAt: String): Int

    @Query(
        "UPDATE sync_queue SET status = :status, retries = :retries, " +
            "lastError = :error, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateEntry(
        id: Long,
        status: String,
        retries: Int,
        error: String?,
        updatedAt: String,
    )

    // --- Mapa identyfikatorów lokalne -> zdalne ----------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMapping(mapping: IdMappingEntity)

    @Query("SELECT remoteId FROM id_mapping WHERE entity = :entity AND localId = :localId")
    suspend fun remoteIdFor(entity: String, localId: Long): Long?

    /** Kierunek odwrotny — potrzebny przy pobieraniu zmian z backendu. */
    @Query("SELECT localId FROM id_mapping WHERE entity = :entity AND remoteId = :remoteId")
    suspend fun localIdFor(entity: String, remoteId: Long): Long?

    @Query("DELETE FROM id_mapping WHERE entity = :entity AND localId = :localId")
    suspend fun deleteMapping(entity: String, localId: Long)

    /**
     * Ile zmian czeka jeszcze na wysłanie (łącznie z nieudanymi).
     *
     * Pobieranie zmian z serwera ruszą dopiero, gdy to zejdzie do zera — inaczej
     * starsza wersja z backendu nadpisałaby lokalną poprawkę, której nie zdążyliśmy
     * wypchnąć. Zgłoszenia do IRZ pomijamy: po porażce wracają do ręcznej decyzji
     * użytkownika i mogłyby blokować synchronizację w nieskończoność.
     */
    @Query(
        "SELECT COUNT(*) FROM sync_queue " +
            "WHERE status IN ('PENDING', 'FAILED') AND operation != 'IRZ_SUBMIT'",
    )
    suspend fun unsentCount(): Int
}
