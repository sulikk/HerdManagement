package com.example.herdmanagementmobile.data.sync

import com.example.herdmanagementmobile.data.remote.dto.AnimalDto
import com.example.herdmanagementmobile.data.remote.dto.DeletionDto
import com.example.herdmanagementmobile.data.remote.dto.EventDto
import com.example.herdmanagementmobile.data.remote.dto.SyncChangesDto
import com.example.herdmanagementmobile.data.remote.dto.TreatmentDto
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
        syncState = fakes.marker,
    )

    private fun changes(
        serverTime: String = "2026-09-21T12:00:00Z",
        animals: List<AnimalDto> = emptyList(),
        events: List<EventDto> = emptyList(),
        treatments: List<TreatmentDto> = emptyList(),
        deletions: List<DeletionDto> = emptyList(),
    ) = SyncChangesDto(serverTime, null, animals, events, treatments, deletions)

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

    @Test
    fun `zapisany znacznik jest oddawany backendowi jako since`() = runBlocking {
        val fakes = Fakes()
        fakes.marker.marker = "2026-09-21T10:00:00Z"
        val api = FakeApi(changes())

        SyncPuller(api, fakes.sync, fakes.animals, fakes.events, fakes.treatments, fakes.marker).pull()

        assertEquals("2026-09-21T10:00:00Z", api.lastSince)
    }
}
