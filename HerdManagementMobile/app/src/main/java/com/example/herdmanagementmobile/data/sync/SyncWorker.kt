package com.example.herdmanagementmobile.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.herdmanagementmobile.data.local.dao.AnimalDao
import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.dao.ListingDao
import com.example.herdmanagementmobile.data.local.dao.SyncDao
import com.example.herdmanagementmobile.data.local.entity.IdMappingEntity
import com.example.herdmanagementmobile.data.local.entity.SyncQueueEntity
import com.example.herdmanagementmobile.data.remote.HerdApiService
import com.example.herdmanagementmobile.data.remote.dto.AnimalDto
import com.example.herdmanagementmobile.data.remote.dto.ListingWriteDto
import com.example.herdmanagementmobile.data.remote.dto.TreatmentDto
import com.example.herdmanagementmobile.data.remote.dto.toDto
import com.example.herdmanagementmobile.data.remote.dto.toIrzResultDto
import com.example.herdmanagementmobile.data.remote.irzplus.IrzplusClient
import com.example.herdmanagementmobile.domain.model.IrzStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.Duration
import java.time.LocalDateTime

/**
 * Drenuje kolejkę synchronizacji (FIFO):
 * - zwierzęta, zdarzenia i leczenia trafiają do backendu aplikacji (z mapowaniem
 *   lokalne id -> zdalne); zwierzęta: tworzenie, edycja, usunięcie; leczenia:
 *   tworzenie, usunięcie; zdarzenia: tworzenie + wynik zgłoszenia IRZ,
 * - zgłoszenie do Portalu IRZplus (IRZ_SUBMIT) trafia do kolejki wyłącznie po
 *   potwierdzeniu przez użytkownika; wynik ustawia [IrzStatus] na CONFIRMED
 *   (nadano numer dokumentu) lub REJECTED (błędy walidacji) i jest przekazywany
 *   do backendu osobnym wpisem IRZ_RESULT.
 *
 * Obsługa błędów ([classifySyncError]): brak sieci → ponawianie bez limitu,
 * błąd serwera → do [SyncContract.MAX_RETRIES], błąd danych → od razu FAILED.
 * Ponowienia planuje osobne zadanie ([SyncManager.scheduleRetry]), więc nowe zmiany
 * nie czekają za backoffem. Worker zawsze kończy się sukcesem.
 *
 * Brakujące zależności (zwierzę/zdarzenie nieobecne na serwerze) są odtwarzane
 * na bieżąco z lokalnej bazy — w tym po 404, gdy serwer utracił dane.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncDao: SyncDao,
    private val eventDao: EventDao,
    private val animalDao: AnimalDao,
    private val listingDao: ListingDao,
    private val api: HerdApiService,
    private val irzplus: IrzplusClient,
    private val syncManager: SyncManager,
    private val syncPuller: SyncPuller,
    private val json: Json,
) : CoroutineWorker(context, params) {

    /** Statusy, przy ktorych ogloszenie wciaz zajmuje zwierze po stronie backendu. */
    private val ACTIVE_STATUSES = setOf("draft", "published")

    override suspend fun doWork(): Result = syncManager.mutex.withLock {
        var retryAttempt = 0

        for (entry in syncDao.pending()) {
            try {
                process(entry)
                markDone(entry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val attempt = handleError(entry, e)
                if (attempt > 0) retryAttempt = maxOf(retryAttempt, attempt)
            }
        }

        // Dopiero po opróżnieniu kolejki pobieramy zmiany z backendu — wcześniej
        // wersja z serwera byłaby starsza od tego, co czeka na wysłanie.
        pullRemoteChanges()

        if (retryAttempt > 0) {
            syncManager.scheduleRetry(Duration.ofSeconds(SyncContract.retryDelaySeconds(retryAttempt)))
        }
        Result.success()
    }

    /**
     * Dociąga zmiany z backendu (wpisy asystenta LLM, poprawki z serwera, inne urządzenie).
     *
     * Błąd pobierania nie może wywrócić całej synchronizacji — wypchnięte właśnie
     * zmiany są już na serwerze, a porcja do pobrania przyjdzie przy kolejnym
     * uruchomieniu (znacznik `since` przesuwa się dopiero po udanym zapisie).
     */
    private suspend fun pullRemoteChanges() {
        try {
            when (val result = syncPuller.pull()) {
                is SyncPuller.Result.Skipped -> Unit
                is SyncPuller.Result.Applied ->
                    if (result.total > 0) {
                        android.util.Log.i(
                            "SyncWorker",
                            "Pobrano z backendu: ${result.animals} zwierzat, " +
                                "${result.events} zdarzen, ${result.treatments} leczen, " +
                                "${result.deletions} usuniec",
                        )
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SyncWorker", "Nie udalo sie pobrac zmian z backendu", e)
        }
    }

    private suspend fun process(entry: SyncQueueEntity) {
        when (entry.entity to entry.operation) {
            SyncContract.Entity.ANIMAL to SyncContract.Operation.CREATE -> pushAnimal(entry)
            SyncContract.Entity.ANIMAL to SyncContract.Operation.UPDATE -> updateAnimal(entry)
            SyncContract.Entity.ANIMAL to SyncContract.Operation.DELETE ->
                deleteRemote(entry, SyncContract.Entity.ANIMAL) { api.deleteAnimal(it) }
            SyncContract.Entity.EVENT to SyncContract.Operation.CREATE -> pushEvent(entry)
            SyncContract.Entity.EVENT to SyncContract.Operation.IRZ_SUBMIT -> submitToIrz(entry)
            SyncContract.Entity.EVENT to SyncContract.Operation.IRZ_RESULT -> pushIrzResult(entry)
            SyncContract.Entity.TREATMENT to SyncContract.Operation.CREATE -> pushTreatment(entry)
            SyncContract.Entity.TREATMENT to SyncContract.Operation.DELETE ->
                deleteRemote(entry, SyncContract.Entity.TREATMENT) { api.deleteTreatment(it) }
            SyncContract.Entity.LISTING to SyncContract.Operation.CREATE -> pushListing(entry)
            SyncContract.Entity.LISTING to SyncContract.Operation.UPDATE -> updateListing(entry)
            SyncContract.Entity.LISTING to SyncContract.Operation.PUBLISH ->
                listingAction(entry) { api.publishListing(it) }
            SyncContract.Entity.LISTING to SyncContract.Operation.WITHDRAW ->
                listingAction(entry) { api.withdrawListing(it) }
            SyncContract.Entity.LISTING to SyncContract.Operation.MARK_SOLD ->
                listingAction(entry) { api.markListingSold(it) }
            SyncContract.Entity.LISTING to SyncContract.Operation.VERIFY -> verifyListing(entry)
            SyncContract.Entity.LISTING to SyncContract.Operation.DELETE ->
                deleteRemote(entry, SyncContract.Entity.LISTING) { api.deleteListing(it) }
            else -> error("Nieznana operacja: ${entry.entity}/${entry.operation}")
        }
    }

    // --- Zwierzęta -------------------------------------------------------

    private suspend fun pushAnimal(entry: SyncQueueEntity) {
        val localId = json.decodeFromString<AnimalDto>(entry.payload).id
        ensureRemoteAnimal(localId)
    }

    private suspend fun updateAnimal(entry: SyncQueueEntity) {
        val localId = json.decodeFromString<AnimalDto>(entry.payload).id
        // Wysyłamy aktualny stan z lokalnej bazy — najnowszy, nawet jeśli edycji było kilka.
        val current = animalDao.getById(localId)?.toDomain()?.toDto() ?: return
        val remoteId = syncDao.remoteIdFor(SyncContract.Entity.ANIMAL, localId)
        if (remoteId == null) {
            ensureRemoteAnimal(localId) // utworzenie od razu z aktualnymi danymi
            return
        }
        try {
            api.updateAnimal(remoteId, current.copy(id = remoteId))
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            // Serwer nie zna już tego zwierzęcia (np. wyczyszczona baza) — załóż je ponownie.
            syncDao.deleteMapping(SyncContract.Entity.ANIMAL, localId)
            ensureRemoteAnimal(localId)
        }
    }

    /**
     * Zdalne id zwierzęcia; jeśli go brak — tworzy zwierzę na serwerze z danych lokalnych.
     * Zwraca null, gdy zwierzę zostało usunięte lokalnie (wpis można pominąć).
     */
    private suspend fun ensureRemoteAnimal(localId: Long): Long? {
        syncDao.remoteIdFor(SyncContract.Entity.ANIMAL, localId)?.let { return it }
        val dto = animalDao.getById(localId)?.toDomain()?.toDto() ?: return null
        val remote = try {
            api.createAnimal(dto)
        } catch (e: HttpException) {
            if (e.code() != 409) throw e
            // Kolczyk już jest na serwerze — przejmij ten rekord i nadpisz go danymi z telefonu.
            val existing = api.listAnimals().firstOrNull { it.earTag == dto.earTag } ?: throw e
            api.updateAnimal(existing.id, dto.copy(id = existing.id))
        }
        syncDao.putMapping(IdMappingEntity(SyncContract.Entity.ANIMAL, localId, remote.id))
        return remote.id
    }

    /**
     * Wywołanie wymagające istnienia zwierzęcia na serwerze. Przy 404 (serwer utracił
     * zwierzę) odtwarza je i ponawia wywołanie raz. Null = zwierzę usunięte lokalnie.
     */
    private suspend fun <T> withRemoteAnimal(localAnimalId: Long, call: suspend (Long) -> T): T? {
        val remoteId = ensureRemoteAnimal(localAnimalId) ?: return null
        return try {
            call(remoteId)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            syncDao.deleteMapping(SyncContract.Entity.ANIMAL, localAnimalId)
            val recreated = ensureRemoteAnimal(localAnimalId) ?: return null
            call(recreated)
        }
    }

    /**
     * Usunięcie po stronie serwera. Brak mapowania = obiekt nigdy nie dotarł na serwer,
     * więc nie ma czego usuwać. 404 traktujemy jako sukces — stan docelowy osiągnięty.
     */
    private suspend fun deleteRemote(
        entry: SyncQueueEntity,
        entity: String,
        call: suspend (Long) -> Unit,
    ) {
        val payload = json.decodeFromString<LocalIdPayload>(entry.payload)
        val remoteId = syncDao.remoteIdFor(entity, payload.localId) ?: return
        try {
            call(remoteId)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
        }
        syncDao.deleteMapping(entity, payload.localId)
    }

    // --- Ogłoszenia sprzedażowe (zadanie 9) ------------------------------

    private suspend fun pushListing(entry: SyncQueueEntity) {
        ensureRemoteListing(localIdOf(entry))
    }

    private suspend fun updateListing(entry: SyncQueueEntity) {
        val localId = localIdOf(entry)
        val remoteId = syncDao.remoteIdFor(SyncContract.Entity.LISTING, localId)
        if (remoteId == null) {
            ensureRemoteListing(localId) // powstanie od razu z aktualną treścią
            return
        }
        val body = listingBody(localId) ?: return
        try {
            api.updateListing(remoteId, body)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            syncDao.deleteMapping(SyncContract.Entity.LISTING, localId)
            ensureRemoteListing(localId)
        }
    }

    /**
     * Akcja zmieniająca stan ogłoszenia (publikacja, wycofanie, sprzedaż).
     *
     * Publikacja trafia tu wyłącznie po potwierdzeniu przez użytkownika — kolejka
     * jedynie dowozi jego decyzję do serwera (F12).
     */
    private suspend fun listingAction(entry: SyncQueueEntity, call: suspend (Long) -> Any) {
        val localId = localIdOf(entry)
        val remoteId = ensureRemoteListing(localId) ?: return
        try {
            call(remoteId)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            syncDao.deleteMapping(SyncContract.Entity.LISTING, localId)
            ensureRemoteListing(localId)?.let { call(it) }
        }
    }

    /**
     * Weryfikacja wobec rejestru IRZplus (F14). Wynik zapisujemy od razu lokalnie,
     * żeby odznaka pojawiła się bez czekania na pobranie zmian.
     */
    private suspend fun verifyListing(entry: SyncQueueEntity) {
        val localId = localIdOf(entry)
        val remoteId = ensureRemoteListing(localId) ?: return
        val result = api.verifyListing(remoteId)
        val current = listingDao.getById(localId) ?: return
        listingDao.update(
            current.copy(
                irzVerified = result.irzVerified,
                irzVerifiedAt = LocalDateTime.now(),
                irzMessage = result.message,
            ),
        )
    }

    /** Zdalne id ogłoszenia; przy braku — zakłada je na serwerze z danych lokalnych. */
    private suspend fun ensureRemoteListing(localId: Long): Long? {
        syncDao.remoteIdFor(SyncContract.Entity.LISTING, localId)?.let { return it }
        val body = listingBody(localId) ?: return null
        val remote = try {
            api.createListing(body)
        } catch (e: HttpException) {
            if (e.code() != 409) throw e
            // Serwer ma już aktywne ogłoszenie tego zwierzęcia — przejmij je,
            // zamiast zakładać drugie (backend i tak na to nie pozwoli).
            val existing = api.getChanges(null).listings
                .firstOrNull { it.animalId == body.animalId && it.status in ACTIVE_STATUSES }
                ?: throw e
            api.updateListing(existing.id, body)
        }
        syncDao.putMapping(IdMappingEntity(SyncContract.Entity.LISTING, localId, remote.id))
        return remote.id
    }

    /** Treść ogłoszenia z lokalnej bazy, z podmienionym zdalnym id zwierzęcia. */
    private suspend fun listingBody(localId: Long): ListingWriteDto? {
        val listing = listingDao.getById(localId) ?: return null
        val remoteAnimalId = ensureRemoteAnimal(listing.animalId) ?: return null
        return ListingWriteDto(
            animalId = remoteAnimalId,
            price = listing.price,
            description = listing.description,
            photoUri = listing.photoUri,
        )
    }

    private fun localIdOf(entry: SyncQueueEntity): Long =
        json.decodeFromString<LocalIdPayload>(entry.payload).localId

    // --- Zdarzenia -------------------------------------------------------

    /** Zapis zdarzenia w backendzie aplikacji (bez zgłoszenia do IRZplus). */
    private suspend fun pushEvent(entry: SyncQueueEntity) {
        val localId = json.decodeFromString<LocalIdOnly>(entry.payload).id
        ensureRemoteEvent(localId)
    }

    /**
     * Zdalne id zdarzenia; jeśli go brak — tworzy zdarzenie na serwerze z aktualnego
     * stanu lokalnego (łącznie z wynikiem IRZ). Null = zdarzenie usunięte lokalnie.
     */
    private suspend fun ensureRemoteEvent(localId: Long): Long? {
        syncDao.remoteIdFor(SyncContract.Entity.EVENT, localId)?.let { return it }
        val event = eventDao.getById(localId)?.toDomain() ?: return null
        val created = withRemoteAnimal(event.animalId) { remoteAnimalId ->
            api.createEvent(remoteAnimalId, event.toDto().copy(animalId = remoteAnimalId))
        } ?: return null
        syncDao.putMapping(IdMappingEntity(SyncContract.Entity.EVENT, localId, created.id))
        return created.id
    }

    /** Zgłoszenie do IRZplus — w kolejce tylko po potwierdzeniu przez użytkownika. */
    private suspend fun submitToIrz(entry: SyncQueueEntity) {
        val eventId = json.decodeFromString<LocalIdPayload>(entry.payload).localId
        // Zdarzenie lub zwierzę usunięte lokalnie — nie ma czego zgłaszać.
        val event = eventDao.getById(eventId)?.toDomain() ?: return
        val animal = animalDao.getById(event.animalId)?.toDomain() ?: return

        val response = irzplus.zglosZdarzenie(event, animal)
        val accepted = response.numerDokumentu != null && response.bledy.isEmpty()
        val status = if (accepted) IrzStatus.CONFIRMED else IrzStatus.REJECTED
        val message = if (accepted) {
            response.komunikat.ifBlank { "Przyjęto przez IRZplus." }
        } else {
            // Złóż czytelny powód odrzucenia z komunikatu i opisów błędów walidacji.
            val powody = response.bledy.joinToString("; ") { it.opis }
            listOf(response.komunikat, powody).filter { it.isNotBlank() }.joinToString(" ")
                .ifBlank { "Zgłoszenie odrzucone przez IRZplus." }
        }
        eventDao.updateIrzResult(
            id = eventId,
            status = status.name,
            message = message,
            documentNumber = response.numerDokumentu,
            submittedAt = LocalDateTime.now().toString(),
        )
        syncManager.enqueueIrzResult(eventId)
    }

    /** Przekazanie wyniku zgłoszenia IRZ do backendu aplikacji. */
    private suspend fun pushIrzResult(entry: SyncQueueEntity) {
        val eventId = json.decodeFromString<LocalIdPayload>(entry.payload).localId
        val event = eventDao.getById(eventId)?.toDomain() ?: return
        val remoteId = syncDao.remoteIdFor(SyncContract.Entity.EVENT, eventId)
        if (remoteId == null) {
            ensureRemoteEvent(eventId) // tworzone od razu z wynikiem IRZ
            return
        }
        try {
            api.updateEventIrz(remoteId, event.toIrzResultDto())
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            syncDao.deleteMapping(SyncContract.Entity.EVENT, eventId)
            ensureRemoteEvent(eventId)
        }
    }

    // --- Leczenia --------------------------------------------------------

    private suspend fun pushTreatment(entry: SyncQueueEntity) {
        val dto = json.decodeFromString<TreatmentDto>(entry.payload)
        if (syncDao.remoteIdFor(SyncContract.Entity.TREATMENT, dto.id) != null) return
        val created = withRemoteAnimal(dto.animalId) { remoteAnimalId ->
            api.createTreatment(remoteAnimalId, dto.copy(animalId = remoteAnimalId))
        } ?: return
        syncDao.putMapping(IdMappingEntity(SyncContract.Entity.TREATMENT, dto.id, created.id))
    }

    // --- Stan wpisów -----------------------------------------------------

    private suspend fun markDone(entry: SyncQueueEntity) {
        syncDao.updateEntry(
            id = entry.id,
            status = SyncContract.Status.DONE,
            retries = entry.retries,
            error = null,
            updatedAt = LocalDateTime.now().toString(),
        )
    }

    /** Zapisuje błąd wpisu. Zwraca numer próby do zaplanowania ponowienia lub 0 (bez ponowienia). */
    private suspend fun handleError(entry: SyncQueueEntity, error: Exception): Int {
        val retries = entry.retries + 1
        val failed = when (classifySyncError(error)) {
            SyncErrorKind.NETWORK -> false
            SyncErrorKind.SERVER -> retries >= SyncContract.MAX_RETRIES
            SyncErrorKind.PERMANENT -> true
        }
        syncDao.updateEntry(
            id = entry.id,
            status = if (failed) SyncContract.Status.FAILED else SyncContract.Status.PENDING,
            retries = retries,
            error = describeSyncError(error),
            updatedAt = LocalDateTime.now().toString(),
        )
        if (failed && entry.operation == SyncContract.Operation.IRZ_SUBMIT) {
            // Zgłoszenie nie dotarło do IRZplus — cofnij „wysyłanie”, żeby użytkownik mógł ponowić.
            val eventId = json.decodeFromString<LocalIdPayload>(entry.payload).localId
            eventDao.updateIrzResult(
                id = eventId,
                status = IrzStatus.PENDING.name,
                message = "Nie udało się wysłać zgłoszenia do IRZplus. Spróbuj ponownie.",
                documentNumber = null,
                submittedAt = null,
            )
        }
        return if (failed) 0 else retries
    }
}
