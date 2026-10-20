package com.example.herdmanagementmobile.domain.validation

import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Testy wykrywania zdarzeń sprzecznych z ewidencją.
 *
 * Weryfikowane jest zarówno wykrywanie sprzeczności (np. powtórne urodzenie), jak
 * i brak fałszywych ostrzeżeń dla sekwencji poprawnych (przybycie po ubyciu,
 * ponowne zgłoszenie po odrzuceniu).
 */
class EventConflictsTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 1)
    private var nextId = 1L

    private fun event(
        type: EventType,
        date: LocalDate,
        status: IrzStatus = IrzStatus.CONFIRMED,
    ) = AnimalEvent(id = nextId++, animalId = 1L, type = type, eventDate = date, irzStatus = status)

    @Test
    fun `drugie urodzenie tej samej sztuki jest sprzeczne`() {
        val existing = listOf(event(EventType.BIRTH, today.minusDays(3)))

        val conflict = EventConflicts.detect(EventType.BIRTH, existing)

        assertEquals(EventConflict.Kind.ALREADY_BORN, conflict?.kind)
        assertEquals(today.minusDays(3), conflict?.conflictingDate)
    }

    @Test
    fun `duplikat jest wykryty takze gdy zgloszenie jeszcze nie poszlo`() {
        // Oba wpisy niezgłoszone — sprzeczność musi zostać wykryta przed wysłaniem.
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
        // Po ubyciu padnięcie zgłasza nowy posiadacz zwierzęcia.
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

    @Test
    fun `sztuka bez historii nie wywoluje ostrzezenia`() {
        EventType.entries.forEach { type ->
            assertNull(type.name, EventConflicts.detect(type, emptyList()))
        }
    }

    @Test
    fun `przybycie po ubyciu jest poprawne`() {
        // Powrót zwierzęcia do gospodarstwa jest poprawną sekwencją.
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
        // Zgłoszenie odrzucone nie blokuje ponownego wprowadzenia poprawionego zdarzenia.
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
        // Przybycie pozostaje sprzeczne, ponieważ zwierzę nie opuściło gospodarstwa.
        assertEquals(
            EventConflict.Kind.ALREADY_IN_HERD,
            EventConflicts.detect(EventType.ARRIVAL, existing)?.kind,
        )
    }

    @Test
    fun `o sprzecznosci decyduje ostatnie zdarzenie a nie kolejnosc na liscie`() {
        // Kolejność określa data, a przy równych datach identyfikator.
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

    @Test
    fun `urodzenie sztuki ktora przybyla jest sprzeczne`() {
        // Urodzenie i przybycie wykluczają się wzajemnie.
        val existing = listOf(event(EventType.ARRIVAL, today.minusMonths(1)))

        val conflict = EventConflicts.detect(EventType.BIRTH, existing)

        assertEquals(EventConflict.Kind.ALREADY_ARRIVED, conflict?.kind)
        assertEquals(today.minusMonths(1), conflict?.conflictingDate)
    }

    @Test
    fun `urodzenie sztuki z jakakolwiek historia jest sprzeczne`() {
        // Urodzenie musi być pierwszym zdarzeniem w historii zwierzęcia.
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
        // Padnięcie może wystąpić tylko raz, niezależnie od późniejszych wpisów.
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
        // Sekwencja `przybycie -> ubycie -> przybycie` jest poprawna.
        var historia = listOf(
            event(EventType.ARRIVAL, today.minusMonths(6)),
            event(EventType.DEPARTURE, today.minusMonths(3)),
        )
        assertNull(EventConflicts.detect(EventType.ARRIVAL, historia))

        historia = historia + event(EventType.ARRIVAL, today.minusMonths(2))
        assertNull(EventConflicts.detect(EventType.DEPARTURE, historia))
        assertNull(EventConflicts.detect(EventType.DEATH, historia))
    }

    @Test
    fun `drugie urodzenie i drugie padniecie blokuja zapis`() {
        // Powtórne urodzenie i powtórne padnięcie są sprzecznościami bezwarunkowymi.
        val born = listOf(event(EventType.BIRTH, today.minusMonths(3)))
        val dead = listOf(event(EventType.DEATH, today.minusDays(2)))

        assertEquals(true, EventConflicts.detect(EventType.BIRTH, born)?.blocking)
        assertEquals(true, EventConflicts.detect(EventType.DEATH, dead)?.blocking)
    }

    @Test
    fun `wnioski z ewidencji tylko ostrzegaja`() {
        // Sprzeczności wynikające z wnioskowania na podstawie ewidencji nie blokują zapisu,
        // ponieważ zdarzenie potwierdzone w rejestrze nie może zostać usunięte.
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
        // Dodanie nowego rodzaju sprzeczności wymaga jawnego określenia jego wagi.
        assertEquals(
            listOf(EventConflict.Kind.ALREADY_BORN, EventConflict.Kind.ALREADY_DEAD),
            EventConflict.Kind.entries.filter { it.blocking },
        )
    }

    @Test
    fun `odrzucone urodzenie nie blokuje poprawionego zgloszenia`() {
        // Zgłoszenie odrzucone nie może blokować ponownego wprowadzenia zdarzenia.
        val existing = listOf(event(EventType.BIRTH, today.minusDays(2), IrzStatus.REJECTED))

        assertNull(EventConflicts.detect(EventType.BIRTH, existing))
    }
}
