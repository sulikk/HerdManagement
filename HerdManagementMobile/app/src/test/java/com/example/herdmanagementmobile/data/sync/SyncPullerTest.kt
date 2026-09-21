package com.example.herdmanagementmobile.data.sync

import com.example.herdmanagementmobile.data.remote.dto.AnimalDto
import com.example.herdmanagementmobile.data.remote.dto.DeletionDto
import com.example.herdmanagementmobile.data.remote.dto.EventDto
import com.example.herdmanagementmobile.data.remote.dto.InquiryDto
import com.example.herdmanagementmobile.data.remote.dto.ListingDto
import com.example.herdmanagementmobile.data.remote.dto.SyncChangesDto
import com.example.herdmanagementmobile.data.remote.dto.TreatmentDto
import com.example.herdmanagementmobile.domain.model.ListingStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy pobierania zmian z backendu.
 *
 * Sprawdzany jest scenariusz, który był realnym błędem: dane dopisane po stronie
 * serwera (np. przez asystenta LLM) nie pojawiały się w aplikacji, bo synchronizacja
 * działała tylko w jedną stronę. Poza tym — mapowanie identyfikatorów, bo lokalne id
 * z Room i id z backendu to dwie różne przestrzenie i pomylenie ich duplikuje rekordy.
 */
class SyncPullerTest {

    private val animalDto = AnimalDto(
        id = 100L,
        earTag = "PL004512378901",
        name = "Krasula",
        birthDate = "2021-03-14",
        breed = "Holsztyńsko-fryzyjska",
        sex = "female",
    )

    private fun puller(fakes: Fakes, changes: SyncChangesDto) = SyncPuller(
        api = FakeApi(changes),
        syncDao = fakes.sync,
        animalDao = fakes.animals,
        eventDao = fakes.events,
        treatmentDao = fakes.treatments,
        listingDao = fakes.listings,
        inquiryDao = fakes.inquiries,
        syncState = fakes.marker,
    )

    private fun changes(
        serverTime: String = "2026-09-21T12:00:00Z",
        animals: List<AnimalDto> = emptyList(),
        events: List<EventDto> = emptyList(),
        treatments: List<TreatmentDto> = emptyList(),
        listings: List<ListingDto> = emptyList(),
        inquiries: List<InquiryDto> = emptyList(),
        deletions: List<DeletionDto> = emptyList(),
    ) = SyncChangesDto(
        serverTime, null, animals, events, treatments, listings, inquiries, deletions,
    )

    @Test
    fun `zwierze z backendu trafia do lokalnej bazy`() = runBlocking {
        val fakes = Fakes()
        val result = puller(fakes, changes(animals = listOf(animalDto))).pull()

        assertTrue(result is SyncPuller.Result.Applied)
        assertEquals(1, fakes.animals.rows.size)
        assertEquals("PL004512378901", fakes.animals.rows.values.first().earTag)
    }

    @Test
    fun `zdalne id jest zapamietane w mapie`() = runBlocking {
        val fakes = Fakes()
        puller(fakes, changes(animals = listOf(animalDto))).pull()

        val localId = fakes.animals.rows.keys.first()
        assertEquals(100L, fakes.sync.remoteIdFor(SyncContract.Entity.ANIMAL, localId))
    }

    @Test
    fun `ponowne pobranie tego samego zwierzecia nie tworzy duplikatu`() = runBlocking {
        val fakes = Fakes()
        puller(fakes, changes(animals = listOf(animalDto))).pull()
        puller(fakes, changes(animals = listOf(animalDto.copy(name = "Krasula II")))).pull()

        assertEquals(1, fakes.animals.rows.size)
        assertEquals("Krasula II", fakes.animals.rows.values.first().name)
    }

    @Test
    fun `zwierze dodane lokalnie jest rozpoznane po kolczyku a nie zdublowane`() = runBlocking {
        val fakes = Fakes()
        // Zwierzę zapisane w telefonie, ale bez wpisu w mapie identyfikatorów
        // (np. po reinstalacji aplikacji). Kolczyk jest unikalny — insert by poległ.
        fakes.animals.seed(earTag = animalDto.earTag, name = "Krasula")

        puller(fakes, changes(animals = listOf(animalDto.copy(name = "Krasula z serwera")))).pull()

        assertEquals(1, fakes.animals.rows.size)
        assertEquals("Krasula z serwera", fakes.animals.rows.values.first().name)
    }

    @Test
    fun `leczenie dopisane przez asystenta trafia pod wlasciwe zwierze`() = runBlocking {
        val fakes = Fakes()
        val treatment = TreatmentDto(
            id = 500L,
            animalId = 100L,
            drug = "Penicylina prokainowa",
            treatmentDate = "2026-09-20",
            withdrawalUntil = "2026-09-28",
        )

        puller(fakes, changes(animals = listOf(animalDto), treatments = listOf(treatment))).pull()

        val animalLocalId = fakes.animals.rows.keys.first()
        assertEquals(1, fakes.treatments.rows.size)
        assertEquals(animalLocalId, fakes.treatments.rows.values.first().animalId)
        assertEquals("Penicylina prokainowa", fakes.treatments.rows.values.first().drug)
    }

    @Test
    fun `zdarzenie bez znanego zwierzecia jest odlozone i znacznik nie idzie do przodu`() = runBlocking {
        val fakes = Fakes()
        val event = EventDto(id = 700L, animalId = 999L, type = "birth", eventDate = "2026-09-18")

        val result = puller(fakes, changes(events = listOf(event))).pull() as SyncPuller.Result.Applied

        assertEquals(1, result.deferred)
        assertEquals(0, fakes.events.rows.size)
        // Znacznik zostaje pusty, więc następne pobranie przyniesie tę porcję ponownie.
        assertNull(fakes.marker.marker)
    }

    @Test
    fun `po udanym pobraniu znacznik wskazuje czas serwera`() = runBlocking {
        val fakes = Fakes()
        puller(fakes, changes(serverTime = "2026-09-21T12:34:56Z", animals = listOf(animalDto))).pull()

        assertEquals("2026-09-21T12:34:56Z", fakes.marker.marker)
    }

    @Test
    fun `niewyslane zmiany wstrzymuja pobieranie`() = runBlocking {
        val fakes = Fakes()
        fakes.sync.unsent = 1

        val result = puller(fakes, changes(animals = listOf(animalDto))).pull()

        assertEquals(SyncPuller.Result.Skipped, result)
        assertEquals(0, fakes.animals.rows.size)
        assertNull(fakes.marker.marker)
    }

    @Test
    fun `usuniete na serwerze znika lokalnie`() = runBlocking {
        val fakes = Fakes()
        puller(fakes, changes(animals = listOf(animalDto))).pull()
        val localId = fakes.animals.rows.keys.first()
        assertNotNull(fakes.sync.remoteIdFor(SyncContract.Entity.ANIMAL, localId))

        val tombstone = DeletionDto("animal", 100L, "2026-09-21T13:00:00Z")
        puller(fakes, changes(deletions = listOf(tombstone))).pull()

        assertEquals(0, fakes.animals.rows.size)
        assertNull(fakes.sync.remoteIdFor(SyncContract.Entity.ANIMAL, localId))
    }

    @Test
    fun `nagrobek nieznanego rekordu nie wywraca synchronizacji`() = runBlocking {
        val fakes = Fakes()
        val tombstone = DeletionDto("animal", 4242L, "2026-09-21T13:00:00Z")

        val result = puller(fakes, changes(deletions = listOf(tombstone))).pull()

        assertTrue(result is SyncPuller.Result.Applied)
        assertEquals("2026-09-21T12:00:00Z", fakes.marker.marker)
    }

    // --- Ogłoszenia sprzedażowe (zadanie 9) -----------------------------

    private val listingDto = ListingDto(
        id = 300L,
        animalId = 100L,
        price = 6500.0,
        description = "Krowa mleczna.",
        status = "published",
        irzVerified = true,
    )

    @Test
    fun `ogloszenie z backendu trafia pod wlasciwe zwierze`() = runBlocking {
        val fakes = Fakes()

        puller(fakes, changes(animals = listOf(animalDto), listings = listOf(listingDto))).pull()

        val animalLocalId = fakes.animals.rows.keys.first()
        assertEquals(1, fakes.listings.rows.size)
        assertEquals(animalLocalId, fakes.listings.rows.values.first().animalId)
        assertEquals(ListingStatus.PUBLISHED, fakes.listings.rows.values.first().status)
    }

    @Test
    fun `odznaka weryfikacji przychodzi z serwera`() = runBlocking {
        val fakes = Fakes()

        puller(fakes, changes(animals = listOf(animalDto), listings = listOf(listingDto))).pull()

        assertTrue(fakes.listings.rows.values.first().irzVerified)
    }

    @Test
    fun `zmiana statusu ogloszenia nie tworzy drugiego rekordu`() = runBlocking {
        val fakes = Fakes()
        puller(fakes, changes(animals = listOf(animalDto), listings = listOf(listingDto))).pull()
        puller(fakes, changes(listings = listOf(listingDto.copy(status = "sold")))).pull()

        assertEquals(1, fakes.listings.rows.size)
        assertEquals(ListingStatus.SOLD, fakes.listings.rows.values.first().status)
    }

    @Test
    fun `zapytanie kupujacego trafia pod wlasciwe ogloszenie`() = runBlocking {
        val fakes = Fakes()
        val inquiry = InquiryDto(
            id = 900L,
            listingId = 300L,
            buyer = "Jan Kowalski",
            message = "Czy sztuka jest cielna?",
        )

        puller(
            fakes,
            changes(
                animals = listOf(animalDto),
                listings = listOf(listingDto),
                inquiries = listOf(inquiry),
            ),
        ).pull()

        val listingLocalId = fakes.listings.rows.keys.first()
        assertEquals(1, fakes.inquiries.rows.size)
        assertEquals(listingLocalId, fakes.inquiries.rows.values.first().listingId)
        assertEquals("Jan Kowalski", fakes.inquiries.rows.values.first().buyer)
    }

    @Test
    fun `zapytanie bez znanego ogloszenia jest odlozone`() = runBlocking {
        val fakes = Fakes()
        val orphan = InquiryDto(id = 900L, listingId = 777L, buyer = "Jan", message = "Pytanie")

        val result = puller(fakes, changes(inquiries = listOf(orphan))).pull()
            as SyncPuller.Result.Applied

        assertEquals(1, result.deferred)
        assertEquals(0, fakes.inquiries.rows.size)
        assertNull(fakes.marker.marker)
    }

    @Test
    fun `usuniete ogloszenie znika lokalnie`() = runBlocking {
        val fakes = Fakes()
        puller(fakes, changes(animals = listOf(animalDto), listings = listOf(listingDto))).pull()
        assertEquals(1, fakes.listings.rows.size)

        val tombstone = DeletionDto("listing", 300L, "2026-09-21T13:00:00Z")
        puller(fakes, changes(deletions = listOf(tombstone))).pull()

        assertEquals(0, fakes.listings.rows.size)
    }

    @Test
    fun `zapisany znacznik jest oddawany backendowi jako since`() = runBlocking {
        val fakes = Fakes()
        fakes.marker.marker = "2026-09-21T10:00:00Z"
        val api = FakeApi(changes())

        SyncPuller(
            api, fakes.sync, fakes.animals, fakes.events, fakes.treatments,
            fakes.listings, fakes.inquiries, fakes.marker,
        ).pull()

        assertEquals("2026-09-21T10:00:00Z", api.lastSince)
    }
}
