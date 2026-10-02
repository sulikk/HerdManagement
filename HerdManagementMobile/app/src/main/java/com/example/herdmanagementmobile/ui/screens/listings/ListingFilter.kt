package com.example.herdmanagementmobile.ui.screens.listings

import com.example.herdmanagementmobile.data.local.relation.ListingWithAnimal
import com.example.herdmanagementmobile.domain.model.ListingStatus
import com.example.herdmanagementmobile.domain.search.POLISH_COLLATOR
import com.example.herdmanagementmobile.domain.search.foldForSearch

/** Grupy ogłoszeń widoczne jako zakładki — odwzorowują cykl życia ogłoszenia. */
enum class ListingGroup(val label: String) {
    DRAFTS("Szkice"),
    PUBLISHED("Opublikowane"),
    CLOSED("Zakończone"),
    ;

    fun accepts(status: ListingStatus): Boolean = when (this) {
        DRAFTS -> status == ListingStatus.DRAFT
        PUBLISHED -> status == ListingStatus.PUBLISHED
        // Sprzedane i wycofane trafiają razem: jedne i drugie są zamknięte,
        // a osobna zakładka na wycofane byłaby prawie zawsze pusta.
        CLOSED -> status == ListingStatus.SOLD || status == ListingStatus.WITHDRAWN
    }
}

enum class ListingSortField(val label: String) {
    NEWEST("Najnowsze"),
    PRICE("Cena"),
    EAR_TAG("Kolczyk"),
}

data class ListingSort(
    val field: ListingSortField = ListingSortField.NEWEST,
    val ascending: Boolean = false,
) {
    /**
     * Kliknięcie w aktywne kryterium odwraca kierunek, w inne — przełącza kryterium.
     *
     * Domyślny kierunek zależy od kryterium: przy dacie sensowne jest „od najnowszych”,
     * przy cenie i kolczyku — rosnąco.
     */
    fun select(next: ListingSortField): ListingSort =
        if (next == field) {
            copy(ascending = !ascending)
        } else {
            ListingSort(next, ascending = next != ListingSortField.NEWEST)
        }
}

data class ListingFilter(
    val query: String = "",
    val sort: ListingSort = ListingSort(),
    /** Zawęża do ogłoszeń z odznaką „dane potwierdzone w IRZplus” (F14). */
    val verifiedOnly: Boolean = false,
)

/**
 * Filtruje i porządkuje ogłoszenia w obrębie jednej zakładki.
 *
 * Czysta funkcja — ta sama logika bez zmian działa w teście jednostkowym,
 * a przy kilkuset ogłoszeniach koszt jest pomijalny wobec zapytania do bazy.
 */
fun List<ListingWithAnimal>.applyFilter(filter: ListingFilter): List<ListingWithAnimal> =
    asSequence()
        .filter { !filter.verifiedOnly || it.irzVerified }
        .filter { it.matches(filter.query) }
        .sortedWith(filter.sort.comparator())
        .toList()

private fun ListingWithAnimal.matches(query: String): Boolean {
    val needle = query.foldForSearch()
    if (needle.isEmpty()) return true
    return earTag.foldForSearch().contains(needle) ||
        animalName?.foldForSearch()?.contains(needle) == true ||
        breed?.foldForSearch()?.contains(needle) == true
}

private fun ListingSort.comparator(): Comparator<ListingWithAnimal> {
    val byValue: Comparator<ListingWithAnimal> = when (field) {
        ListingSortField.NEWEST -> compareBy { it.createdAt }
        ListingSortField.PRICE -> compareBy { it.price }
        ListingSortField.EAR_TAG -> compareBy(POLISH_COLLATOR) { it.earTag }
    }
    // Kolczyk rozstrzyga remisy, żeby kolejność nie zmieniała się przy odświeżeniu.
    return (if (ascending) byValue else byValue.reversed())
        .thenBy(POLISH_COLLATOR) { it.earTag }
}
