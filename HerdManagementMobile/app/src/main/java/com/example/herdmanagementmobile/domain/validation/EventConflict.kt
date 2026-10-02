package com.example.herdmanagementmobile.domain.validation

import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.withoutRejected
import java.time.LocalDate

/**
 * Zdarzenie, które kłóci się z historią sztuki.
 *
 * [conflictingDate] to data zdarzenia, które już jest w ewidencji — bez niej
 * ostrzeżenie każe hodowcy szukać po omacku, które z wcześniejszych zdarzeń
 * ma na myśli aplikacja.
 */
data class EventConflict(
    val kind: Kind,
    val conflictingDate: LocalDate,
) {
    /** Czy zapis jest niemożliwy, czy tylko podejrzany — patrz [Kind.blocking]. */
    val blocking: Boolean get() = kind.blocking

    /**
     * Rodzaj sprzeczności wraz z jej wagą.
     *
     * [blocking] jest zarezerwowane dla sprzeczności **bezwarunkowych**: urodzenie
     * i padnięcie zdarzają się raz w życiu sztuki i żadna historia ewidencji tego nie
     * zmieni, więc drugiego takiego zdarzenia nie da się obronić żadnym wyjaśnieniem.
     *
     * Pozostałe rodzaje to **wnioski** z tego, co aplikacja wie: że sztuka przyjechała,
     * że już wyszła, że stoi w gospodarstwie. Ewidencja bywa niepełna i uzupełniana
     * wstecz, a zdarzenie, które taki wniosek opiera, może być potwierdzone przez
     * rejestr — a wtedy nie da się go usunąć ([com.example.herdmanagementmobile.domain.model.allowsRemoval]).
     * Blokada w takim przypadku zakleszczałaby kartę sztuki: nie da się ani usunąć
     * przeszkody, ani zapisać zdarzenia, które faktycznie zaszło. Dlatego tam zostaje
     * ostrzeżenie z możliwością zapisu.
     */
    enum class Kind(val blocking: Boolean) {
        /** Urodzenie zdarza się raz w życiu sztuki — drugiego nie ma jak uzasadnić. */
        ALREADY_BORN(blocking = true),

        /** Sztuka przyjechała z innej siedziby, więc nie urodziła się tutaj. */
        ALREADY_ARRIVED(blocking = false),

        /** Padnięcie zdarza się raz w życiu sztuki. */
        ALREADY_DEAD(blocking = true),

        /** Sztuka jest już rozliczona jako ubyła albo padła. */
        ALREADY_LEFT(blocking = false),

        /** Sztuka już stoi w gospodarstwie, więc nie może do niego przybyć. */
        ALREADY_IN_HERD(blocking = false),
    }
}

/**
 * Wykrywa zdarzenia sprzeczne z tym, co już wiadomo o sztuce.
 *
 * **Model.** Zdarzenia dzielą się na wejścia (urodzenie, przybycie) i wyjścia
 * (ubycie, padnięcie) i muszą się przeplatać: sztuka nie wchodzi do gospodarstwa
 * dwa razy z rzędu ani nie wychodzi z niego dwa razy z rzędu. Dlatego urodzenie
 * i przybycie wykluczają się wzajemnie — sztuka albo urodziła się u hodowcy, albo
 * do niego przyjechała. Ubycie i padnięcie wykluczają się tak samo.
 *
 * Nie znaczy to jednak „tylko raz w ogóle”: sztuka może ubyć i wrócić (niedoszła
 * sprzedaż, powrót z targu czy z pastwiska), więc `przybycie → ubycie → przybycie`
 * jest poprawne i rejestr to przyjmie. Blokowanie drugiego przybycia na zawsze
 * uniemożliwiłoby zapisanie takiego obrotu.
 *
 * Wyjątkiem są urodzenie i padnięcie — te zdarzają się raz w życiu sztuki,
 * niezależnie od tego, co było później. Urodzenie jest przy tym zawsze pierwszym
 * zdarzeniem w historii, więc kłóci się z każdym wcześniejszym wpisem.
 *
 * **Waga sprzeczności.** Drugie urodzenie i drugie padnięcie są niemożliwe
 * bezwarunkowo, więc tych dwu formularz nie zapisze ([EventConflict.Kind.blocking]).
 * Reszta to wnioski z ewidencji, która bywa niepełna i uzupełniana wstecz — tam
 * aplikacja ostrzega, ale ostatnie słowo ma hodowca. Zadanie ostrzeżenia jest wąskie:
 * powiedzieć o odmowie, zanim rejestr ją wystawi.
 *
 * Odrzucone zgłoszenia nie wchodzą do rachunku ([withoutRejected]): skoro rejestr
 * ich nie przyjął, nic się nie stało i nie mogą blokować poprawnego wpisu — właśnie
 * po to hodowca poprawia odrzucone zgłoszenie i wysyła je jeszcze raz.
 */
object EventConflicts {

    /**
     * Zdarzenia wprowadzające sztukę do gospodarstwa. Dopełnieniem są wyjścia
     * (ubycie, padnięcie), więc jeden zbiór wystarczy do rozstrzygnięcia obu stron.
     */
    private val ENTRY = setOf(EventType.BIRTH, EventType.ARRIVAL)

    fun detect(type: EventType, existing: List<AnimalEvent>): EventConflict? {
        val actual = existing.withoutRejected()
        if (actual.isEmpty()) return null

        // Kolejność po dacie, a przy równej dacie po id — tak samo jak w statystykach,
        // inaczej „ostatnie zdarzenie” znaczyłoby w obu miejscach coś innego.
        val last = actual.maxWith(compareBy({ it.eventDate }, { it.id }))
        val inHerd = last.type in ENTRY

        return when (type) {
            // Urodzenie jest pierwszym zdarzeniem w historii sztuki, więc kłóci się
            // z każdym wpisem, jaki już jest. Rozróżniamy tylko powód, żeby komunikat
            // mówił hodowcy coś konkretnego.
            EventType.BIRTH -> {
                val born = actual.lastOfType(EventType.BIRTH)
                when {
                    born != null -> conflict(EventConflict.Kind.ALREADY_BORN, born)
                    inHerd -> conflict(EventConflict.Kind.ALREADY_ARRIVED, last)
                    else -> conflict(EventConflict.Kind.ALREADY_LEFT, last)
                }
            }

            // Przybycie po ubyciu jest poprawne (sztuka wraca), więc liczy się tylko to,
            // czy wedle ewidencji sztuka już stoi w gospodarstwie.
            EventType.ARRIVAL ->
                if (inHerd) conflict(EventConflict.Kind.ALREADY_IN_HERD, last) else null

            EventType.DEPARTURE ->
                if (inHerd) null else conflict(EventConflict.Kind.ALREADY_LEFT, last)

            EventType.DEATH -> {
                val dead = actual.lastOfType(EventType.DEATH)
                when {
                    dead != null -> conflict(EventConflict.Kind.ALREADY_DEAD, dead)
                    // Sztuka wyprowadzona z gospodarstwa nie może w nim padnąć —
                    // padnięcie zgłasza wtedy jej nowy posiadacz.
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
