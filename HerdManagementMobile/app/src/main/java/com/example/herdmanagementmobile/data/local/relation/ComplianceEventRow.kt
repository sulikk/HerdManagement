package com.example.herdmanagementmobile.data.local.relation

import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import java.time.LocalDate

/**
 * Projekcja zapytania łączącego zdarzenie z danymi zwierzęcia (kolczyk, nazwa).
 * Używana przez widok zgodności 7-dniowej.
 */
data class ComplianceEventRow(
    val eventId: Long,
    val animalId: Long,
    val earTag: String,
    val animalName: String?,
    val type: EventType,
    val eventDate: LocalDate,
    val irzStatus: IrzStatus,
)
