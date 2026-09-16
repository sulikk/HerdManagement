package com.example.herdmanagementmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.herdmanagementmobile.data.local.entity.EventEntity
import com.example.herdmanagementmobile.data.local.relation.ComplianceEventRow
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE animalId = :animalId ORDER BY eventDate DESC")
    fun observeForAnimal(animalId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY eventDate")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE irzStatus = 'PENDING' ORDER BY eventDate")
    fun observePending(): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Query(
        "UPDATE events SET irzStatus = :status, irzMessage = :message, " +
            "irzDocumentNumber = :documentNumber WHERE id = :id",
    )
    suspend fun updateIrzResult(id: Long, status: String, message: String?, documentNumber: String?)

    /** Zdarzenia jeszcze niepotwierdzone przez IRZplus — kandydaci do widoku zgodności. */
    @Query(
        "SELECT e.id AS eventId, e.animalId AS animalId, a.earTag AS earTag, " +
            "a.name AS animalName, e.type AS type, e.eventDate AS eventDate, " +
            "e.irzStatus AS irzStatus, e.irzMessage AS irzMessage " +
            "FROM events e JOIN animals a ON a.id = e.animalId " +
            "WHERE e.irzStatus != 'CONFIRMED' " +
            "ORDER BY e.eventDate",
    )
    fun observeUnconfirmed(): Flow<List<ComplianceEventRow>>
}
