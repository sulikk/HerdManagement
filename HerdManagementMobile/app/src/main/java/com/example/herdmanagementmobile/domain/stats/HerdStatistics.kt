package com.example.herdmanagementmobile.domain.stats

import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.ComplianceItem
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import com.example.herdmanagementmobile.domain.model.Sex
import com.example.herdmanagementmobile.domain.model.Treatment
import com.example.herdmanagementmobile.domain.model.withoutRejected
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Grupa wiekowo-płciowa według nazewnictwa hodowlanego. Powyżej wieku cielęcego
 * płeć określa kategorię użytkową (jałówka, byczek), a wiek — dojrzałość.
 */
enum class AgeGroup {
    /** Cielęta obu płci, do [HerdStats.CALF_MONTHS] miesięcy. */
    CALVES,

    /** Jałówki — samice od odsadzenia do [HerdStats.ADULT_MONTHS] miesięcy. */
    HEIFERS,

    /** Byczki i opasy — samce od odsadzenia do [HerdStats.ADULT_MONTHS] miesięcy. */
    YOUNG_BULLS,

    /** Krowy — samice dorosłe. */
    COWS,

    /** Buhaje — samce dorosłe. */
    BULLS,

    /** Zwierzęta o nieokreślonej płci, powyżej wieku cielęcego. */
    OTHER_SEX,

    /** Zwierzęta bez podanej daty urodzenia. */
    UNKNOWN_AGE,
}

/** Liczba zdarzeń zmieniających stan stada w danym miesiącu. */
data class MonthlyMovement(
    val month: YearMonth,
    val births: Int = 0,
    val arrivals: Int = 0,
    val departures: Int = 0,
    val deaths: Int = 0,
) {
    val inflow: Int get() = births + arrivals
    val outflow: Int get() = departures + deaths
}

/**
 * Terminowość zgłoszeń do IRZplus względem ustawowego terminu 7 dni.
 *
 * Uwzględniane są wyłącznie zdarzenia wysłane do rejestru (z ustawionym znacznikiem
 * wysyłki).
 */
data class ReportingStats(
    val onTime: Int = 0,
    val late: Int = 0,
) {
    val total: Int get() = onTime + late

    /** Odsetek zgłoszeń terminowych; `null`, gdy brak wysłanych zgłoszeń. */
    val onTimePercent: Int?
        get() = if (total == 0) null else (onTime * 100) / total
}

/** Zagregowane statystyki stada prezentowane na ekranie statystyk. */
data class HerdStats(
    /** Liczba zwierząt obecnych w stadzie. */
    val inHerd: Int,
    /** Liczba zwierząt, których ostatnim zdarzeniem jest ubycie lub padnięcie. */
    val leftHerd: Int,
    val ageGroups: Map<AgeGroup, Int>,
    /** Rasy w kolejności malejącej liczebności; najrzadsze zgrupowane jako [OTHER_BREEDS]. */
    val breeds: List<Pair<String, Int>>,
    /** Ruch stada w ostatnich [MONTHS] miesiącach, od najstarszego. */
    val movements: List<MonthlyMovement>,
    /** Liczba zwierząt w stadzie objętych trwającą karencją. */
    val animalsInWithdrawal: Int,
    val treatmentsLast12Months: Int,
    val irzStatusCounts: Map<IrzStatus, Int>,
    /** Średni wiek zwierząt w miesiącach; `null`, gdy brak dat urodzenia. */
    val averageAgeMonths: Int? = null,
    /** Liczebność według płci, niezależnie od wieku. */
    val sexCounts: Map<Sex, Int> = emptyMap(),
    /** Terminowość zgłoszeń do IRZplus. */
    val reporting: ReportingStats = ReportingStats(),
) {
    val movementTotals: MonthlyMovement
        get() = movements.fold(MonthlyMovement(movements.lastOrNull()?.month ?: YearMonth.now())) { acc, m ->
            acc.copy(
                births = acc.births + m.births,
                arrivals = acc.arrivals + m.arrivals,
                departures = acc.departures + m.departures,
                deaths = acc.deaths + m.deaths,
            )
        }

    companion object {
        const val MONTHS = 12
        const val MAX_BREEDS = 5
        const val OTHER_BREEDS = "Inne"
        const val UNKNOWN_BREED = "Nie podano"
        const val CALF_MONTHS = 6L
        const val ADULT_MONTHS = 24L
    }
}

/** Oblicza statystyki stada na podstawie zwierząt, zdarzeń i leczeń. */
object HerdStatistics {

    private val CLOSING = setOf(EventType.DEPARTURE, EventType.DEATH)

    fun compute(
        animals: List<Animal>,
        events: List<AnimalEvent>,
        treatments: List<Treatment>,
        today: LocalDate = LocalDate.now(),
    ): HerdStats {
        // Ruch stada i terminowość obliczane są bez zgłoszeń odrzuconych,
        // rozkład statusów IRZ — ze wszystkich zdarzeń.
        val actual = events.withoutRejected()
        val eventsByAnimal = events.groupBy { it.animalId }
        val present = animals.filter { isInHerd(eventsByAnimal[it.id].orEmpty()) }
        val presentIds = present.map { it.id }.toSet()

        return HerdStats(
            inHerd = present.size,
            leftHerd = animals.size - present.size,
            ageGroups = AgeGroup.entries.associateWith { group ->
                present.count { ageGroup(it, today) == group }
            },
            breeds = breeds(present),
            movements = movements(actual, YearMonth.from(today)),
            animalsInWithdrawal = treatments
                .filter { it.animalId in presentIds }
                .filter { it.withdrawalUntil?.let { until -> !until.isBefore(today) } == true }
                .map { it.animalId }
                .distinct()
                .size,
            treatmentsLast12Months = treatments.count {
                !it.treatmentDate.isAfter(today) && it.treatmentDate.isAfter(today.minusMonths(12))
            },
            irzStatusCounts = IrzStatus.entries.associateWith { status ->
                events.count { it.irzStatus == status }
            },
            averageAgeMonths = averageAgeMonths(present, today),
            sexCounts = Sex.entries.associateWith { sex -> present.count { it.sex == sex } },
            reporting = reporting(actual),
        )
    }

    /** Oblicza średni wiek zwierząt z podaną datą urodzenia. */
    private fun averageAgeMonths(animals: List<Animal>, today: LocalDate): Int? {
        val ages = animals.mapNotNull { animal ->
            animal.birthDate?.let { ChronoUnit.MONTHS.between(it, today) }
        }
        return if (ages.isEmpty()) null else (ages.sum() / ages.size).toInt()
    }

    /**
     * Oblicza terminowość zgłoszeń. Zgłoszenie jest terminowe, jeżeli zostało wysłane
     * najpóźniej siódmego dnia od zdarzenia. Lista wejściowa nie powinna zawierać
     * zgłoszeń odrzuconych.
     */
    private fun reporting(events: List<AnimalEvent>): ReportingStats {
        val submitted = events.filter { it.irzSubmittedAt != null }
        val onTime = submitted.count { event ->
            val sentOn = event.irzSubmittedAt!!.toLocalDate()
            !sentOn.isAfter(event.eventDate.plusDays(ComplianceItem.REPORTING_DAYS))
        }
        return ReportingStats(onTime = onTime, late = submitted.size - onTime)
    }

    /**
     * Określa, czy zwierzę znajduje się w stadzie, tj. czy jego ostatnie zdarzenie
     * (z pominięciem zgłoszeń odrzuconych) nie jest ubyciem ani padnięciem.
     */
    fun isInHerd(animalEvents: List<AnimalEvent>): Boolean {
        val last = animalEvents.withoutRejected()
            .maxWithOrNull(compareBy<AnimalEvent>({ it.eventDate }, { it.id }))
        return last == null || last.type !in CLOSING
    }

    /** Przypisuje zwierzę do grupy wiekowo-płciowej według stanu na dzień [today]. */
    fun ageGroup(animal: Animal, today: LocalDate): AgeGroup {
        val birth = animal.birthDate ?: return AgeGroup.UNKNOWN_AGE
        val months = ChronoUnit.MONTHS.between(birth, today)
        if (months < HerdStats.CALF_MONTHS) return AgeGroup.CALVES

        val adult = months >= HerdStats.ADULT_MONTHS
        return when (animal.sex) {
            Sex.FEMALE -> if (adult) AgeGroup.COWS else AgeGroup.HEIFERS
            Sex.MALE -> if (adult) AgeGroup.BULLS else AgeGroup.YOUNG_BULLS
            Sex.OTHER -> AgeGroup.OTHER_SEX
        }
    }

    private fun breeds(animals: List<Animal>): List<Pair<String, Int>> {
        val sorted = animals
            .groupingBy { it.breed?.trim()?.uppercase()?.ifBlank { null } ?: HerdStats.UNKNOWN_BREED }
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        if (sorted.size <= HerdStats.MAX_BREEDS) return sorted
        val head = sorted.take(HerdStats.MAX_BREEDS - 1)
        val tail = sorted.drop(HerdStats.MAX_BREEDS - 1).sumOf { it.second }
        return head + (HerdStats.OTHER_BREEDS to tail)
    }

    private fun movements(events: List<AnimalEvent>, current: YearMonth): List<MonthlyMovement> {
        val first = current.minusMonths(HerdStats.MONTHS - 1L)
        val byMonth = events
            .filter { val m = YearMonth.from(it.eventDate); !m.isBefore(first) && !m.isAfter(current) }
            .groupBy { YearMonth.from(it.eventDate) }
        return (0 until HerdStats.MONTHS).map { offset ->
            val month = first.plusMonths(offset.toLong())
            val list = byMonth[month].orEmpty()
            MonthlyMovement(
                month = month,
                births = list.count { it.type == EventType.BIRTH },
                arrivals = list.count { it.type == EventType.ARRIVAL },
                departures = list.count { it.type == EventType.DEPARTURE },
                deaths = list.count { it.type == EventType.DEATH },
            )
        }
    }
}
