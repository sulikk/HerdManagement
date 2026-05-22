package com.example.herdmanagementmobile.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class EventType { BIRTH, ARRIVAL, DEPARTURE, DEATH }

enum class IrzStatus { PENDING, SUBMITTED, CONFIRMED, REJECTED }

data class AnimalEvent(
    val id: Long = 0L,
    val animalId: Long,
    val type: EventType,
    val eventDate: LocalDate,
    val notes: String? = null,
    val irzStatus: IrzStatus = IrzStatus.PENDING,
    val irzSubmittedAt: LocalDateTime? = null,
)
