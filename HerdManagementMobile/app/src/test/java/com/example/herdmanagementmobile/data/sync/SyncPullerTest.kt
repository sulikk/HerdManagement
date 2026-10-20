package com.example.herdmanagementmobile.data.sync

import com.example.herdmanagementmobile.data.local.entity.IdMappingEntity
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
 * Testy pobierania zmian z backendu: zapis danych wprowadzonych po stronie serwera,
 * mapowanie identyfikatorów lokalnych i zdalnych oraz uzgadnianie stanu z pełną migawką.
 */
class SyncPullerTest {

    private companion object {
        const val SERVER = "http://192.168.0.132:8000/"
    }

    private val animalDto = AnimalDto(
        id = 100L,
        earTag = "PL004512378901",
        name = "Krasula",
        birthDate = "2021-03-14",
        breed = "Holsztyńsko-fryzyjska",
        sex = "female",
    )

    private fun puller(
        fakes: Fakes,
        changes: SyncChangesDto,
        baseUrl: String = SERVER,
    ) = SyncPuller(
        api = FakeApi(changes),
        syncDao = fakes.sync,
        animalDao = fakes.animals,
        eventDao = fakes.events,
        treatmentDao = fakes.treatments,
        listingDao = fakes.listings,
        inquiryDao = fakes.inquiries,
        syncState = fakes.marker,
        baseUrl = baseUrl,
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
        // Zwierzę zapisane lokalnie bez mapowania identyfikatora (np. po reinstalacji).
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
        // Znacznik pozostaje pusty, więc porcja zostanie pobrana ponownie.
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
        fakes.marker.server = SERVER
        fakes.marker.marker = "2026-09-21T10:00:00Z"
        val api = FakeApi(changes())

        SyncPuller(
            api, fakes.sync, fakes.animals, fakes.events, fakes.treatments,
            fakes.listings, fakes.inquiries, fakes.marker, SERVER,
        ).pull()

        assertEquals("2026-09-21T10:00:00Z", api.lastSince)
    }

    @Test
    fun `nagrobek po starym zwierzeciu nie kasuje nowego o tym samym id`() = runBlocking {
        val fakes = Fakes()
        // Backend może ponownie nadać identyfikator usuniętego wiersza, więc migawka
        // zawiera zwierzę o id 100 oraz znacznik usunięcia animal/100 jego poprzednika.
        val tombstone = DeletionDto("animal", 100L, "2026-09-01T10:00:00Z")

        val result = puller(
            fakes,
            changes(animals = listOf(animalDto), deletions = listOf(tombstone)),
        ).pull()

        assertTrue(result is SyncPuller.Result.Applied)
        assertEquals(1, fakes.animals.rows.size)
        assertEquals("PL004512378901", fakes.animals.rows.values.first().earTag)
    }

    @Test
    fun `nagrobek po ogloszeniu obecnym w tej samej porcji jest pomijany`() = runBlocking {
        val fakes = Fakes()
        val tombstone = DeletionDto("listing", 300L, "2026-09-01T10:00:00Z")

        puller(
            fakes,
            changes(
                animals = listOf(animalDto),
                listings = listOf(listingDto),
                deletions = listOf(tombstone),
            ),
        ).pull()

        assertEquals(1, fakes.listings.rows.size)
    }

    @Test
    fun `pelna migawka kasuje lokalne zwierze ktorego nie ma na serwerze`() = runBlocking {
        val fakes = Fakes()
        puller(fakes, changes(animals = listOf(animalDto))).pull()
        val localId = fakes.animals.rows.keys.first()

        // Migawka nie zawiera zwierzęcia, mimo braku znacznika usunięcia.
        val other = animalDto.copy(id = 101L, earTag = "PL004512378902", name = "Mucha")
        fakes.marker.marker = null
        val result = puller(fakes, changes(animals = listOf(other))).pull()
            as SyncPuller.Result.Applied

        assertEquals(1, result.reconciled)
        assertNull(fakes.animals.rows[localId])
        assertEquals(1, fakes.animals.rows.size)
        assertEquals("Mucha", fakes.animals.rows.values.first().name)
    }

    @Test
    fun `pelna migawka nie rusza zwierzecia ktore jeszcze nie bylo na serwerze`() = runBlocking {
        val fakes = Fakes()
        // Rekord lokalny bez mapowania oczekuje na wysłanie.
        val localId = fakes.animals.seed(earTag = "PL004512378903", name = "Lokalna")

        val result = puller(fakes, changes(animals = listOf(animalDto))).pull()
            as SyncPuller.Result.Applied

        assertEquals(0, result.reconciled)
        assertNotNull(fakes.animals.rows[localId])
        assertEquals(2, fakes.animals.rows.size)
    }

    @Test
    fun `pusta odpowiedz serwera nie kasuje lokalnych danych`() = runBlocking {
        val fakes = Fakes()
        puller(fakes, changes(animals = listOf(animalDto))).pull()
        assertEquals(1, fakes.animals.rows.size)

        // Pusta migawka nie może powodować usunięcia danych lokalnych.
        fakes.marker.marker = null
        puller(fakes, changes()).pull()

        assertEquals(1, fakes.animals.rows.size)
    }

    @Test
    fun `zmiana adresu serwera wymusza pelna migawke`() = runBlocking {
        val fakes = Fakes()
        puller(fakes, changes(animals = listOf(animalDto))).pull()
        assertEquals("2026-09-21T12:00:00Z", fakes.marker.marker)

        // Zmiana adresu serwera unieważnia znacznik pobrania.
        val api = FakeApi(changes(animals = listOf(animalDto)))
        SyncPuller(
            api, fakes.sync, fakes.animals, fakes.events, fakes.treatments,
            fakes.listings, fakes.inquiries, fakes.marker, "http://192.168.0.200:8000/",
        ).pull()

        assertNull(api.lastSince)
    }

    @Test
    fun `stare powiazanie nie wskrzesza usunietego zdarzenia`() = runBlocking {
        // Nowe zdarzenie otrzymuje identyfikator zdalny usuniętego poprzednika; nieaktualne
        // mapowanie nie może spowodować przywrócenia usuniętego wiersza.
        val fakes = Fakes()
        fakes.sync.putMapping(IdMappingEntity(SyncContract.Entity.EVENT, localId = 77L, remoteId = 5L))
        val event = EventDto(id = 5L, animalId = 100L, type = "birth", eventDate = "2026-09-18")

        puller(fakes, changes(animals = listOf(animalDto), events = listOf(event))).pull()

        assertEquals(1, fakes.events.rows.size)
        assertNull("wiersz 77 byl usuniety i ma taki zostac", fakes.events.rows[77L])
        // Mapowanie wskazuje istniejący wiersz, a poprzednie zostało usunięte.
        val localId = fakes.events.rows.keys.first()
        assertEquals(localId, fakes.sync.localIdFor(SyncContract.Entity.EVENT, 5L))
        assertEquals(1, fakes.sync.mappingCount(SyncContract.Entity.EVENT))
    }

    @Test
    fun `jedno zdalne id nalezy do jednego rekordu lokalnego`() = runBlocking {
        val fakes = Fakes()
        fakes.sync.putMapping(IdMappingEntity(SyncContract.Entity.ANIMAL, localId = 1L, remoteId = 9L))
        fakes.sync.putMapping(IdMappingEntity(SyncContract.Entity.ANIMAL, localId = 2L, remoteId = 9L))

        assertEquals(1, fakes.sync.mappingCount(SyncContract.Entity.ANIMAL))
        assertEquals(2L, fakes.sync.localIdFor(SyncContract.Entity.ANIMAL, 9L))
        assertNull(fakes.sync.remoteIdFor(SyncContract.Entity.ANIMAL, 1L))
    }

    @Test
    fun `stare powiazanie nie wskrzesza usunietego leczenia`() = runBlocking {
        val fakes = Fakes()
        fakes.sync.putMapping(
            IdMappingEntity(SyncContract.Entity.TREATMENT, localId = 55L, remoteId = 8L),
        )
        val treatment = TreatmentDto(
            id = 8L,
            animalId = 100L,
            drug = "Penicylina prokainowa",
            treatmentDate = "2026-09-20",
            withdrawalUntil = "2026-09-28",
        )

        puller(fakes, changes(animals = listOf(animalDto), treatments = listOf(treatment))).pull()

        assertEquals(1, fakes.treatments.rows.size)
        assertNull(fakes.treatments.rows[55L])
    }
}
