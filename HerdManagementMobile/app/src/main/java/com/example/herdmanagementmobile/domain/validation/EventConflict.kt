package com.example.herdmanagementmobile.domain.validation

import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.withoutRejected
import java.time.LocalDate

/**
 * Sprzeczność nowego zdarzenia z dotychczasową historią zwierzęcia.
 *
 * @property conflictingDate data istniejącego zdarzenia, z którym występuje sprzeczność;
 *   prezentowana użytkownikowi w treści ostrzeżenia.
 */
data class EventConflict(
    val kind: Kind,
    val conflictingDate: LocalDate,
) {
    /** Określa, czy sprzeczność uniemożliwia zapis (zob. [Kind.blocking]). */
    val blocking: Boolean get() = kind.blocking

    /**
     * Rodzaj sprzeczności wraz z jej wagą.
     *
     * Blokujące są wyłącznie sprzeczności bezwarunkowe: urodzenie i padnięcie występują
     * jednokrotnie w życiu zwierzęcia. Pozostałe rodzaje wynikają z wnioskowania na
     * podstawie ewidencji, która może być niepełna, a zdarzenia potwierdzone w rejestrze
     * nie mogą zostać usunięte
     * ([com.example.herdmanagementmobile.domain.model.allowsRemoval]). W tych przypadkach
     * zgłaszane jest ostrzeżenie, a decyzja o zapisie należy do użytkownika.
     */
    enum class Kind(val blocking: Boolean) {
        /** Zwierzę ma już zarejestrowane urodzenie. */
        ALREADY_BORN(blocking = true),

        /** Zwierzę przybyło z innej siedziby stada, więc nie urodziło się w gospodarstwie. */
        ALREADY_ARRIVED(blocking = false),

        /** Zwierzę ma już zarejestrowane padnięcie. */
        ALREADY_DEAD(blocking = true),

        /** Zwierzę zostało już rozliczone jako ubyłe lub padłe. */
        ALREADY_LEFT(blocking = false),

        /** Zwierzę znajduje się w gospodarstwie, więc nie może do niego przybyć. */
        ALREADY_IN_HERD(blocking = false),
    }
}

/**
 * Wykrywa zdarzenia sprzeczne z dotychczasową historią zwierzęcia.
 *
 * Zdarzenia dzielą się na wejścia (urodzenie, przybycie) i wyjścia (ubycie, padnięcie),
 * które muszą występować naprzemiennie. Sekwencja `przybycie → ubycie → przybycie` jest
 * poprawna, natomiast urodzenie i padnięcie mogą wystąpić tylko raz, a urodzenie musi
 * być pierwszym zdarzeniem w historii.
 *
 * Zgłoszenia odrzucone przez IRZplus są pomijane ([withoutRejected]), ponieważ nie
 * zmieniły stanu rejestru i nie mogą blokować zapisu poprawionego zdarzenia.
 */
object EventConflicts {

    /** Zdarzenia wprowadzające zwierzę do gospodarstwa; pozostałe typy są wyjściami. */
    private val ENTRY = setOf(EventType.BIRTH, EventType.ARRIVAL)

    fun detect(type: EventType, existing: List<AnimalEvent>): EventConflict? {
        val actual = existing.withoutRejected()
        if (actual.isEmpty()) return null

        // Porządek zgodny z HerdStatistics: data zdarzenia, a przy równych datach identyfikator.
        val last = actual.maxWith(compareBy({ it.eventDate }, { it.id }))
        val inHerd = last.type in ENTRY

        return when (type) {
            // Urodzenie musi być pierwszym zdarzeniem; rodzaj sprzeczności określa treść komunikatu.
            EventType.BIRTH -> {
                val born = actual.lastOfType(EventType.BIRTH)
                when {
                    born != null -> conflict(EventConflict.Kind.ALREADY_BORN, born)
                    inHerd -> conflict(EventConflict.Kind.ALREADY_ARRIVED, last)
                    else -> conflict(EventConflict.Kind.ALREADY_LEFT, last)
                }
            }

            // Przybycie po ubyciu jest dopuszczalne (powrót zwierzęcia).
            EventType.ARRIVAL ->
                if (inHerd) conflict(EventConflict.Kind.ALREADY_IN_HERD, last) else null

            EventType.DEPARTURE ->
                if (inHerd) null else conflict(EventConflict.Kind.ALREADY_LEFT, last)

            EventType.DEATH -> {
                val dead = actual.lastOfType(EventType.DEATH)
                when {
                    dead != null -> conflict(EventConflict.Kind.ALREADY_DEAD, dead)
                    // Padnięcie zwierzęcia po ubyciu zgłasza jego nowy posiadacz.
                    !inHerd -> conflict(EventConflict.Kind.ALREADY_LEFT, last)
                    else -> null
                }
            }
        }
    }

    private fun conflict(kind: EventConflict.Kind, event: AnimalEvent) =
        EventConflict(kind, event.eventDate)

    private fun List<AnimalEvent>.lastOfType(type: EventType): AnimalEvent? =
        filter { it.type == type }.maxWithOrNull(compareBy({ it.eventDate }, { it.id }))
}
