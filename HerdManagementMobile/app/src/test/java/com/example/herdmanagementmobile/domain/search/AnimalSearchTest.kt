package com.example.herdmanagementmobile.domain.search

import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Testy wyszukiwania i sortowania listy zwierząt, w tym dopasowania po fragmencie
 * numeru kolczyka i wyszukiwania bez polskich znaków diakrytycznych.
 */
class AnimalSearchTest {

    private fun animal(
        id: Long,
        earTag: String,
        name: String? = null,
        breed: String? = null,
        birthDate: LocalDate? = null,
    ) = Animal(
        id = id,
        earTag = earTag,
        name = name,
        birthDate = birthDate,
        breed = breed,
        sex = Sex.FEMALE,
        motherEarTag = null,
        herdId = null,
    )

    private val krasula = animal(1, "PL004512378901", "Krasula", "Holsztyńsko-fryzyjska", LocalDate.of(2021, 3, 14))
    private val latka = animal(2, "PL004512378904", "Łatka", "Holsztyńsko-fryzyjska", LocalDate.of(2023, 1, 5))
    private val mruga = animal(3, "PL005338887538", "Mruga", "Simentalska", LocalDate.of(2024, 6, 30))
    private val bezDanych = animal(4, "PL009999999999")

    private val stado = listOf(krasula, latka, mruga, bezDanych)

    private fun ids(result: List<Animal>) = result.map(Animal::id)

    @Test
    fun `pusty tekst zwraca cale stado`() {
        assertEquals(4, stado.searchAndSort("", AnimalSort()).size)
    }

    @Test
    fun `fragment kolczyka wystarczy do znalezienia sztuki`() {
        val result = stado.searchAndSort("533888", AnimalSort())
        assertEquals(listOf(3L), ids(result))
    }

    @Test
    fun `szuka po imieniu`() {
        assertEquals(listOf(1L), ids(stado.searchAndSort("krasula", AnimalSort())))
    }

    @Test
    fun `szuka po rasie`() {
        assertEquals(listOf(1L, 2L), ids(stado.searchAndSort("holsztyn", AnimalSort())))
    }

    @Test
    fun `polskie znaki nie sa wymagane`() {
        // Zapytanie bez znaków diakrytycznych dopasowuje „Łatka”.
        assertEquals(listOf(2L), ids(stado.searchAndSort("latka", AnimalSort())))
    }

    @Test
    fun `wielkosc liter i spacje nie maja znaczenia`() {
        assertEquals(listOf(3L), ids(stado.searchAndSort("  MRUGA ", AnimalSort())))
    }

    @Test
    fun `brak dopasowania daje pusta liste`() {
        assertEquals(emptyList<Long>(), ids(stado.searchAndSort("burek", AnimalSort())))
    }

    @Test
    fun `domyslnie sortuje po kolczyku rosnaco`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(stado.searchAndSort("", AnimalSort())))
    }

    @Test
    fun `odwrocenie kierunku odwraca kolejnosc`() {
        val sort = AnimalSort(AnimalSortField.EAR_TAG, ascending = false)
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(stado.searchAndSort("", sort)))
    }

    @Test
    fun `sortowanie po imieniu uwzglednia polski alfabet`() {
        // Polski porządek alfabetyczny: „Ł” między „L” a „M”.
        val sort = AnimalSort(AnimalSortField.NAME)
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(stado.searchAndSort("", sort)))
    }

    @Test
    fun `sztuki bez imienia sa na koncu takze przy odwroconym sortowaniu`() {
        val rosnaco = stado.searchAndSort("", AnimalSort(AnimalSortField.NAME, ascending = true))
        val malejaco = stado.searchAndSort("", AnimalSort(AnimalSortField.NAME, ascending = false))

        assertEquals(4L, ids(rosnaco).last())
        assertEquals(4L, ids(malejaco).last())
    }

    @Test
    fun `sortowanie po dacie urodzenia idzie od najstarszej`() {
        val sort = AnimalSort(AnimalSortField.BIRTH_DATE)
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(stado.searchAndSort("", sort)))
    }

    @Test
    fun `sortowanie po rasie rozstrzyga remisy kolczykiem`() {
        val sort = AnimalSort(AnimalSortField.BREED)
        // Przy równej rasie o kolejności decyduje numer kolczyka.
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(stado.searchAndSort("", sort)))
    }

    @Test
    fun `klikniecie w aktywne kryterium odwraca kierunek`() {
        val sort = AnimalSort(AnimalSortField.EAR_TAG, ascending = true)
        assertEquals(AnimalSort(AnimalSortField.EAR_TAG, ascending = false), sort.select(AnimalSortField.EAR_TAG))
    }

    @Test
    fun `wybor innego kryterium zaczyna od rosnaco`() {
        val sort = AnimalSort(AnimalSortField.EAR_TAG, ascending = false)
        assertEquals(AnimalSort(AnimalSortField.BREED, ascending = true), sort.select(AnimalSortField.BREED))
    }
}
