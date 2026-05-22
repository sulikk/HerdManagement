package com.example.herdmanagementmobile.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "listings",
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
data class ListingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val animalId: Long,
    val price: Double,
    val description: String?,
    val irzVerified: Boolean,
    val createdAt: LocalDateTime,
)
