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
import com.example.herdmanagementmobile.domain.model.ListingStatus
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
 * Przetwarza kolejkę synchronizacji w kolejności FIFO.
 *
 * - Zwierzęta, zdarzenia, leczenia i ogłoszenia są przesyłane do backendu aplikacji
 *   z zachowaniem mapowania identyfikatorów lokalnych na zdalne.
 * - Zgłoszenie do IRZplus (IRZ_SUBMIT) trafia do kolejki wyłącznie po potwierdzeniu
 *   przez użytkownika. Wynik ustawia [IrzStatus] na CONFIRMED lub REJECTED i jest
 *   przekazywany do backendu osobnym wpisem IRZ_RESULT.
 *
 * Obsługa błędów ([classifySyncError]): błąd sieci jest ponawiany bez limitu, błąd
 * serwera do [SyncContract.MAX_RETRIES] razy, a błąd danych od razu oznacza wpis jako
 * FAILED. Ponowienia planuje osobne zadanie ([SyncManager.scheduleRetry]). Rekordy
 * nadrzędne nieobecne na serwerze (również po odpowiedzi 404) są odtwarzane z bazy lokalnej.
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
    private val syncState: SyncStateRepository,
    private val json: Json,
) : CoroutineWorker(context, params) {

    /** Statusy, w których ogłoszenie blokuje zwierzę po stronie backendu. */
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

        // Pobieranie następuje po wysłaniu kolejki, aby wersja serwerowa nie nadpisała
        // nowszych zmian lokalnych.
        pullRemoteChanges()

        if (retryAttempt > 0) {
            syncManager.scheduleRetry(Duration.ofSeconds(SyncContract.retryDelaySeconds(retryAttempt)))
        }
        Result.success()
    }

    /**
     * Pobiera zmiany wprowadzone po stronie serwera (m.in. przez asystenta LLM).
     *
     * Błąd pobierania nie przerywa synchronizacji, ponieważ znacznik `since` jest
     * przesuwany dopiero po udanym zapisie i porcja zostanie pobrana przy kolejnym
     * uruchomieniu. Wynik jest zapisywany w [SyncStateRepository] i prezentowany
     * użytkownikowi.
     */
    private suspend fun pullRemoteChanges() {
        try {
            when (val result = syncPuller.pull()) {
                is SyncPuller.Result.Skipped -> Unit
                is SyncPuller.Result.Applied -> {
                    syncState.recordPullSuccess()
                    if (result.total > 0) {
                        android.util.Log.i(
                            "SyncWorker",
                            "Pobrano z backendu: ${result.animals} zwierzat, " +
                                "${result.events} zdarzen, ${result.treatments} leczen, " +
                                "${result.deletions} usuniec, " +
                                "${result.reconciled} skasowanych lokalnie",
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SyncWorker", "Nie udalo sie pobrac zmian z backendu", e)
            syncState.recordPullFailure(describeSyncError(e))
        }
    }

    private suspend fun process(entry: SyncQueueEntity) {
        when (entry.entity to entry.operation) {
            SyncContract.Entity.ANIMAL to SyncContract.Operation.CREATE -> pushAnimal(entry)
            SyncContract.Entity.ANIMAL to SyncContract.Operation.UPDATE -> updateAnimal(entry)
            SyncContract.Entity.ANIMAL to SyncContract.Operation.DELETE ->
                deleteRemote(entry, SyncContract.Entity.ANIMAL) { api.deleteAnimal(it) }
            SyncContract.Entity.EVENT to SyncContract.Operation.CREATE -> pushEvent(entry)
            SyncContract.Entity.EVENT to SyncContract.Operation.DELETE ->
                deleteRemote(entry, SyncContract.Entity.EVENT) { api.deleteEvent(it) }
            SyncContract.Entity.EVENT to SyncContract.Operation.IRZ_SUBMIT -> submitToIrz(entry)
            SyncContract.Entity.EVENT to SyncContract.Operation.IRZ_RESULT -> pushIrzResult(entry)
            SyncContract.Entity.TREATMENT to SyncContract.Operation.CREATE -> pushTreatment(entry)
            SyncContract.Entity.TREATMENT to SyncContract.Operation.DELETE ->
                deleteRemote(entry, SyncContract.Entity.TREATMENT) { api.deleteTreatment(it) }
            SyncContract.Entity.LISTING to SyncContract.Operation.CREATE -> pushListing(entry)
            SyncContract.Entity.LISTING to SyncContract.Operation.UPDATE -> updateListing(entry)
            SyncContract.Entity.LISTING to SyncContract.Operation.PUBLISH -> publishListing(entry)
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

    private suspend fun pushAnimal(entry: SyncQueueEntity) {
        val localId = json.decodeFromString<AnimalDto>(entry.payload).id
        ensureRemoteAnimal(localId)
    }

    private suspend fun updateAnimal(entry: SyncQueueEntity) {
        val localId = json.decodeFromString<AnimalDto>(entry.payload).id
        // Wysyłany jest bieżący stan rekordu, co scala kilka kolejnych edycji w jedną.
        val current = animalDao.getById(localId)?.toDomain()?.toDto() ?: return
        val remoteId = syncDao.remoteIdFor(SyncContract.Entity.ANIMAL, localId)
        if (remoteId == null) {
            ensureRemoteAnimal(localId)
            return
        }
        try {
            api.updateAnimal(remoteId, current.copy(id = remoteId))
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            syncDao.deleteMapping(SyncContract.Entity.ANIMAL, localId)
            ensureRemoteAnimal(localId)
        }
    }

    /**
     * Zwraca zdalny identyfikator zwierzęcia, w razie potrzeby tworząc rekord na serwerze.
     *
     * @return identyfikator zdalny lub `null`, gdy zwierzę zostało usunięte lokalnie.
     */
    private suspend fun ensureRemoteAnimal(localId: Long): Long? {
        syncDao.remoteIdFor(SyncContract.Entity.ANIMAL, localId)?.let { return it }
        val dto = animalDao.getById(localId)?.toDomain()?.toDto() ?: return null
        val remote = try {
            api.createAnimal(dto)
        } catch (e: HttpException) {
            if (e.code() != 409) throw e
            // Rekord o tym kolczyku już istnieje na serwerze — zostaje przejęty i nadpisany.
            val existing = api.listAnimals().firstOrNull { it.earTag == dto.earTag } ?: throw e
            api.updateAnimal(existing.id, dto.copy(id = existing.id))
        }
        syncDao.putMapping(IdMappingEntity(SyncContract.Entity.ANIMAL, localId, remote.id))
        return remote.id
    }

    /**
     * Wykonuje [call] dla zdalnego identyfikatora zwierzęcia. Po odpowiedzi 404 odtwarza
     * zwierzę na serwerze i jednokrotnie ponawia wywołanie.
     *
     * @return wynik wywołania lub `null`, gdy zwierzę zostało usunięte lokalnie.
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
     * Usuwa rekord po stronie serwera. Brak mapowania oznacza, że rekord nie został
     * nigdy wysłany. Odpowiedź 404 jest traktowana jako sukces.
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

    private suspend fun pushListing(entry: SyncQueueEntity) {
        ensureRemoteListing(localIdOf(entry))
    }

    private suspend fun updateListing(entry: SyncQueueEntity) {
        val localId = localIdOf(entry)
        val remoteId = syncDao.remoteIdFor(SyncContract.Entity.LISTING, localId)
        if (remoteId == null) {
            ensureRemoteListing(localId)
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

    /** Wykonuje akcję zmieniającą stan ogłoszenia (wycofanie, oznaczenie jako sprzedane). */
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
     * Publikuje ogłoszenie zatwierdzone przez użytkownika (wymagania F12 i F14).
     *
     * Backend ponownie weryfikuje dane w rejestrze i może odmówić publikacji (HTTP 409).
     * Ponieważ status lokalny został zmieniony optymistycznie, odmowa przywraca szkic
     * wraz z komunikatem. Odmowa ma charakter trwały, dlatego wpis kolejki nie jest ponawiany.
     */
    private suspend fun publishListing(entry: SyncQueueEntity) {
        val localId = localIdOf(entry)
        val remoteId = ensureRemoteListing(localId) ?: return
        try {
            api.publishListing(remoteId)
        } catch (e: HttpException) {
            if (e.code() != 409) throw e
            val current = listingDao.getById(localId) ?: return
            listingDao.update(
                current.copy(
                    status = ListingStatus.DRAFT,
                    publishedAt = null,
                    irzVerified = false,
                    irzMessage = detailOf(e) ?: "Rejestr IRZplus odmówił publikacji.",
                ),
            )
        }
    }

    /** Odczytuje komunikat z odpowiedzi błędu FastAPI w formacie `{"detail": "..."}`. */
    private fun detailOf(e: HttpException): String? = runCatching {
        val body = e.response()?.errorBody()?.string().orEmpty()
        json.decodeFromString<ErrorDetail>(body).detail?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * Zleca weryfikację ogłoszenia w rejestrze IRZplus (wymaganie F14). Wynik jest
     * zapisywany lokalnie bez oczekiwania na kolejne pobranie zmian.
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

    /** Zwraca zdalny identyfikator ogłoszenia, w razie potrzeby tworząc rekord na serwerze. */
    private suspend fun ensureRemoteListing(localId: Long): Long? {
        syncDao.remoteIdFor(SyncContract.Entity.LISTING, localId)?.let { return it }
        val body = listingBody(localId) ?: return null
        val remote = try {
            api.createListing(body)
        } catch (e: HttpException) {
            if (e.code() != 409) throw e
            // Backend dopuszcza jedno aktywne ogłoszenie na zwierzę — istniejące zostaje przejęte.
            val existing = api.getChanges(null).listings
                .firstOrNull { it.animalId == body.animalId && it.status in ACTIVE_STATUSES }
                ?: throw e
            api.updateListing(existing.id, body)
        }
        syncDao.putMapping(IdMappingEntity(SyncContract.Entity.LISTING, localId, remote.id))
        return remote.id
    }

    /** Buduje treść żądania z lokalnego ogłoszenia, podstawiając zdalny identyfikator zwierzęcia. */
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

    /** Zapisuje zdarzenie w backendzie aplikacji, bez zgłaszania go do IRZplus. */
    private suspend fun pushEvent(entry: SyncQueueEntity) {
        val localId = json.decodeFromString<LocalIdOnly>(entry.payload).id
        ensureRemoteEvent(localId)
    }

    /**
     * Zwraca zdalny identyfikator zdarzenia, w razie potrzeby tworząc rekord na serwerze
     * z bieżącego stanu lokalnego (wraz z wynikiem zgłoszenia IRZ).
     *
     * @return identyfikator zdalny lub `null`, gdy zdarzenie zostało usunięte lokalnie.
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

    /** Wysyła zgłoszenie zdarzenia do IRZplus i zapisuje wynik lokalnie. */
    private suspend fun submitToIrz(entry: SyncQueueEntity) {
        val eventId = json.decodeFromString<LocalIdPayload>(entry.payload).localId
        val event = eventDao.getById(eventId)?.toDomain() ?: return
        val animal = animalDao.getById(event.animalId)?.toDomain() ?: return

        val response = irzplus.zglosZdarzenie(event, animal)
        val accepted = response.numerDokumentu != null && response.bledy.isEmpty()
        val status = if (accepted) IrzStatus.CONFIRMED else IrzStatus.REJECTED
        val message = if (accepted) {
            response.komunikat.ifBlank { "Przyjęto przez IRZplus." }
        } else {
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

    /** Przekazuje wynik zgłoszenia IRZ do backendu aplikacji. */
    private suspend fun pushIrzResult(entry: SyncQueueEntity) {
        val eventId = json.decodeFromString<LocalIdPayload>(entry.payload).localId
        val event = eventDao.getById(eventId)?.toDomain() ?: return
        val remoteId = syncDao.remoteIdFor(SyncContract.Entity.EVENT, eventId)
        if (remoteId == null) {
            ensureRemoteEvent(eventId)
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

    private suspend fun pushTreatment(entry: SyncQueueEntity) {
        val dto = json.decodeFromString<TreatmentDto>(entry.payload)
        if (syncDao.remoteIdFor(SyncContract.Entity.TREATMENT, dto.id) != null) return
        val created = withRemoteAnimal(dto.animalId) { remoteAnimalId ->
            api.createTreatment(remoteAnimalId, dto.copy(animalId = remoteAnimalId))
        } ?: return
        syncDao.putMapping(IdMappingEntity(SyncContract.Entity.TREATMENT, dto.id, created.id))
    }

    private suspend fun markDone(entry: SyncQueueEntity) {
        syncDao.updateEntry(
            id = entry.id,
            status = SyncContract.Status.DONE,
            retries = entry.retries,
            error = null,
            updatedAt = LocalDateTime.now().toString(),
        )
    }

    /**
     * Zapisuje błąd wpisu kolejki.
     *
     * @return numer próby, dla której należy zaplanować ponowienie, lub 0, gdy wpis nie będzie ponawiany.
     */
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
            // Przywrócenie stanu PENDING umożliwia użytkownikowi ponowne wysłanie zgłoszenia.
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
