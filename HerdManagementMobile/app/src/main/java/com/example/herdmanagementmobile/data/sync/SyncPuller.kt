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
 * Pobiera z backendu zmiany wprowadzone poza urządzeniem (np. przez asystenta LLM)
 * i zapisuje je w bazie Room, zapewniając dwukierunkowość synchronizacji.
 *
 * Polityka konfliktów: pobieranie rozpoczyna się wyłącznie przy pustej kolejce
 * wysyłkowej. Do tego momentu wersja lokalna jest traktowana jako nowsza; po wysłaniu
 * kolejki wersja serwerowa nadpisuje lokalną. Niewysłane zmiany użytkownika nie są
 * więc nigdy tracone.
 *
 * Pełna migawka (pierwsze uruchomienie, reinstalacja, zmiana adresu serwera) dodatkowo
 * usuwa lokalne rekordy, które posiadają mapowanie na serwer, a nie występują
 * w migawce ([reconcile]).
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
        /** Pobieranie pominięte, ponieważ kolejka wysyłkowa nie jest pusta. */
        data object Skipped : Result

        data class Applied(
            val animals: Int,
            val events: Int,
            val treatments: Int,
            val listings: Int,
            val inquiries: Int,
            val deletions: Int,
            val deferred: Int,
            /** Liczba rekordów usuniętych lokalnie z powodu braku w pełnej migawce. */
            val reconciled: Int = 0,
        ) : Result {
            val total: Int
                get() = animals + events + treatments + listings + inquiries +
                    deletions + reconciled
        }
    }

    suspend fun pull(): Result {
        if (syncDao.unsentCount() > 0) return Result.Skipped

        // Znacznik pobrania jest ważny wyłącznie dla serwera, który go wystawił.
        syncState.bindToServer(baseUrl)

        val since = syncState.lastPullMarker()
        val fullSnapshot = since == null
        val changes = api.getChanges(since)
        if (changes.isEmpty) {
            syncState.saveLastPullMarker(changes.serverTime)
            return Result.Applied(0, 0, 0, 0, 0, 0, 0)
        }

        var deferred = 0
        // Rekordy nadrzędne muszą zostać zapisane (i zmapowane) przed zależnymi.
        changes.animals.forEach { applyAnimal(it) }
        changes.events.forEach { if (!applyEvent(it)) deferred++ }
        changes.treatments.forEach { if (!applyTreatment(it)) deferred++ }
        changes.listings.forEach { if (!applyListing(it)) deferred++ }
        changes.inquiries.forEach { if (!applyInquiry(it)) deferred++ }

        val live = liveRemoteIds(changes)
        changes.deletions.forEach { applyDeletion(it, live) }

        // Pusta migawka kończy działanie wcześniej, co chroni dane lokalne przed
        // usunięciem w przypadku wyczyszczonej bazy backendu.
        val reconciled = if (fullSnapshot) reconcile(live) else 0

        // Odroczone rekordy (brak rekordu nadrzędnego) zostaną pobrane ponownie;
        // zapis jest idempotentny.
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

    /** Zwraca zdalne identyfikatory rekordów obecnych w porcji, pogrupowane według encji. */
    private fun liveRemoteIds(changes: SyncChangesDto): Map<String, Set<Long>> = mapOf(
        SyncContract.Entity.ANIMAL to changes.animals.mapTo(mutableSetOf()) { it.id },
        SyncContract.Entity.EVENT to changes.events.mapTo(mutableSetOf()) { it.id },
        SyncContract.Entity.TREATMENT to changes.treatments.mapTo(mutableSetOf()) { it.id },
        SyncContract.Entity.LISTING to changes.listings.mapTo(mutableSetOf()) { it.id },
        SyncContract.Entity.INQUIRY to changes.inquiries.mapTo(mutableSetOf()) { it.id },
    )

    /**
     * Usuwa lokalne rekordy nieobecne w pełnej migawce.
     *
     * Uwzględniane są wyłącznie rekordy posiadające mapowanie na identyfikator zdalny;
     * rekordy bez mapowania oczekują na wysłanie i pozostają nienaruszone. Zwierzęta są
     * przetwarzane jako pierwsze, ponieważ ich usunięcie kaskadowo usuwa rekordy zależne.
     *
     * @return liczba usuniętych rekordów.
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
     * Zwraca lokalny identyfikator odpowiadający [remoteId], o ile wskazywany wiersz
     * nadal istnieje.
     *
     * Mapowanie może wskazywać wiersz usunięty kaskadowo. Ponieważ zapis działa w trybie
     * REPLACE, użycie takiego identyfikatora przywróciłoby usunięty rekord. W takim
     * przypadku zwracane jest `null`, a rekord jest wstawiany pod nowym identyfikatorem.
     */
    private suspend fun <T> liveLocalId(
        entity: String,
        remoteId: Long,
        load: suspend (Long) -> T?,
    ): Long? = syncDao.localIdFor(entity, remoteId)?.takeIf { load(it) != null }

    private suspend fun applyAnimal(dto: AnimalDto) {
        val domain = dto.toDomain()
        val mappedId = syncDao.localIdFor(SyncContract.Entity.ANIMAL, dto.id)
        val existing = mappedId?.let { animalDao.getById(it) }
            // Dopasowanie po unikalnym numerze kolczyka obsługuje rekordy, które
            // utraciły mapowanie, i zapobiega naruszeniu ograniczenia UNIQUE.
            ?: animalDao.getByEarTag(dto.earTag)

        if (existing != null) {
            animalDao.update(AnimalEntity.fromDomain(domain).copy(id = existing.id))
            rememberMapping(SyncContract.Entity.ANIMAL, existing.id, dto.id)
        } else {
            val localId = animalDao.insert(AnimalEntity.fromDomain(domain).copy(id = 0L))
            rememberMapping(SyncContract.Entity.ANIMAL, localId, dto.id)
        }
    }

    /** @return `false`, gdy zwierzę, do którego należy zdarzenie, nie istnieje jeszcze lokalnie. */
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
        // Wstawienie w trybie REPLACE z ustawionym identyfikatorem działa jak upsert.
        val newId = eventDao.insert(entity)
        rememberMapping(SyncContract.Entity.EVENT, localId ?: newId, dto.id)
        return true
    }

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

    /** Zapisuje zapytanie kupującego; zapytania powstają wyłącznie po stronie serwera. */
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

    /**
     * Stosuje znacznik usunięcia rekordu.
     *
     * Znacznik jest pomijany, jeżeli ten sam identyfikator występuje w porcji jako
     * istniejący rekord — backend może ponownie wykorzystać identyfikatory usuniętych
     * wierszy, więc znacznik dotyczy wówczas poprzedniego rekordu. Usunięcie zwierzęcia
     * lub ogłoszenia kaskadowo usuwa rekordy zależne.
     */
    private suspend fun applyDeletion(dto: DeletionDto, live: Map<String, Set<Long>>) {
        if (dto.entityId in live[dto.entity].orEmpty()) return
        val localId = syncDao.localIdFor(dto.entity, dto.entityId) ?: return
        when (dto.entity) {
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
