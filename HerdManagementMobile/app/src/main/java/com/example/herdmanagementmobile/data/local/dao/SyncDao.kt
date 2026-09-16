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

    @Query("DELETE FROM id_mapping WHERE entity = :entity AND localId = :localId")
    suspend fun deleteMapping(entity: String, localId: Long)
}
