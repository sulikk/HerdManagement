package com.example.herdmanagementmobile.domain.stats

import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import com.example.herdmanagementmobile.domain.model.Sex
import com.example.herdmanagementmobile.domain.model.Treatment
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Grupa wiekowo-płciowa (uproszczony podział stosowany w ewidencji bydła). */
enum class AgeGroup { CALVES, YOUNGSTOCK, COWS, BULLS, ADULTS_OTHER, UNKNOWN_AGE }

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

data class HerdStats(
    /** Zwierzęta obecnie w stadzie (bez ubyłych i padłych). */
    val inHerd: Int,
    /** Zwierzęta rozliczone — ostatnie zdarzenie to ubycie lub padnięcie. */
    val leftHerd: Int,
    val ageGroups: Map<AgeGroup, Int>,
    /** Rasy w kolejności malejącej liczebności; ogon zwinięty do [OTHER_BREEDS]. */
    val breeds: List<Pair<String, Int>>,
    /** Ostatnie [MONTHS] miesięcy, od najstarszego. */
    val movements: List<MonthlyMovement>,
    /** Zwierzęta w stadzie z trwającą karencją. */
    val animalsInWithdrawal: Int,
    val treatmentsLast12Months: Int,
    val irzStatusCounts: Map<IrzStatus, Int>,
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

object HerdStatistics {

    private val CLOSING = setOf(EventType.DEPARTURE, EventType.DEATH)

    fun compute(
        animals: List<Animal>,
        events: List<AnimalEvent>,
        treatments: List<Treatment>,
        today: LocalDate = LocalDate.now(),
    ): HerdStats {
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
            movements = movements(events, YearMonth.from(today)),
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
        )
    }

    /** Zwierzę jest w stadzie, jeśli jego najnowsze zdarzenie nie jest ubyciem ani padnięciem. */
    fun isInHerd(animalEvents: List<AnimalEvent>): Boolean {
        val last = animalEvents.maxWithOrNull(compareBy<AnimalEvent>({ it.eventDate }, { it.id }))
        return last == null || last.type !in CLOSING
    }

    fun ageGroup(animal: Animal, today: LocalDate): AgeGroup {
        val birth = animal.birthDate ?: return AgeGroup.UNKNOWN_AGE
        val months = ChronoUnit.MONTHS.between(birth, today)
        return when {
            months < HerdStats.CALF_MONTHS -> AgeGroup.CALVES
            months < HerdStats.ADULT_MONTHS -> AgeGroup.YOUNGSTOCK
            else -> when (animal.sex) {
                Sex.FEMALE -> AgeGroup.COWS
                Sex.MALE -> AgeGroup.BULLS
                Sex.OTHER -> AgeGroup.ADULTS_OTHER
            }
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
