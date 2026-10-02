package com.example.herdmanagementmobile.domain.validation

import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Testy ostrzeżenia o zdarzeniu sprzecznym z ewidencją.
 *
 * Pilnują przypadku, który zgłosił użytkownik: dwa razy dodane urodzenie tej samej
 * sztuki. Rejestr odrzuca drugie zgłoszenie, ale dowiadujemy się o tym dopiero po
 * wysłaniu — dopóki oba wpisy są niezgłoszone, statystyki liczą je jak dwa zdarzenia.
 *
 * Druga, równie ważna strona tych testów: ostrzeżenie nie może krzyczeć bez powodu.
 * Przybycie po ubyciu jest poprawne, a odrzucone zgłoszenie nie może blokować
 * poprawionego wpisu — inaczej hodowca nauczy się klikać „Dodaj mimo to” odruchowo.
 */
class EventConflictsTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 1)
    private var nextId = 1L

    private fun event(
        type: EventType,
        date: LocalDate,
        status: IrzStatus = IrzStatus.CONFIRMED,
    ) = AnimalEvent(id = nextId++, animalId = 1L, type = type, eventDate = date, irzStatus = status)

    // --- Duplikaty ------------------------------------------------------

    @Test
    fun `drugie urodzenie tej samej sztuki jest sprzeczne`() {
        val existing = listOf(event(EventType.BIRTH, today.minusDays(3)))

        val conflict = EventConflicts.detect(EventType.BIRTH, existing)

        assertEquals(EventConflict.Kind.ALREADY_BORN, conflict?.kind)
        assertEquals(today.minusDays(3), conflict?.conflictingDate)
    }

    @Test
    fun `duplikat jest wykryty takze gdy zgloszenie jeszcze nie poszlo`() {
        // Najgorszy wariant: oba wpisy niezgłoszone, więc rejestr jeszcze nic nie wie,
        // a statystyki liczą dwa urodzenia.
        val existing = listOf(event(EventType.BIRTH, today, IrzStatus.PENDING))

        assertEquals(
            EventConflict.Kind.ALREADY_BORN,
            EventConflicts.detect(EventType.BIRTH, existing)?.kind,
        )
    }

    @Test
    fun `drugie padniecie jest sprzeczne`() {
        val existing = listOf(event(EventType.DEATH, today.minusDays(1)))

        assertEquals(
            EventConflict.Kind.ALREADY_DEAD,
            EventConflicts.detect(EventType.DEATH, existing)?.kind,
        )
    }

    @Test
    fun `ubycie sztuki juz rozliczonej jest sprzeczne`() {
        val existing = listOf(
            event(EventType.ARRIVAL, today.minusMonths(2)),
            event(EventType.DEPARTURE, today.minusDays(5)),
        )

        val conflict = EventConflicts.detect(EventType.DEPARTURE, existing)

        assertEquals(EventConflict.Kind.ALREADY_LEFT, conflict?.kind)
        assertEquals(today.minusDays(5), conflict?.conflictingDate)
    }

    @Test
    fun `ubycie sztuki padlej jest sprzeczne`() {
        val existing = listOf(event(EventType.DEATH, today.minusDays(2)))

        assertEquals(
            EventConflict.Kind.ALREADY_LEFT,
            EventConflicts.detect(EventType.DEPARTURE, existing)?.kind,
        )
    }

    @Test
    fun `padniecie sztuki ktora opuscila gospodarstwo jest sprzeczne`() {
        // Padnięcie zgłasza posiadacz, u którego sztuka stoi — po ubyciu to już ktoś inny.
        val existing = listOf(event(EventType.DEPARTURE, today.minusDays(4)))

        assertEquals(
            EventConflict.Kind.ALREADY_LEFT,
            EventConflicts.detect(EventType.DEATH, existing)?.kind,
        )
    }

    @Test
    fun `przybycie sztuki ktora juz jest w stadzie jest sprzeczne`() {
        val existing = listOf(event(EventType.BIRTH, today.minusMonths(6)))

        val conflict = EventConflicts.detect(EventType.ARRIVAL, existing)

        assertEquals(EventConflict.Kind.ALREADY_IN_HERD, conflict?.kind)
        assertEquals(today.minusMonths(6), conflict?.conflictingDate)
    }

    // --- Przypadki poprawne ---------------------------------------------

    @Test
    fun `sztuka bez historii nie wywoluje ostrzezenia`() {
        EventType.entries.forEach { type ->
            assertNull(type.name, EventConflicts.detect(type, emptyList()))
        }
    }

    @Test
    fun `przybycie po ubyciu jest poprawne`() {
        // Sztuka wraca do gospodarstwa — normalny obrót, nie pomyłka.
        val existing = listOf(
            event(EventType.ARRIVAL, today.minusMonths(5)),
            event(EventType.DEPARTURE, today.minusMonths(2)),
        )

        assertNull(EventConflicts.detect(EventType.ARRIVAL, existing))
    }

    @Test
    fun `ubycie sztuki stojacej w gospodarstwie jest poprawne`() {
        val existing = listOf(event(EventType.BIRTH, today.minusMonths(8)))

        assertNull(EventConflicts.detect(EventType.DEPARTURE, existing))
    }

    @Test
    fun `padniecie sztuki w stadzie jest poprawne`() {
        val existing = listOf(event(EventType.ARRIVAL, today.minusMonths(3)))

        assertNull(EventConflicts.detect(EventType.DEATH, existing))
    }

    @Test
    fun `odrzucone zgloszenie nie blokuje poprawionego wpisu`() {
        // Po odrzuceniu hodowca poprawia dane i wysyła jeszcze raz — gdyby odrzucone
        // liczyło się jak duplikat, każda poprawka wymagałaby „Dodaj mimo to”.
        val existing = listOf(event(EventType.BIRTH, today.minusDays(2), IrzStatus.REJECTED))

        assertNull(EventConflicts.detect(EventType.BIRTH, existing))
    }

    @Test
    fun `odrzucone ubycie nie czyni sztuki rozliczona`() {
        val existing = listOf(
            event(EventType.ARRIVAL, today.minusMonths(2)),
            event(EventType.DEPARTURE, today.minusDays(1), IrzStatus.REJECTED),
        )

        assertNull(EventConflicts.detect(EventType.DEPARTURE, existing))
        // ...ale przybycie wciąż jest sprzeczne, bo sztuka nigdzie nie wyjechała.
        assertEquals(
            EventConflict.Kind.ALREADY_IN_HERD,
            EventConflicts.detect(EventType.ARRIVAL, existing)?.kind,
        )
    }

    @Test
    fun `o sprzecznosci decyduje ostatnie zdarzenie a nie kolejnosc na liscie`() {
        // Lista z bazy bywa posortowana inaczej; liczy się data, a przy równej — id.
        val existing = listOf(
            event(EventType.DEPARTURE, today.minusMonths(2)),
            event(EventType.ARRIVAL, today.minusMonths(1)),
        ).reversed()

        assertNull(EventConflicts.detect(EventType.DEPARTURE, existing))
        assertEquals(
            EventConflict.Kind.ALREADY_IN_HERD,
            EventConflicts.detect(EventType.ARRIVAL, existing)?.kind,
        )
    }

    // --- Wejscia i wyjscia wykluczaja sie wzajemnie ---------------------

    @Test
    fun `urodzenie sztuki ktora przybyla jest sprzeczne`() {
        // Luka zgloszona przez uzytkownika: sztuka albo urodzila sie u hodowcy, albo
        // do niego przyjechala. Wczesniej ten przypadek nie byl w ogole wykrywany.
        val existing = listOf(event(EventType.ARRIVAL, today.minusMonths(1)))

        val conflict = EventConflicts.detect(EventType.BIRTH, existing)

        assertEquals(EventConflict.Kind.ALREADY_ARRIVED, conflict?.kind)
        assertEquals(today.minusMonths(1), conflict?.conflictingDate)
    }

    @Test
    fun `urodzenie sztuki z jakakolwiek historia jest sprzeczne`() {
        // Urodzenie jest zawsze pierwszym zdarzeniem w historii sztuki.
        val existing = listOf(
            event(EventType.ARRIVAL, today.minusMonths(4)),
            event(EventType.DEPARTURE, today.minusMonths(1)),
        )

        val conflict = EventConflicts.detect(EventType.BIRTH, existing)

        assertEquals(EventConflict.Kind.ALREADY_LEFT, conflict?.kind)
    }

    @Test
    fun `urodzenie jest sprzeczne nawet gdy sztuka wrocila do stada`() {
        val existing = listOf(
            event(EventType.BIRTH, today.minusYears(2)),
            event(EventType.DEPARTURE, today.minusMonths(3)),
            event(EventType.ARRIVAL, today.minusMonths(1)),
        )

        assertEquals(
            EventConflict.Kind.ALREADY_BORN,
            EventConflicts.detect(EventType.BIRTH, existing)?.kind,
        )
    }

    @Test
    fun `padniecie jest sprzeczne nawet gdy sztuka ma pozniejsze zdarzenia`() {
        // Padniecie zdarza sie raz w zyciu sztuki, niezaleznie od tego, co wpisano potem.
        val existing = listOf(
            event(EventType.DEATH, today.minusMonths(2)),
            event(EventType.ARRIVAL, today.minusMonths(1)),
        )

        assertEquals(
            EventConflict.Kind.ALREADY_DEAD,
            EventConflicts.detect(EventType.DEATH, existing)?.kind,
        )
    }

    @Test
    fun `przybycie sztuki urodzonej u hodowcy jest sprzeczne`() {
        val existing = listOf(event(EventType.BIRTH, today.minusMonths(2)))

        assertEquals(
            EventConflict.Kind.ALREADY_IN_HERD,
            EventConflicts.detect(EventType.ARRIVAL, existing)?.kind,
        )
    }

    @Test
    fun `po ubyciu mozna zapisac powrot i kolejne ubycie`() {
        // Niedoszla sprzedaz albo powrot z targu: `przybycie -> ubycie -> przybycie`
        // jest poprawnym obrotem i rejestr to przyjmie. Twarda blokada drugiego
        // przybycia uniemozliwilaby zapisanie tego w ogole.
        var historia = listOf(
            event(EventType.ARRIVAL, today.minusMonths(6)),
            event(EventType.DEPARTURE, today.minusMonths(3)),
        )
        assertNull(EventConflicts.detect(EventType.ARRIVAL, historia))

        historia = historia + event(EventType.ARRIVAL, today.minusMonths(2))
        assertNull(EventConflicts.detect(EventType.DEPARTURE, historia))
        assertNull(EventConflicts.detect(EventType.DEATH, historia))
    }

    // --- Waga sprzecznosci: blokada kontra ostrzezenie -------------------

    @Test
    fun `drugie urodzenie i drugie padniecie blokuja zapis`() {
        // Bezwarunkowe: zadna historia ewidencji nie uzasadni drugiego urodzenia
        // ani drugiego padniecia tej samej sztuki.
        val born = listOf(event(EventType.BIRTH, today.minusMonths(3)))
        val dead = listOf(event(EventType.DEATH, today.minusDays(2)))

        assertEquals(true, EventConflicts.detect(EventType.BIRTH, born)?.blocking)
        assertEquals(true, EventConflicts.detect(EventType.DEATH, dead)?.blocking)
    }

    @Test
    fun `wnioski z ewidencji tylko ostrzegaja`() {
        // Te sprzecznosci wynikaja z tego, co aplikacja WIE o sztuce, a ewidencja bywa
        // niepelna i uzupelniana wstecz. Gdyby blokowaly, a zdarzenie stojace na
        // przeszkodzie bylo potwierdzone przez rejestr (czyli nieusuwalne), karta
        // sztuki byłaby zakleszczona: ani usunac przeszkody, ani zapisac prawdy.
        val arrived = listOf(event(EventType.ARRIVAL, today.minusMonths(1)))
        val gone = listOf(event(EventType.DEPARTURE, today.minusDays(4)))

        assertEquals(
            EventConflict.Kind.ALREADY_ARRIVED,
            EventConflicts.detect(EventType.BIRTH, arrived)?.kind,
        )
        assertEquals(false, EventConflicts.detect(EventType.BIRTH, arrived)?.blocking)
        assertEquals(false, EventConflicts.detect(EventType.DEATH, gone)?.blocking)
        assertEquals(false, EventConflicts.detect(EventType.ARRIVAL, arrived)?.blocking)
        assertEquals(false, EventConflicts.detect(EventType.DEPARTURE, gone)?.blocking)
    }

    @Test
    fun `blokuja dokladnie dwa rodzaje sprzecznosci`() {
        // Nowy rodzaj sprzecznosci ma wymagac swiadomej decyzji o jego wadze.
        assertEquals(
            listOf(EventConflict.Kind.ALREADY_BORN, EventConflict.Kind.ALREADY_DEAD),
            EventConflict.Kind.entries.filter { it.blocking },
        )
    }

    @Test
    fun `odrzucone urodzenie nie blokuje poprawionego zgloszenia`() {
        // Najwazniejsza granica blokady: po odmowie rejestru hodowca poprawia dane
        // i wysyla jeszcze raz. Blokada w tym miejscu zamykalaby mu te droge.
        val existing = listOf(event(EventType.BIRTH, today.minusDays(2), IrzStatus.REJECTED))

        assertNull(EventConflicts.detect(EventType.BIRTH, existing))
    }
}
