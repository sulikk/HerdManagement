package com.example.herdmanagementmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.herdmanagementmobile.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE animalId = :animalId ORDER BY eventDate DESC")
    fun observeForAnimal(animalId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE irzStatus = 'PENDING' ORDER BY eventDate")
    fun observePending(): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long
}
