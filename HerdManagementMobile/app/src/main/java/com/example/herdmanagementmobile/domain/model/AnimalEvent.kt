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
    /** Numer siedziby partnera: skąd przybyło (przybycie) / dokąd ubyło (ubycie). */
    val partnerHoldingNumber: String? = null,
    val irzStatus: IrzStatus = IrzStatus.PENDING,
    val irzSubmittedAt: LocalDateTime? = null,
    val irzMessage: String? = null,
    val irzDocumentNumber: String? = null,
)

/**
 * Zdarzenia opisujące rzeczywisty stan stada — bez odrzuconych przez IRZplus.
 *
 * Odrzucone zgłoszenie to próba, nie fakt: rejestr go nie przyjął, więc nic się nie
 * stało. Hodowca może dwa razy zgłosić urodzenie tego samego byczka — drugie odbije
 * się od rejestru, bo sztuka już tam jest. Liczone do statystyk dawało dwa urodzenia
 * i dwie sztuki przybyłe z jednej; przy ubyciu było gorzej, bo odrzucone zgłoszenie
 * wyprowadzało żywą sztukę ze stanu stada.
 *
 * Zdarzenia niezgłoszone ([IrzStatus.PENDING]) i wysłane ([IrzStatus.SUBMITTED])
 * zostają: cielę urodzone wczoraj jest w stadzie niezależnie od tego, czy hodowca
 * zdążył wypełnić zgłoszenie — na to właśnie ma ustawowe siedem dni.
 */
fun List<AnimalEvent>.withoutRejected(): List<AnimalEvent> =
    filter { it.irzStatus != IrzStatus.REJECTED }

/**
 * Czy zgłoszenie wolno usunąć z ewidencji bez rozjeżdżania się z rejestrem.
 *
 * [IrzStatus.PENDING] nigdy nie poszło, a [IrzStatus.REJECTED] rejestr odrzucił — w obu
 * przypadkach w IRZplus nic nie powstało, więc skasowanie wpisu w telefonie niczego nie
 * zafałszuje. To właśnie te dwa stany potrafią uraść w stertę pozycji „do poprawy”,
 * których nie da się już naprawić — na przykład zgłoszenia urodzenia sztuki, która
 * jest w rejestrze od dawna.
 *
 * [IrzStatus.SUBMITTED] czeka na odpowiedź rejestru, a [IrzStatus.CONFIRMED] jest w nim
 * zapisane — tam wpis w aplikacji to jedyny ślad tego, co hodowca naprawdę zgłosił,
 * i musi zostać. Usunięcie potwierdzonego zgłoszenia kazałoby aplikacji kłamać
 * o stanie rejestru.
 */
val IrzStatus.allowsRemoval: Boolean
    get() = this == IrzStatus.PENDING || this == IrzStatus.REJECTED
