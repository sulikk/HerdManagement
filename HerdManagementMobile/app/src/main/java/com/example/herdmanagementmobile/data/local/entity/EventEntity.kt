package com.example.herdmanagementmobile.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "events",
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
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val animalId: Long,
    val type: EventType,
    val eventDate: LocalDate,
    val notes: String?,
    val partnerHoldingNumber: String? = null,
    val irzStatus: IrzStatus,
    val irzSubmittedAt: LocalDateTime?,
    val irzMessage: String? = null,
    val irzDocumentNumber: String? = null,
) {
    fun toDomain(): AnimalEvent = AnimalEvent(
        id = id,
        animalId = animalId,
        type = type,
        eventDate = eventDate,
        notes = notes,
        partnerHoldingNumber = partnerHoldingNumber,
        irzStatus = irzStatus,
        irzSubmittedAt = irzSubmittedAt,
        irzMessage = irzMessage,
        irzDocumentNumber = irzDocumentNumber,
    )
}
