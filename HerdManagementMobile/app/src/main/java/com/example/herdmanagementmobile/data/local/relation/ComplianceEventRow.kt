package com.example.herdmanagementmobile.data.local.relation

import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import java.time.LocalDate

/** Projekcja zdarzenia wraz z danymi zwierzęcia, wykorzystywana w widoku zgodności. */
data class ComplianceEventRow(
    val eventId: Long,
    val animalId: Long,
    val earTag: String,
    val animalName: String?,
    val type: EventType,
    val eventDate: LocalDate,
    val irzStatus: IrzStatus,
    val irzMessage: String? = null,
)
