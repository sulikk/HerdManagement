package com.example.herdmanagementmobile.data.remote.dto

import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class EventDto(
    val id: Long = 0L,
    @SerialName("animal_id") val animalId: Long,
    val type: String,
    @SerialName("event_date") val eventDate: String,
    val notes: String? = null,
    @SerialName("partner_holding_number") val partnerHoldingNumber: String? = null,
    @SerialName("irz_status") val irzStatus: String = "pending",
    @SerialName("irz_submitted_at") val irzSubmittedAt: String? = null,
    @SerialName("irz_message") val irzMessage: String? = null,
    @SerialName("irz_document_number") val irzDocumentNumber: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Body dla PATCH /events/{id}/irz. */
@Serializable
data class IrzResultDto(
    @SerialName("irz_status") val irzStatus: String,
    @SerialName("irz_submitted_at") val irzSubmittedAt: String? = null,
    @SerialName("irz_message") val irzMessage: String? = null,
    @SerialName("irz_document_number") val irzDocumentNumber: String? = null,
)

fun EventDto.toDomain(): AnimalEvent = AnimalEvent(
    id = id,
    animalId = animalId,
    type = EventType.valueOf(type.uppercase()),
    eventDate = LocalDate.parse(eventDate),
    notes = notes,
    partnerHoldingNumber = partnerHoldingNumber,
    irzStatus = IrzStatus.valueOf(irzStatus.uppercase()),
    irzSubmittedAt = irzSubmittedAt.utcToLocal(),
    irzMessage = irzMessage,
    irzDocumentNumber = irzDocumentNumber,
)

fun AnimalEvent.toDto(): EventDto = EventDto(
    id = id,
    animalId = animalId,
    type = type.name.lowercase(),
    eventDate = eventDate.toString(),
    notes = notes,
    partnerHoldingNumber = partnerHoldingNumber,
    irzStatus = irzStatus.name.lowercase(),
    irzSubmittedAt = irzSubmittedAt?.localToUtc(),
    irzMessage = irzMessage,
    irzDocumentNumber = irzDocumentNumber,
)

fun AnimalEvent.toIrzResultDto(): IrzResultDto = IrzResultDto(
    irzStatus = irzStatus.name.lowercase(),
    irzSubmittedAt = irzSubmittedAt?.localToUtc(),
    irzMessage = irzMessage,
    irzDocumentNumber = irzDocumentNumber,
)
