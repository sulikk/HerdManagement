package com.example.herdmanagementmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.herdmanagementmobile.data.local.entity.TreatmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TreatmentDao {
    @Query("SELECT * FROM treatments WHERE animalId = :animalId ORDER BY treatmentDate DESC")
    fun observeForAnimal(animalId: Long): Flow<List<TreatmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(treatment: TreatmentEntity): Long

    @Query("DELETE FROM treatments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
