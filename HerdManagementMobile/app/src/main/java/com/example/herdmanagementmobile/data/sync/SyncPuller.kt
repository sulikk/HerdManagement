package com.example.herdmanagementmobile.data.sync

import com.example.herdmanagementmobile.data.local.dao.AnimalDao
import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.dao.InquiryDao
import com.example.herdmanagementmobile.data.local.dao.ListingDao
import com.example.herdmanagementmobile.data.local.dao.SyncDao
import com.example.herdmanagementmobile.data.local.dao.TreatmentDao
import com.example.herdmanagementmobile.data.local.entity.AnimalEntity
import com.example.herdmanagementmobile.data.local.entity.EventEntity
import com.example.herdmanagementmobile.data.local.entity.IdMappingEntity
import com.example.herdmanagementmobile.data.local.entity.InquiryEntity
import com.example.herdmanagementmobile.data.local.entity.ListingEntity
import com.example.herdmanagementmobile.data.local.entity.TreatmentEntity
import com.example.herdmanagementmobile.data.remote.HerdApiService
import com.example.herdmanagementmobile.data.remote.dto.AnimalDto
import com.example.herdmanagementmobile.data.remote.dto.DeletionDto
import com.example.herdmanagementmobile.data.remote.dto.EventDto
import com.example.herdmanagementmobile.data.remote.dto.InquiryDto
import com.example.herdmanagementmobile.data.remote.dto.ListingDto
import com.example.herdmanagementmobile.data.remote.dto.SyncChangesDto
import com.example.herdmanagementmobile.data.remote.dto.TreatmentDto
import com.example.herdmanagementmobile.data.remote.dto.toDomain
import com.example.herdmanagementmobile.di.BaseUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pobiera z backendu zmiany, których telefon sam nie wprowadził, i wgrywa je do Room.
 *
 * Bez tego kroku synchronizacja jest jednokierunkowa: aplikacja wypycha swoje wpisy,
 * ale nie widzi niczego, co powstało po drugiej stronie — a po drugiej stronie zapisuje
 * choćby asystent LLM (narzędzia `dodaj_zwierze`, `dodaj_zdarzenie`, `dodaj_leczenie`
 * działają na bazie backendu). Hodowca podejmuje decyzje na podstawie ekranu telefonu,
 * więc rozjazd między telefonem a serwerem jest błędem, nie niedogodnością.
 *
 * **Polityka konfliktów.** Pobieranie rusza dopiero, gdy kolejka wysyłkowa jest pusta.
 * Dopóki cokolwiek czeka na wysłanie, lokalna wersja jest nowsza i to ona wygrywa;
 * gdy kolejka się opróżni, wersja z serwera nadpisuje lokalną. Dzięki temu nigdy nie
 * kasujemy zmiany, której użytkownik nie zdążył wypchnąć, a stan i tak się zbiega.
 *
 * **Pełna migawka** (pierwsze uruchomienie, reinstalacja, zmiana adresu serwera) nie
 * tylko dowozi rekordy, ale też uzgadnia stan w drugą stronę: lokalny rekord, który
 * ma już swój odpowiednik na serwerze, a w migawce go nie ma, został tam usunięty
 * i znika także z telefonu ([reconcile]). Bez tego kroku „zsynchronizowano” mogło
 * znaczyć „telefon i serwer pokazują dwa różne stada”.
 */
@Singleton
class SyncPuller @Inject constructor(
    private val api: HerdApiService,
    private val syncDao: SyncDao,
    private val animalDao: AnimalDao,
    private val eventDao: EventDao,
    private val treatmentDao: TreatmentDao,
    private val listingDao: ListingDao,
    private val inquiryDao: InquiryDao,
    private val syncState: SyncMarkerStore,
    @BaseUrl private val baseUrl: String,
) {

    sealed interface Result {
        /** Kolejka wysyłkowa nie jest pusta — pobieranie odłożone do następnego razu. */
        data object Skipped : Result

        data class Applied(
            val animals: Int,
            val events: Int,
            val treatments: Int,
            val listings: Int,
            val inquiries: Int,
            val deletions: Int,
            val deferred: Int,
            /** Rekordy skasowane lokalnie, bo znikły z pełnej migawki. */
            val reconciled: Int = 0,
        ) : Result {
            val total: Int
                get() = animals + events + treatments + listings + inquiries +
                    deletions + reconciled
        }
    }

    suspend fun pull(): Result {
        if (syncDao.unsentCount() > 0) return Result.Skipped

        // Znacznik wystawiony przez inny backend nie opisuje tego, co widzi ten backend.
        syncState.bindToServer(baseUrl)

        val since = syncState.lastPullMarker()
        val fullSnapshot = since == null
        val changes = api.getChanges(since)
        if (changes.isEmpty) {
            syncState.saveLastPullMarker(changes.serverTime)
            return Result.Applied(0, 0, 0, 0, 0, 0, 0)
        }

        var deferred = 0
        // Kolejność ma znaczenie: zdarzenie, leczenie i ogłoszenie wskazują zwierzę,
        // a zapytanie wskazuje ogłoszenie. Rodzic musi trafić do bazy (i do mapy
        // identyfikatorów) przed dzieckiem.
        changes.animals.forEach { applyAnimal(it) }
        changes.events.forEach { if (!applyEvent(it)) deferred++ }
        changes.treatments.forEach { if (!applyTreatment(it)) deferred++ }
        changes.listings.forEach { if (!applyListing(it)) deferred++ }
        changes.inquiries.forEach { if (!applyInquiry(it)) deferred++ }

        val live = liveRemoteIds(changes)
        changes.deletions.forEach { applyDeletion(it, live) }

        // Uzgadniamy stan tylko wobec migawki, która cokolwiek zawiera — całkiem
        // pusta odpowiedź na pełne pytanie to równie dobrze backend z wyczyszczoną
        // bazą, a wtedy skasowanie lokalnych danych byłoby utratą pracy hodowcy,
        // nie synchronizacją (pusta porcja kończy się wcześniejszym powrotem wyżej).
        val reconciled = if (fullSnapshot) reconcile(live) else 0

        // Gdy czegoś nie dało się wpiąć (brak zwierzęcia-rodzica), nie przesuwamy
        // znacznika — następne pobranie przyniesie tę porcję jeszcze raz. Zapis jest
        // idempotentny, więc powtórka niczego nie psuje.
        if (deferred == 0) {
            syncState.saveLastPullMarker(changes.serverTime)
        }

        return Result.Applied(
            animals = changes.animals.size,
            events = changes.events.size,
            treatments = changes.treatments.size,
            listings = changes.listings.size,
            inquiries = changes.inquiries.size,
            deletions = changes.deletions.size,
            deferred = deferred,
            reconciled = reconciled,
        )
    }

    /** Zdalne identyfikatory rekordów obecnych w tej porcji, po encjach. */
    private fun liveRemoteIds(changes: SyncChangesDto): Map<String, Set<Long>> = mapOf(
        SyncContract.Entity.ANIMAL to changes.animals.mapTo(mutableSetOf()) { it.id },
        SyncContract.Entity.EVENT to changes.events.mapTo(mutableSetOf()) { it.id },
        SyncContract.Entity.TREATMENT to changes.treatments.mapTo(mutableSetOf()) { it.id },
        SyncContract.Entity.LISTING to changes.listings.mapTo(mutableSetOf()) { it.id },
        SyncContract.Entity.INQUIRY to changes.inquiries.mapTo(mutableSetOf()) { it.id },
    )

    /**
     * Usuwa lokalne rekordy, których nie ma w pełnej migawce.
     *
     * Bierzemy pod uwagę wyłącznie rekordy z mapowaniem na zdalne id: o nich wiemy,
     * że kiedyś były na serwerze, a teraz ich tam nie ma — czyli zostały usunięte,
     * nawet jeśli nagrobek po nich przepadł (np. po odtworzeniu bazy backendu).
     * Rekordów bez mapowania nie ruszamy: to wpisy, które dopiero czekają na wysłanie.
     *
     * Zwierzęta idą pierwsze — w Room kasowanie zwierzęcia pociąga kaskadę na jego
     * zdarzenia, leczenia i ogłoszenia, więc dalsze przebiegi mają mniej pracy.
     */
    private suspend fun reconcile(live: Map<String, Set<Long>>): Int {
        var removed = 0
        removed += reconcileEntity(SyncContract.Entity.ANIMAL, live) { animalDao.deleteById(it) }
        removed += reconcileEntity(SyncContract.Entity.EVENT, live) { eventDao.deleteById(it) }
        removed += reconcileEntity(SyncContract.Entity.TREATMENT, live) {
            treatmentDao.deleteById(it)
        }
        removed += reconcileEntity(SyncContract.Entity.LISTING, live) { listingDao.deleteById(it) }
        removed += reconcileEntity(SyncContract.Entity.INQUIRY, live) { inquiryDao.deleteById(it) }
        return removed
    }

    private suspend fun reconcileEntity(
        entity: String,
        live: Map<String, Set<Long>>,
        delete: suspend (Long) -> Unit,
    ): Int {
        val present = live[entity].orEmpty()
        val stale = syncDao.mappingsFor(entity).filter { it.remoteId !in present }
        stale.forEach {
            delete(it.localId)
            syncDao.deleteMapping(entity, it.localId)
        }
        return stale.size
    }

    /**
     * Lokalny identyfikator rekordu odpowiadającego zdalnemu — ale tylko jeśli ten
     * wiersz nadal istnieje.
     *
     * Powiązanie może wskazywać rekord, którego już nie ma (usunięty kaskadą razem ze
     * zwierzęciem, zanim czyszczenie powiązań dzieci trafiło do aplikacji). Zapis
     * działa w trybie REPLACE, więc użycie takiego identyfikatora nie nadpisuje
     * niczego — ono **wskrzesza usunięty wiersz**, i to podpięty pod nowe zwierzę,
     * bo `animalId` bierzemy z aktualnej mapy. Tak właśnie po usunięciu byka i dodaniu
     * go od nowa wracało jego stare urodzenie. Gdy wiersza nie ma, traktujemy porcję
     * jak nowy rekord: wstawiamy go pod nowym identyfikatorem i poprawiamy mapę.
     */
    private suspend fun <T> liveLocalId(
        entity: String,
        remoteId: Long,
        load: suspend (Long) -> T?,
    ): Long? = syncDao.localIdFor(entity, remoteId)?.takeIf { load(it) != null }

    // --- Zwierzęta ------------------------------------------------------

    private suspend fun applyAnimal(dto: AnimalDto) {
        val domain = dto.toDomain()
        val mappedId = syncDao.localIdFor(SyncContract.Entity.ANIMAL, dto.id)
        val existing = mappedId?.let { animalDao.getById(it) }
            // Zwierzę mogło powstać lokalnie i stracić mapowanie (np. po
            // reinstalacji). Kolczyk jest unikalny, więc można je rozpoznać po nim
            // — bez tego insert wywaliłby się na UNIQUE.
            ?: animalDao.getByEarTag(dto.earTag)

        if (existing != null) {
            animalDao.update(AnimalEntity.fromDomain(domain).copy(id = existing.id))
            rememberMapping(SyncContract.Entity.ANIMAL, existing.id, dto.id)
        } else {
            val localId = animalDao.insert(AnimalEntity.fromDomain(domain).copy(id = 0L))
            rememberMapping(SyncContract.Entity.ANIMAL, localId, dto.id)
        }
    }

    // --- Zdarzenia ------------------------------------------------------

    /** `false`, gdy nie ma jeszcze lokalnego zwierzęcia, do którego należy zdarzenie. */
    private suspend fun applyEvent(dto: EventDto): Boolean {
        val animalLocalId = syncDao.localIdFor(SyncContract.Entity.ANIMAL, dto.animalId)
            ?: return false

        val domain = dto.toDomain()
        val localId = liveLocalId(SyncContract.Entity.EVENT, dto.id) { eventDao.getById(it) }
        val entity = EventEntity(
            id = localId ?: 0L,
            animalId = animalLocalId,
            type = domain.type,
            eventDate = domain.eventDate,
            notes = domain.notes,
            partnerHoldingNumber = domain.partnerHoldingNumber,
            irzStatus = domain.irzStatus,
            irzSubmittedAt = domain.irzSubmittedAt,
            irzMessage = domain.irzMessage,
            irzDocumentNumber = domain.irzDocumentNumber,
        )
        // EventDao.insert działa w trybie REPLACE, więc z ustawionym id jest upsertem.
        val newId = eventDao.insert(entity)
        rememberMapping(SyncContract.Entity.EVENT, localId ?: newId, dto.id)
        return true
    }

    // --- Leczenia -------------------------------------------------------

    private suspend fun applyTreatment(dto: TreatmentDto): Boolean {
        val animalLocalId = syncDao.localIdFor(SyncContract.Entity.ANIMAL, dto.animalId)
            ?: return false

        val localId = liveLocalId(SyncContract.Entity.TREATMENT, dto.id) {
            treatmentDao.getById(it)
        }
        val entity = TreatmentEntity.fromDomain(dto.toDomain()).copy(
            id = localId ?: 0L,
            animalId = animalLocalId,
        )
        val newId = treatmentDao.insert(entity)
        rememberMapping(SyncContract.Entity.TREATMENT, localId ?: newId, dto.id)
        return true
    }

    // --- Ogłoszenia (zadanie 9) -----------------------------------------

    private suspend fun applyListing(dto: ListingDto): Boolean {
        val animalLocalId = syncDao.localIdFor(SyncContract.Entity.ANIMAL, dto.animalId)
            ?: return false

        val localId = liveLocalId(SyncContract.Entity.LISTING, dto.id) { listingDao.getById(it) }
        val entity = ListingEntity.fromDomain(dto.toDomain()).copy(
            id = localId ?: 0L,
            animalId = animalLocalId,
        )
        val newId = listingDao.insert(entity)
        rememberMapping(SyncContract.Entity.LISTING, localId ?: newId, dto.id)
        return true
    }

    /**
     * Zapytania kupujących powstają wyłącznie na serwerze — to jedyna droga,
     * którą trafiają do telefonu.
     */
    private suspend fun applyInquiry(dto: InquiryDto): Boolean {
        val listingLocalId = syncDao.localIdFor(SyncContract.Entity.LISTING, dto.listingId)
            ?: return false

        val localId = liveLocalId(SyncContract.Entity.INQUIRY, dto.id) { inquiryDao.getById(it) }
        val entity = InquiryEntity.fromDomain(dto.toDomain()).copy(
            id = localId ?: 0L,
            listingId = listingLocalId,
        )
        val newId = inquiryDao.insert(entity)
        rememberMapping(SyncContract.Entity.INQUIRY, localId ?: newId, dto.id)
        return true
    }

    // --- Usunięcia ------------------------------------------------------

    /**
     * Nagrobek z porcji, w której ten sam identyfikator należy już do żywego rekordu,
     * dotyczy poprzedniego właściciela numeru — backend nadaje id usuniętych wierszy
     * od nowa. Zastosowanie takiego nagrobka skasowałoby rekord zapisany chwilę
     * wcześniej w tym samym przebiegu, więc go pomijamy. Backend odsiewa je u siebie,
     * ale telefon nie może na tym polegać — równie dobrze może rozmawiać ze starszą
     * wersją serwera.
     */
    private suspend fun applyDeletion(dto: DeletionDto, live: Map<String, Set<Long>>) {
        if (dto.entityId in live[dto.entity].orEmpty()) return
        val localId = syncDao.localIdFor(dto.entity, dto.entityId) ?: return
        when (dto.entity) {
            // Kasowanie zwierzęcia pociąga w Room kaskadę na zdarzenia, leczenia
            // i ogłoszenia; kasowanie ogłoszenia — na jego zapytania.
            SyncContract.Entity.ANIMAL -> animalDao.deleteById(localId)
            SyncContract.Entity.EVENT -> eventDao.deleteById(localId)
            SyncContract.Entity.TREATMENT -> treatmentDao.deleteById(localId)
            SyncContract.Entity.LISTING -> listingDao.deleteById(localId)
            SyncContract.Entity.INQUIRY -> inquiryDao.deleteById(localId)
            else -> return
        }
        syncDao.deleteMapping(dto.entity, localId)
    }

    private suspend fun rememberMapping(entity: String, localId: Long, remoteId: Long) {
        syncDao.putMapping(IdMappingEntity(entity = entity, localId = localId, remoteId = remoteId))
    }
}
