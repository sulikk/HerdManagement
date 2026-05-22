package com.example.herdmanagementmobile.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.herdmanagementmobile.domain.model.MeasurementType
import java.time.LocalDate

@Entity(
    tableName = "measurements",
    foreignKeys = [
        ForeignKey(
            entity = AnimalEntity::class,
            parentColumns = ["id"],
            childColumns = ["animalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("animalId")],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val animalId: Long,
    val type: MeasurementType,
    val value: Double,
    val measurementDate: LocalDate,
    val photoUri: String?,
)
