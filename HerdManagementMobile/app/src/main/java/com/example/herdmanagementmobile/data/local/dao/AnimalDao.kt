package com.example.herdmanagementmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.herdmanagementmobile.data.local.entity.AnimalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimalDao {
    @Query("SELECT * FROM animals ORDER BY earTag")
    fun observeAll(): Flow<List<AnimalEntity>>

    @Query("SELECT * FROM animals WHERE id = :id")
    fun observeById(id: Long): Flow<AnimalEntity?>

    @Query("SELECT * FROM animals WHERE id = :id")
    suspend fun getById(id: Long): AnimalEntity?

    @Query("SELECT * FROM animals WHERE earTag = :earTag LIMIT 1")
    suspend fun getByEarTag(earTag: String): AnimalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(animal: AnimalEntity): Long

    @Update
    suspend fun update(animal: AnimalEntity)

    @Query("DELETE FROM animals WHERE id = :id")
    suspend fun deleteById(id: Long)
}
