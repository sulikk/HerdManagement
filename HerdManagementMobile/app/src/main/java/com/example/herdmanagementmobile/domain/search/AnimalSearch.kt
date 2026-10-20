package com.example.herdmanagementmobile.domain.search

import com.example.herdmanagementmobile.domain.model.Animal
import java.time.LocalDate

/** Kryterium sortowania listy zwierząt. */
enum class AnimalSortField { EAR_TAG, NAME, BREED, BIRTH_DATE }

/** Kryterium i kierunek sortowania listy zwierząt. */
data class AnimalSort(
    val field: AnimalSortField = AnimalSortField.EAR_TAG,
    val ascending: Boolean = true,
) {
    /** Wybór aktywnego kryterium odwraca kierunek; wybór innego ustawia je rosnąco. */
    fun select(next: AnimalSortField): AnimalSort =
        if (next == field) copy(ascending = !ascending) else AnimalSort(next, ascending = true)
}

/**
 * Filtruje listę zwierząt według fragmentu numeru kolczyka, imienia lub rasy
 * i sortuje ją według [sort]. Operacja jest wykonywana w pamięci, bez zapytań do bazy
 * (wymaganie N4).
 */
fun List<Animal>.searchAndSort(query: String, sort: AnimalSort): List<Animal> =
    filter { it.matches(query) }.sortedWith(sort.comparator())

private fun Animal.matches(query: String): Boolean {
    val needle = query.foldForSearch()
    if (needle.isEmpty()) return true
    return earTag.foldForSearch().contains(needle) ||
        name?.foldForSearch()?.contains(needle) == true ||
        breed?.foldForSearch()?.contains(needle) == true
}

private fun AnimalSort.comparator(): Comparator<Animal> {
    // Rekordy bez wartości sortowanego pola trafiają na koniec niezależnie od kierunku.
    val missing: (Animal) -> Boolean = when (field) {
        AnimalSortField.EAR_TAG -> { _ -> false }
        AnimalSortField.NAME -> { animal -> animal.name.isNullOrBlank() }
        AnimalSortField.BREED -> { animal -> animal.breed.isNullOrBlank() }
        AnimalSortField.BIRTH_DATE -> { animal -> animal.birthDate == null }
    }

    val byValue: Comparator<Animal> = when (field) {
        AnimalSortField.EAR_TAG -> compareBy(POLISH_COLLATOR) { it.earTag }
        AnimalSortField.NAME -> compareBy(POLISH_COLLATOR) { it.name.orEmpty() }
        AnimalSortField.BREED -> compareBy(POLISH_COLLATOR) { it.breed.orEmpty() }
        AnimalSortField.BIRTH_DATE -> compareBy { it.birthDate ?: LocalDate.MIN }
    }

    return compareBy<Animal> { if (missing(it)) 1 else 0 }
        .then(if (ascending) byValue else byValue.reversed())
        // Numer kolczyka zapewnia stabilną kolejność przy równych wartościach.
        .thenBy(POLISH_COLLATOR) { it.earTag }
}
