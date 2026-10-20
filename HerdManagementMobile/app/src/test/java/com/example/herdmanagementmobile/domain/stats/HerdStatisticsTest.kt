package com.example.herdmanagementmobile.domain.stats

import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import com.example.herdmanagementmobile.domain.model.Sex
import com.example.herdmanagementmobile.domain.model.Treatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HerdStatisticsTest {

    private val today = LocalDate.of(2026, 9, 16)
    private var nextId = 1L

    private fun animal(
        birth: LocalDate? = today.minusYears(3),
        sex: Sex = Sex.FEMALE,
        breed: String? = "HO",
    ) = Animal(id = nextId++, earTag = "PL${nextId.toString().padStart(12, '0')}", birthDate = birth, sex = sex, breed = breed)

    private fun event(
        animalId: Long,
        type: EventType,
        date: LocalDate,
        status: IrzStatus = IrzStatus.PENDING,
    ) = AnimalEvent(id = nextId++, animalId = animalId, type = type, eventDate = date, irzStatus = status)

    /** Tworzy zdarzenie wysłane do rejestru w podanym dniu. */
    private fun submitted(
        animalId: Long,
        date: LocalDate,
        sentOn: LocalDate,
    ) = AnimalEvent(
        id = nextId++,
        animalId = animalId,
        type = EventType.BIRTH,
        eventDate = date,
        irzStatus = IrzStatus.CONFIRMED,
        irzSubmittedAt = sentOn.atTime(10, 0),
    )

    @Test
    fun `puste dane dają zerowe statystyki`() {
        val stats = HerdStatistics.compute(emptyList(), emptyList(), emptyList(), today)

        assertEquals(0, stats.inHerd)
        assertEquals(0, stats.leftHerd)
        assertEquals(HerdStats.MONTHS, stats.movements.size)
        assertEquals(YearMonth.of(2026, 9), stats.movements.last().month)
        assertEquals(YearMonth.of(2025, 10), stats.movements.first().month)
        assertTrue(stats.breeds.isEmpty())
    }

    @Test
    fun `ubycie i padnięcie usuwają zwierzę ze stanu`() {
        val a = animal(); val b = animal(); val c = animal()
        val events = listOf(
            event(a.id, EventType.BIRTH, today.minusMonths(5)),
            event(b.id, EventType.DEPARTURE, today.minusDays(3)),
            event(c.id, EventType.DEATH, today.minusDays(1)),
        )

        val stats = HerdStatistics.compute(listOf(a, b, c), events, emptyList(), today)

        assertEquals(1, stats.inHerd)
        assertEquals(2, stats.leftHerd)
    }

    @Test
    fun `ponowne przybycie po ubyciu przywraca zwierzę`() {
        val a = animal()
        val events = listOf(
            event(a.id, EventType.DEPARTURE, today.minusMonths(2)),
            event(a.id, EventType.ARRIVAL, today.minusMonths(1)),
        )

        assertTrue(HerdStatistics.isInHerd(events))
    }

    @Test
    fun `zdarzenia z tej samej daty rozstrzyga kolejność dodania`() {
        val a = animal()
        val events = listOf(
            event(a.id, EventType.ARRIVAL, today),
            event(a.id, EventType.DEPARTURE, today),
        )

        assertFalse(HerdStatistics.isInHerd(events))
    }

    @Test
    fun `grupy wiekowe`() {
        val animals = listOf(
            animal(birth = today.minusMonths(2)),
            animal(birth = today.minusMonths(6)),
            animal(birth = today.minusMonths(23), sex = Sex.MALE),
            animal(birth = today.minusMonths(24)),
            animal(birth = today.minusYears(4), sex = Sex.MALE),
            animal(birth = today.minusYears(4), sex = Sex.OTHER),
            animal(birth = null),
        )

        val groups = HerdStatistics.compute(animals, emptyList(), emptyList(), today).ageGroups

        assertEquals(1, groups[AgeGroup.CALVES])
        // Zwierzęta w wieku 6–24 miesięcy są klasyfikowane według płci.
        assertEquals(1, groups[AgeGroup.HEIFERS])
        assertEquals(1, groups[AgeGroup.YOUNG_BULLS])
        assertEquals(1, groups[AgeGroup.COWS])
        assertEquals(1, groups[AgeGroup.BULLS])
        assertEquals(1, groups[AgeGroup.OTHER_SEX])
        assertEquals(1, groups[AgeGroup.UNKNOWN_AGE])
    }

    @Test
    fun `roczny samiec jest byczkiem a nie bezplciowa mlodzieza`() {
        // Samiec powyżej wieku cielęcego należy do grupy byczków.
        val byczek = animal(birth = today.minusMonths(13), sex = Sex.MALE)
        val jalowka = animal(birth = today.minusMonths(13), sex = Sex.FEMALE)

        val groups = HerdStatistics
            .compute(listOf(byczek, jalowka), emptyList(), emptyList(), today)
            .ageGroups

        assertEquals(1, groups[AgeGroup.YOUNG_BULLS])
        assertEquals(1, groups[AgeGroup.HEIFERS])
    }

    @Test
    fun `granica dorosloci wypada na 24 miesiacu`() {
        val przed = animal(birth = today.minusMonths(23), sex = Sex.FEMALE)
        val po = animal(birth = today.minusMonths(24), sex = Sex.FEMALE)

        val groups = HerdStatistics
            .compute(listOf(przed, po), emptyList(), emptyList(), today)
            .ageGroups

        assertEquals(1, groups[AgeGroup.HEIFERS])
        assertEquals(1, groups[AgeGroup.COWS])
    }

    @Test
    fun `podzial na plec obejmuje cale stado`() {
        val animals = listOf(
            animal(sex = Sex.FEMALE),
            animal(sex = Sex.FEMALE),
            animal(sex = Sex.MALE),
        )

        val counts = HerdStatistics.compute(animals, emptyList(), emptyList(), today).sexCounts

        assertEquals(2, counts[Sex.FEMALE])
        assertEquals(1, counts[Sex.MALE])
        assertEquals(0, counts[Sex.OTHER])
    }

    @Test
    fun `rasy są normalizowane i ogon zwijany do Inne`() {
        val animals = listOf("HO", "ho ", "HO", "SM", "SM", "LM", "CH", "AN", "HF", null, "")
            .map { animal(breed = it) }

        val breeds = HerdStatistics.compute(animals, emptyList(), emptyList(), today).breeds

        assertEquals(HerdStats.MAX_BREEDS, breeds.size)
        assertEquals("HO" to 3, breeds[0])
        assertEquals(HerdStats.UNKNOWN_BREED to 2, breeds[1])
        assertEquals("SM" to 2, breeds[2])
        assertEquals("AN" to 1, breeds[3])
        assertEquals(HerdStats.OTHER_BREEDS to 3, breeds.last())
        assertEquals(animals.size, breeds.sumOf { it.second })
    }

    @Test
    fun `ruch stada liczy tylko ostatnie 12 miesięcy`() {
        val a = animal()
        val events = listOf(
            event(a.id, EventType.BIRTH, today.withDayOfMonth(1)),
            event(a.id, EventType.ARRIVAL, today.minusMonths(11).withDayOfMonth(1)),
            event(a.id, EventType.DEPARTURE, today.minusMonths(12)),
            event(a.id, EventType.DEATH, today.plusMonths(1)),
        )

        val stats = HerdStatistics.compute(listOf(a), events, emptyList(), today)

        assertEquals(1, stats.movements.last().births)
        assertEquals(1, stats.movements.first().arrivals)
        val totals = stats.movementTotals
        assertEquals(2, totals.inflow)
        assertEquals(0, totals.outflow)
    }

    @Test
    fun `karencja liczona per zwierzę w stadzie`() {
        val inHerd = animal(); val gone = animal()
        val treatments = listOf(
            Treatment(animalId = inHerd.id, drug = "A", treatmentDate = today.minusDays(5), withdrawalUntil = today),
            Treatment(animalId = inHerd.id, drug = "B", treatmentDate = today.minusDays(2), withdrawalUntil = today.plusDays(3)),
            Treatment(animalId = gone.id, drug = "C", treatmentDate = today.minusDays(2), withdrawalUntil = today.plusDays(3)),
            Treatment(animalId = inHerd.id, drug = "D", treatmentDate = today.minusYears(2), withdrawalUntil = today.minusYears(2)),
        )
        val events = listOf(event(gone.id, EventType.DEPARTURE, today.minusDays(1)))

        val stats = HerdStatistics.compute(listOf(inHerd, gone), events, treatments, today)

        assertEquals(1, stats.animalsInWithdrawal)
        assertEquals(3, stats.treatmentsLast12Months)
    }

    @Test
    fun `statusy zgłoszeń IRZ`() {
        val a = animal()
        val events = listOf(
            event(a.id, EventType.BIRTH, today, IrzStatus.CONFIRMED),
            event(a.id, EventType.ARRIVAL, today, IrzStatus.REJECTED),
            event(a.id, EventType.ARRIVAL, today, IrzStatus.PENDING),
        )

        val counts = HerdStatistics.compute(listOf(a), events, emptyList(), today).irzStatusCounts

        assertEquals(1, counts[IrzStatus.CONFIRMED])
        assertEquals(1, counts[IrzStatus.REJECTED])
        assertEquals(1, counts[IrzStatus.PENDING])
        assertEquals(0, counts[IrzStatus.SUBMITTED])
    }

    @Test
    fun `sredni wiek liczy tylko sztuki w stadzie`() {
        val a = animal(birth = today.minusMonths(10))
        val b = animal(birth = today.minusMonths(20))

        val stats = HerdStatistics.compute(listOf(a, b), emptyList(), emptyList(), today)

        assertEquals(15, stats.averageAgeMonths)
    }

    @Test
    fun `sredni wiek jest nieznany gdy brakuje dat urodzenia`() {
        val stats = HerdStatistics.compute(listOf(animal(birth = null)), emptyList(), emptyList(), today)

        assertEquals(null, stats.averageAgeMonths)
    }

    @Test
    fun `zgloszenie w siodmym dniu jest jeszcze terminowe`() {
        val a = animal()
        val events = listOf(submitted(a.id, today.minusDays(7), sentOn = today))

        val stats = HerdStatistics.compute(listOf(a), events, emptyList(), today)

        assertEquals(1, stats.reporting.onTime)
        assertEquals(0, stats.reporting.late)
        assertEquals(100, stats.reporting.onTimePercent)
    }

    @Test
    fun `zgloszenie osmego dnia jest spoznione`() {
        val a = animal()
        val events = listOf(submitted(a.id, today.minusDays(8), sentOn = today))

        val stats = HerdStatistics.compute(listOf(a), events, emptyList(), today)

        assertEquals(0, stats.reporting.onTime)
        assertEquals(1, stats.reporting.late)
        assertEquals(0, stats.reporting.onTimePercent)
    }

    @Test
    fun `niewyslane zdarzenia nie wchodza do terminowosci`() {
        // Zdarzenie niewysłane nie jest uwzględniane w terminowości.
        val a = animal()
        val events = listOf(
            event(a.id, EventType.BIRTH, today.minusDays(30)),
            submitted(a.id, today.minusDays(2), sentOn = today),
        )

        val stats = HerdStatistics.compute(listOf(a), events, emptyList(), today)

        assertEquals(1, stats.reporting.total)
        assertEquals(1, stats.reporting.onTime)
    }

    @Test
    fun `bez zadnych wyslanych zgloszen odsetek jest nieznany`() {
        val stats = HerdStatistics.compute(emptyList(), emptyList(), emptyList(), today)

        assertEquals(0, stats.reporting.total)
        assertEquals(null, stats.reporting.onTimePercent)
    }

    @Test
    fun `odrzucone urodzenie nie podbija ruchu stada`() {
        // Drugie zgłoszenie urodzenia tego samego zwierzęcia zostało odrzucone
        // i nie może być uwzględnione w statystykach.
        val a = animal(birth = today.minusDays(3), sex = Sex.MALE)
        val events = listOf(
            event(a.id, EventType.BIRTH, today.minusDays(3), IrzStatus.CONFIRMED),
            event(a.id, EventType.BIRTH, today.minusDays(3), IrzStatus.REJECTED),
        )

        val stats = HerdStatistics.compute(listOf(a), events, emptyList(), today)

        assertEquals(1, stats.movements.last().births)
        assertEquals(1, stats.movementTotals.inflow)
    }

    @Test
    fun `odrzucone ubycie nie wyprowadza sztuki ze stada`() {
        // Odrzucone zgłoszenie ubycia nie zmienia stanu stada.
        val a = animal()
        val events = listOf(
            event(a.id, EventType.ARRIVAL, today.minusMonths(2), IrzStatus.CONFIRMED),
            event(a.id, EventType.DEPARTURE, today.minusDays(1), IrzStatus.REJECTED),
        )

        val stats = HerdStatistics.compute(listOf(a), events, emptyList(), today)

        assertTrue(HerdStatistics.isInHerd(events))
        assertEquals(1, stats.inHerd)
        assertEquals(0, stats.leftHerd)
        assertEquals(0, stats.movementTotals.outflow)
    }

    @Test
    fun `odrzucone padniecie nie konczy zycia sztuki w ewidencji`() {
        val a = animal()
        val events = listOf(event(a.id, EventType.DEATH, today, IrzStatus.REJECTED))

        val stats = HerdStatistics.compute(listOf(a), events, emptyList(), today)

        assertEquals(1, stats.inHerd)
        assertEquals(0, stats.movements.last().deaths)
    }

    @Test
    fun `niezgloszone zdarzenie wciaz liczy sie do stada`() {
        // Zdarzenia niezgłoszone (PENDING) są uwzględniane w stanie stada.
        val a = animal(birth = today.minusDays(1))
        val events = listOf(event(a.id, EventType.BIRTH, today.minusDays(1), IrzStatus.PENDING))

        val stats = HerdStatistics.compute(listOf(a), events, emptyList(), today)

        assertEquals(1, stats.inHerd)
        assertEquals(1, stats.movements.last().births)
    }

    @Test
    fun `odrzucone zgloszenie nie zawyza terminowosci`() {
        val a = animal()
        val rejected = AnimalEvent(
            id = 9001L,
            animalId = a.id,
            type = EventType.BIRTH,
            eventDate = today.minusDays(1),
            irzStatus = IrzStatus.REJECTED,
            irzSubmittedAt = today.atTime(10, 0),
        )
        val events = listOf(submitted(a.id, today.minusDays(1), sentOn = today), rejected)

        val stats = HerdStatistics.compute(listOf(a), events, emptyList(), today)

        // Zgłoszenie odrzucone posiada znacznik wysłania, lecz nie zostało zarejestrowane.
        assertEquals(1, stats.reporting.total)
        assertEquals(1, stats.reporting.onTime)
    }

    @Test
    fun `odrzucone zdarzenia nadal widac w rozkladzie statusow`() {
        // Rozkład statusów IRZ obejmuje również zgłoszenia odrzucone.
        val a = animal()
        val events = listOf(event(a.id, EventType.DEATH, today, IrzStatus.REJECTED))

        val counts = HerdStatistics.compute(listOf(a), events, emptyList(), today).irzStatusCounts

        assertEquals(1, counts[IrzStatus.REJECTED])
    }
}
