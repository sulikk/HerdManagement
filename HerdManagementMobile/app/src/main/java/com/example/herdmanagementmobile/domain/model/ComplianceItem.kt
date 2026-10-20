package com.example.herdmanagementmobile.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pozycja widoku zgodności: zdarzenie wymagające zgłoszenia do IRZplus wraz
 * z 7-dniowym terminem ustawowym.
 */
data class ComplianceItem(
    val eventId: Long,
    val animalId: Long,
    val earTag: String,
    val animalName: String?,
    val type: EventType,
    val eventDate: LocalDate,
    val irzStatus: IrzStatus,
    val irzMessage: String? = null,
) {
    /** Termin zgłoszenia: data zdarzenia powiększona o 7 dni (ustawa o systemie IRZ). */
    val deadline: LocalDate get() = eventDate.plusDays(REPORTING_DAYS)

    /** Liczba dni pozostałych do terminu względem [today]; wartość ujemna oznacza przekroczenie. */
    fun daysLeft(today: LocalDate): Long = ChronoUnit.DAYS.between(today, deadline)

    fun urgency(today: LocalDate): Urgency {
        val days = daysLeft(today)
        return when {
            days < 0 -> Urgency.OVERDUE
            days <= URGENT_THRESHOLD -> Urgency.URGENT
            else -> Urgency.ON_TIME
        }
    }

    enum class Urgency { OVERDUE, URGENT, ON_TIME }

    companion object {
        const val REPORTING_DAYS = 7L
        const val URGENT_THRESHOLD = 2L
    }
}
