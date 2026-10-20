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
    /** Numer siedziby stada kontrahenta: pochodzenia (przybycie) lub przeznaczenia (ubycie). */
    val partnerHoldingNumber: String? = null,
    val irzStatus: IrzStatus = IrzStatus.PENDING,
    val irzSubmittedAt: LocalDateTime? = null,
    val irzMessage: String? = null,
    val irzDocumentNumber: String? = null,
)

/**
 * Zwraca zdarzenia opisujące rzeczywisty stan stada, z pominięciem zgłoszeń odrzuconych
 * przez IRZplus, które nie zmieniły stanu rejestru.
 *
 * Zdarzenia niezgłoszone ([IrzStatus.PENDING]) i wysłane ([IrzStatus.SUBMITTED]) są
 * uwzględniane, ponieważ odzwierciedlają faktyczny stan stada w okresie na dokonanie
 * zgłoszenia.
 */
fun List<AnimalEvent>.withoutRejected(): List<AnimalEvent> =
    filter { it.irzStatus != IrzStatus.REJECTED }

/**
 * Określa, czy zdarzenie można usunąć z ewidencji bez utraty zgodności z rejestrem.
 *
 * Usunięcie jest dozwolone dla stanów [IrzStatus.PENDING] i [IrzStatus.REJECTED],
 * w których zgłoszenie nie zostało zapisane w IRZplus. Zdarzenia w stanach
 * [IrzStatus.SUBMITTED] i [IrzStatus.CONFIRMED] muszą zostać zachowane jako zapis
 * dokonanego zgłoszenia.
 */
val IrzStatus.allowsRemoval: Boolean
    get() = this == IrzStatus.PENDING || this == IrzStatus.REJECTED
