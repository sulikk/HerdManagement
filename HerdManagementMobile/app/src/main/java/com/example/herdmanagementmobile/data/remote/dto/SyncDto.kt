package com.example.herdmanagementmobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Odpowiedź `GET /sync/changes` — porcja zmian z backendu od ostatniej synchronizacji.
 *
 * `serverTime` traktujemy jak nieprzezroczysty znacznik: zapisujemy go u siebie i
 * oddajemy nietknięty w kolejnym zapytaniu jako `since`. Dzięki temu rozjechany
 * zegar telefonu nie gubi zmian ani nie ściąga w kółko tych samych rekordów.
 */
@Serializable
data class SyncChangesDto(
    @SerialName("server_time") val serverTime: String,
    val since: String? = null,
    val animals: List<AnimalDto> = emptyList(),
    val events: List<EventDto> = emptyList(),
    val treatments: List<TreatmentDto> = emptyList(),
    val deletions: List<DeletionDto> = emptyList(),
) {
    val isEmpty: Boolean
        get() = animals.isEmpty() && events.isEmpty() &&
            treatments.isEmpty() && deletions.isEmpty()
}

/** Rekord usunięty na serwerze — trzeba go skasować także lokalnie. */
@Serializable
data class DeletionDto(
    val entity: String,
    @SerialName("entity_id") val entityId: Long,
    @SerialName("deleted_at") val deletedAt: String,
)
