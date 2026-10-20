package com.example.herdmanagementmobile.ui.screens.listings

import com.example.herdmanagementmobile.data.local.relation.ListingWithAnimal
import com.example.herdmanagementmobile.domain.model.ListingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Testy filtrowania i sortowania listy ogłoszeń: dopasowanie po fragmencie numeru
 * kolczyka, wyszukiwanie bez znaków diakrytycznych i stabilność kolejności.
 */
class ListingFilterTest {

    private fun listing(
        id: Long,
        earTag: String,
        price: Double,
        status: ListingStatus = ListingStatus.PUBLISHED,
        verified: Boolean = false,
        name: String? = null,
        breed: String? = null,
        createdAt: LocalDateTime = LocalDateTime.of(2026, 9, 1, 12, 0),
    ) = ListingWithAnimal(
        listingId = id,
        animalId = id,
        price = price,
        description = null,
        status = status,
        irzVerified = verified,
        irzMessage = null,
        createdAt = createdAt,
        earTag = earTag,
        animalName = name,
        breed = breed,
        inquiryCount = 0,
    )

    private val krasula = listing(
        1, "PL004512378901", 6500.0, verified = true, name = "Krasula",
        breed = "Holsztyńsko-fryzyjska", createdAt = LocalDateTime.of(2026, 9, 1, 10, 0),
    )
    private val latka = listing(
        2, "PL004512378904", 4200.0, name = "Łatka",
        breed = "Simentalska", createdAt = LocalDateTime.of(2026, 9, 5, 10, 0),
    )
    private val grom = listing(
        3, "PL005338887538", 9100.0, verified = true, name = "Grom",
        breed = "Limousine", createdAt = LocalDateTime.of(2026, 9, 3, 10, 0),
    )

    private val all = listOf(krasula, latka, grom)

    private fun ids(result: List<ListingWithAnimal>) = result.map { it.listingId }

    @Test
    fun `szkice i opublikowane trafiaja do osobnych grup`() {
        assertTrue(ListingGroup.DRAFTS.accepts(ListingStatus.DRAFT))
        assertTrue(ListingGroup.PUBLISHED.accepts(ListingStatus.PUBLISHED))
        assertTrue(!ListingGroup.DRAFTS.accepts(ListingStatus.PUBLISHED))
    }

    @Test
    fun `sprzedane i wycofane sa razem jako zakonczone`() {
        assertTrue(ListingGroup.CLOSED.accepts(ListingStatus.SOLD))
        assertTrue(ListingGroup.CLOSED.accepts(ListingStatus.WITHDRAWN))
        assertTrue(!ListingGroup.CLOSED.accepts(ListingStatus.DRAFT))
    }

    @Test
    fun `pusty tekst nie zaweza listy`() {
        assertEquals(3, all.applyFilter(ListingFilter()).size)
    }

    @Test
    fun `fragment kolczyka wystarczy`() {
        assertEquals(listOf(3L), ids(all.applyFilter(ListingFilter(query = "533888"))))
    }

    @Test
    fun `polskie znaki nie sa wymagane`() {
        assertEquals(listOf(2L), ids(all.applyFilter(ListingFilter(query = "latka"))))
    }

    @Test
    fun `szuka po rasie`() {
        assertEquals(listOf(1L), ids(all.applyFilter(ListingFilter(query = "holsztyn"))))
    }

    @Test
    fun `filtr odznaki zostawia tylko potwierdzone w IRZplus`() {
        val result = all.applyFilter(ListingFilter(verifiedOnly = true))
        assertEquals(setOf(1L, 3L), ids(result).toSet())
    }

    @Test
    fun `filtr odznaki laczy sie z wyszukiwaniem`() {
        val filter = ListingFilter(query = "simentalska", verifiedOnly = true)
        assertEquals(emptyList<Long>(), ids(all.applyFilter(filter)))
    }

    @Test
    fun `domyslnie od najnowszych`() {
        assertEquals(listOf(2L, 3L, 1L), ids(all.applyFilter(ListingFilter())))
    }

    @Test
    fun `sortowanie po cenie rosnaco`() {
        val filter = ListingFilter(sort = ListingSort(ListingSortField.PRICE, ascending = true))
        assertEquals(listOf(2L, 1L, 3L), ids(all.applyFilter(filter)))
    }

    @Test
    fun `sortowanie po cenie malejaco`() {
        val filter = ListingFilter(sort = ListingSort(ListingSortField.PRICE, ascending = false))
        assertEquals(listOf(3L, 1L, 2L), ids(all.applyFilter(filter)))
    }

    @Test
    fun `sortowanie po kolczyku`() {
        val filter = ListingFilter(sort = ListingSort(ListingSortField.EAR_TAG, ascending = true))
        assertEquals(listOf(1L, 2L, 3L), ids(all.applyFilter(filter)))
    }

    @Test
    fun `klikniecie w aktywne kryterium odwraca kierunek`() {
        val sort = ListingSort(ListingSortField.PRICE, ascending = true)
        assertEquals(
            ListingSort(ListingSortField.PRICE, ascending = false),
            sort.select(ListingSortField.PRICE),
        )
    }

    @Test
    fun `data startuje od najnowszych a cena od najtanszych`() {
        val fromPrice = ListingSort(ListingSortField.PRICE, ascending = true)
        assertEquals(
            ListingSort(ListingSortField.NEWEST, ascending = false),
            fromPrice.select(ListingSortField.NEWEST),
        )

        val fromDate = ListingSort(ListingSortField.NEWEST, ascending = false)
        assertEquals(
            ListingSort(ListingSortField.PRICE, ascending = true),
            fromDate.select(ListingSortField.PRICE),
        )
    }
}
