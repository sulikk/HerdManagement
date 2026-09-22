package com.example.herdmanagementmobile.domain.search

import com.example.herdmanagementmobile.domain.model.Animal
import java.time.LocalDate

/** Po czym można uporządkować listę zwierząt. */
enum class AnimalSortField { EAR_TAG, NAME, BREED, BIRTH_DATE }

data class AnimalSort(
    val field: AnimalSortField = AnimalSortField.EAR_TAG,
    val ascending: Boolean = true,
) {
    /** Kliknięcie w aktywne kryterium odwraca kierunek, w inne — przełącza kryterium. */
    fun select(next: AnimalSortField): AnimalSort =
        if (next == field) copy(ascending = !ascending) else AnimalSort(next, ascending = true)
}

/**
 * Filtruje i porządkuje listę zwierząt na potrzeby ekranu stada.
 *
 * Przy kilkuset sztukach przewijanie płaskiej listy przestaje być praktyczne —
 * hodowca szuka konkretnej sztuki po fragmencie kolczyka, imieniu albo rasie.
 * Operacja jest czysta (bez zapytań do bazy), więc mieści się w wymaganiu N4
 * nawet dla tysiąca rekordów.
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
    // Zwierzę bez imienia, rasy czy daty urodzenia ma trafić na koniec listy —
    // niezależnie od kierunku sortowania. Puste pole na górze wygląda jak błąd.
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
        // Kolczyk rozstrzyga remisy — bez tego sztuki tej samej rasy skakałyby
        // po liście przy każdym odświeżeniu.
        .thenBy(POLISH_COLLATOR) { it.earTag }
}

// Normalizacja tekstu i porównywanie po polsku są wspólne dla wszystkich list —
// patrz TextSearch.kt.
