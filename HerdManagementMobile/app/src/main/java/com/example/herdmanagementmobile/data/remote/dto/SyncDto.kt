package com.example.herdmanagementmobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Odpowiedź `GET /sync/changes` zawierająca zmiany od ostatniej synchronizacji.
 *
 * Wartość [serverTime] jest traktowana jako nieprzezroczysty znacznik przekazywany
 * w kolejnym zapytaniu jako `since`, co uniezależnia synchronizację od zegara urządzenia.
 */
@Serializable
data class SyncChangesDto(
    @SerialName("server_time") val serverTime: String,
    val since: String? = null,
    val animals: List<AnimalDto> = emptyList(),
    val events: List<EventDto> = emptyList(),
    val treatments: List<TreatmentDto> = emptyList(),
    val listings: List<ListingDto> = emptyList(),
    val inquiries: List<InquiryDto> = emptyList(),
    val deletions: List<DeletionDto> = emptyList(),
) {
    val isEmpty: Boolean
        get() = animals.isEmpty() && events.isEmpty() && treatments.isEmpty() &&
            listings.isEmpty() && inquiries.isEmpty() && deletions.isEmpty()
}

/** Znacznik usunięcia rekordu po stronie serwera. */
@Serializable
data class DeletionDto(
    val entity: String,
    @SerialName("entity_id") val entityId: Long,
    @SerialName("deleted_at") val deletedAt: String,
)
