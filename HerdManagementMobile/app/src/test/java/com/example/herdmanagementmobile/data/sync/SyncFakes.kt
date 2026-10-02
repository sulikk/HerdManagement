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
import com.example.herdmanagementmobile.data.local.entity.SyncQueueEntity
import com.example.herdmanagementmobile.data.local.entity.TreatmentEntity
import com.example.herdmanagementmobile.data.local.relation.ComplianceEventRow
import com.example.herdmanagementmobile.data.local.relation.ListingWithAnimal
import com.example.herdmanagementmobile.data.remote.HerdApiService
import com.example.herdmanagementmobile.data.remote.dto.AnimalDto
import com.example.herdmanagementmobile.data.remote.dto.EventDto
import com.example.herdmanagementmobile.data.remote.dto.InquiryDto
import com.example.herdmanagementmobile.data.remote.dto.IrzResultDto
import com.example.herdmanagementmobile.data.remote.dto.ListingDraftDto
import com.example.herdmanagementmobile.data.remote.dto.ListingDto
import com.example.herdmanagementmobile.data.remote.dto.ListingVerificationDto
import com.example.herdmanagementmobile.data.remote.dto.ListingWriteDto
import com.example.herdmanagementmobile.data.remote.dto.SyncChangesDto
import com.example.herdmanagementmobile.data.remote.dto.TreatmentDto
import com.example.herdmanagementmobile.domain.model.ListingStatus
import com.example.herdmanagementmobile.domain.model.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Atrapy DAO i API dla testów [SyncPuller] — zwykłe mapy w pamięci zamiast Room.
 *
 * Interesuje nas logika mapowania identyfikatorów i rozstrzygania konfliktów,
 * a ta nie potrzebuje ani emulatora, ani prawdziwej bazy.
 */
class Fakes {
    val animals = FakeAnimalDao()
    val events = FakeEventDao()
    val treatments = FakeTreatmentDao()
    val listings = FakeListingDao()
    val inquiries = FakeInquiryDao()
    val sync = FakeSyncDao()
    val marker = FakeMarkerStore()
}

class FakeMarkerStore : SyncMarkerStore {
    var marker: String? = null
    var server: String? = null

    override suspend fun lastPullMarker(): String? = marker

    override suspend fun saveLastPullMarker(marker: String) {
        this.marker = marker
    }

    /** Jak w [SyncStateRepository]: inny backend unieważnia znacznik. */
    override suspend fun bindToServer(baseUrl: String) {
        if (server != baseUrl) {
            marker = null
            server = baseUrl
        }
    }
}

class FakeApi(private val changes: SyncChangesDto) : HerdApiService {
    var lastSince: String? = null

    override suspend fun getChanges(since: String?): SyncChangesDto {
        lastSince = since
        return changes
    }

    // Pozostałe operacje nie biorą udziału w pobieraniu zmian.
    override suspend fun getListingDraft(animalId: Long): ListingDraftDto = unsupported()
    override suspend fun createListing(body: ListingWriteDto): ListingDto = unsupported()
    override suspend fun updateListing(id: Long, body: ListingWriteDto): ListingDto = unsupported()
    override suspend fun publishListing(id: Long): ListingDto = unsupported()
    override suspend fun withdrawListing(id: Long): ListingDto = unsupported()
    override suspend fun markListingSold(id: Long): ListingDto = unsupported()
    override suspend fun verifyListing(id: Long): ListingVerificationDto = unsupported()
    override suspend fun deleteListing(id: Long) = unsupported()
    override suspend fun listInquiries(id: Long): List<InquiryDto> = unsupported()
    override suspend fun listAnimals(): List<AnimalDto> = unsupported()
    override suspend fun getAnimal(id: Long): AnimalDto = unsupported()
    override suspend fun createAnimal(body: AnimalDto): AnimalDto = unsupported()
    override suspend fun updateAnimal(id: Long, body: AnimalDto): AnimalDto = unsupported()
    override suspend fun deleteAnimal(id: Long) = unsupported()
    override suspend fun listEvents(animalId: Long): List<EventDto> = unsupported()
    override suspend fun createEvent(animalId: Long, body: EventDto): EventDto = unsupported()
    override suspend fun updateEventIrz(id: Long, body: IrzResultDto): EventDto = unsupported()
    override suspend fun deleteEvent(id: Long) = unsupported()
    override suspend fun listTreatments(animalId: Long): List<TreatmentDto> = unsupported()
    override suspend fun createTreatment(animalId: Long, body: TreatmentDto): TreatmentDto = unsupported()
    override suspend fun deleteTreatment(id: Long) = unsupported()

    private fun unsupported(): Nothing = error("Nieużywane w teście pobierania zmian")
}

class FakeAnimalDao : AnimalDao {
    val rows = linkedMapOf<Long, AnimalEntity>()
    private var nextId = 1L

    fun seed(earTag: String, name: String?): Long {
        val id = nextId++
        rows[id] = AnimalEntity(id, earTag, name, null, null, Sex.FEMALE, null, null)
        return id
    }

    override suspend fun insert(animal: AnimalEntity): Long {
        require(rows.values.none { it.earTag == animal.earTag }) {
            "Kolczyk ${animal.earTag} juz istnieje - Room zglosilby naruszenie UNIQUE"
        }
        val id = if (animal.id == 0L) nextId++ else animal.id
        rows[id] = animal.copy(id = id)
        return id
    }

    override suspend fun update(animal: AnimalEntity) {
        rows[animal.id] = animal
    }

    override suspend fun getById(id: Long): AnimalEntity? = rows[id]

    override suspend fun getByEarTag(earTag: String): AnimalEntity? =
        rows.values.firstOrNull { it.earTag == earTag }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override fun observeAll(): Flow<List<AnimalEntity>> = flowOf(rows.values.toList())
    override fun observeById(id: Long): Flow<AnimalEntity?> = flowOf(rows[id])
}

class FakeEventDao : EventDao {
    val rows = linkedMapOf<Long, EventEntity>()
    private var nextId = 1L

    override suspend fun insert(event: EventEntity): Long {
        val id = if (event.id == 0L) nextId++ else event.id
        rows[id] = event.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override suspend fun getById(id: Long): EventEntity? = rows[id]

    override suspend fun updateIrzResult(
        id: Long,
        status: String,
        message: String?,
        documentNumber: String?,
        submittedAt: String?,
    ) = error("Nieużywane w teście pobierania zmian")

    override fun observeForAnimal(animalId: Long): Flow<List<EventEntity>> =
        flowOf(rows.values.filter { it.animalId == animalId })

    override fun observeAll(): Flow<List<EventEntity>> = flowOf(rows.values.toList())
    override fun observePending(): Flow<List<EventEntity>> = flowOf(emptyList())
    override fun observeUnconfirmed(): Flow<List<ComplianceEventRow>> = flowOf(emptyList())
}

class FakeTreatmentDao : TreatmentDao {
    val rows = linkedMapOf<Long, TreatmentEntity>()
    private var nextId = 1L

    override suspend fun getById(id: Long): TreatmentEntity? = rows[id]

    override suspend fun insert(treatment: TreatmentEntity): Long {
        val id = if (treatment.id == 0L) nextId++ else treatment.id
        rows[id] = treatment.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override fun observeForAnimal(animalId: Long): Flow<List<TreatmentEntity>> =
        flowOf(rows.values.filter { it.animalId == animalId })

    override fun observeAll(): Flow<List<TreatmentEntity>> = flowOf(rows.values.toList())
}

class FakeListingDao : ListingDao {
    val rows = linkedMapOf<Long, ListingEntity>()
    private var nextId = 1L

    override suspend fun insert(listing: ListingEntity): Long {
        val id = if (listing.id == 0L) nextId++ else listing.id
        rows[id] = listing.copy(id = id)
        return id
    }

    override suspend fun update(listing: ListingEntity) {
        rows[listing.id] = listing
    }

    override suspend fun getById(id: Long): ListingEntity? = rows[id]

    override suspend fun getActiveForAnimal(animalId: Long): ListingEntity? =
        rows.values.firstOrNull {
            it.animalId == animalId &&
                (it.status == ListingStatus.DRAFT || it.status == ListingStatus.PUBLISHED)
        }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override fun observeById(id: Long): Flow<ListingEntity?> = flowOf(rows[id])
    override fun observeWithAnimal(): Flow<List<ListingWithAnimal>> = flowOf(emptyList())
}

class FakeInquiryDao : InquiryDao {
    val rows = linkedMapOf<Long, InquiryEntity>()
    private var nextId = 1L

    override suspend fun getById(id: Long): InquiryEntity? = rows[id]

    override suspend fun insert(inquiry: InquiryEntity): Long {
        val id = if (inquiry.id == 0L) nextId++ else inquiry.id
        rows[id] = inquiry.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override fun observeForListing(listingId: Long): Flow<List<InquiryEntity>> =
        flowOf(rows.values.filter { it.listingId == listingId })

    override fun observeCount(): Flow<Int> = flowOf(rows.size)
}

class FakeSyncDao : SyncDao {
    /** klucz: encja + id lokalne -> id zdalne */
    private val mappings = mutableMapOf<Pair<String, Long>, Long>()
    var unsent = 0

    /** Ile powiązań encji jest w mapie — do sprawdzania, czy śmieci nie zostają. */
    fun mappingCount(entity: String): Int = mappings.keys.count { it.first == entity }

    // putMapping zostaje domyślną implementacją z [SyncDao] — atrapa dostarcza tylko
    // dwie operacje składowe, żeby test sprawdzał prawdziwy niezmiennik mapy.
    override suspend fun insertMapping(mapping: IdMappingEntity) {
        mappings[mapping.entity to mapping.localId] = mapping.remoteId
    }

    override suspend fun clearRemoteId(entity: String, remoteId: Long) {
        mappings.entries.removeAll { it.key.first == entity && it.value == remoteId }
    }

    /** Dzieci zna tylko prawdziwa baza; w teście czyścimy mapę wprost. */
    override suspend fun deleteEventMappingsForAnimal(animalId: Long) = Unit
    override suspend fun deleteTreatmentMappingsForAnimal(animalId: Long) = Unit
    override suspend fun deleteListingMappingsForAnimal(animalId: Long) = Unit
    override suspend fun deleteInquiryMappingsForAnimal(animalId: Long) = Unit
    override suspend fun deleteInquiryMappingsForListing(listingId: Long) = Unit

    override suspend fun remoteIdFor(entity: String, localId: Long): Long? =
        mappings[entity to localId]

    override suspend fun localIdFor(entity: String, remoteId: Long): Long? =
        mappings.entries.firstOrNull { it.key.first == entity && it.value == remoteId }?.key?.second

    override suspend fun deleteMapping(entity: String, localId: Long) {
        mappings.remove(entity to localId)
    }

    override suspend fun mappingsFor(entity: String): List<IdMappingEntity> =
        mappings.entries
            .filter { it.key.first == entity }
            .map { IdMappingEntity(entity, it.key.second, it.value) }

    override suspend fun unsentCount(): Int = unsent

    override suspend fun enqueue(entry: SyncQueueEntity): Long = error("Nieużywane w teście")
    override suspend fun pending(): List<SyncQueueEntity> = emptyList()
    override fun observePendingCount(): Flow<Int> = flowOf(0)
    override fun observeFailedCount(): Flow<Int> = flowOf(0)
    override suspend fun resetFailed(updatedAt: String): Int = 0
    override suspend fun updateEntry(
        id: Long,
        status: String,
        retries: Int,
        error: String?,
        updatedAt: String,
    ) = error("Nieużywane w teście")
}
