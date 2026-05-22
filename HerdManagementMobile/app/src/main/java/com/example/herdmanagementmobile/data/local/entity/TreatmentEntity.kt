package com.example.herdmanagementmobile.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "treatments",
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
data class TreatmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val animalId: Long,
    val drug: String,
    val dose: String?,
    val vet: String?,
    val treatmentDate: LocalDate,
    val withdrawalUntil: LocalDate?,
    val notes: String?,
)
