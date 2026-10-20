package com.example.herdmanagementmobile.data.repository

import com.example.herdmanagementmobile.data.local.dao.AnimalDao
import com.example.herdmanagementmobile.data.local.dao.InquiryDao
import com.example.herdmanagementmobile.data.local.dao.SyncDao
import com.example.herdmanagementmobile.data.local.dao.ListingDao
import com.example.herdmanagementmobile.data.local.entity.InquiryEntity
import com.example.herdmanagementmobile.data.local.entity.ListingEntity
import com.example.herdmanagementmobile.data.local.relation.ListingWithAnimal
import com.example.herdmanagementmobile.data.sync.SyncManager
import com.example.herdmanagementmobile.domain.model.Inquiry
import com.example.herdmanagementmobile.domain.model.Listing
import com.example.herdmanagementmobile.domain.model.ListingDraft
import com.example.herdmanagementmobile.domain.model.ListingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repozytorium ogłoszeń sprzedażowych w modelu offline-first.
 *
 * Zmiany są zapisywane w bazie lokalnej i kolejkowane do synchronizacji. Wynik
 * weryfikacji IRZplus oraz zapytania kupujących są pobierane synchronizacją.
 */
@Singleton
class ListingRepository @Inject constructor(
    private val listingDao: ListingDao,
    private val inquiryDao: InquiryDao,
    private val syncDao: SyncDao,
    private val animalDao: AnimalDao,
    private val syncManager: SyncManager,
) {
    fun observeAll(): Flow<List<ListingWithAnimal>> = listingDao.observeWithAnimal()

    fun observeById(id: Long): Flow<Listing?> =
        listingDao.observeById(id).map { it?.toDomain() }

    fun observeInquiries(listingId: Long): Flow<List<Inquiry>> =
        inquiryDao.observeForListing(listingId).map { list -> list.map(InquiryEntity::toDomain) }

    fun observeInquiryCount(): Flow<Int> = inquiryDao.observeCount()

    /** Zwraca dane z lokalnej karty zwierzęcia do wstępnego wypełnienia formularza (wymaganie F13). */
    suspend fun draftFor(animalId: Long): ListingDraft? {
        val animal = animalDao.getById(animalId) ?: return null
        val existing = listingDao.getActiveForAnimal(animalId)
        return ListingDraft(
            animalId = animal.id,
            earTag = animal.earTag,
            name = animal.name,
            breed = animal.breed,
            suggestedDescription = buildDescription(animal.breed, animal.birthDate?.toString(), animal.earTag),
            existingListingId = existing?.id,
        )
    }

    suspend fun activeListingFor(animalId: Long): Listing? =
        listingDao.getActiveForAnimal(animalId)?.toDomain()

    suspend fun create(listing: Listing): Long {
        val id = listingDao.insert(ListingEntity.fromDomain(listing).copy(id = 0L))
        syncManager.enqueueListingCreate(listing.copy(id = id))
        return id
    }

    suspend fun update(listing: Listing) {
        // Zmiana treści unieważnia odznakę weryfikacji, zgodnie z logiką backendu.
        val edited = listing.copy(irzVerified = false, irzVerifiedAt = null, irzMessage = null)
        listingDao.update(ListingEntity.fromDomain(edited))
        syncManager.enqueueListingUpdate(edited)
    }

    /** Publikuje ogłoszenie; wywoływane wyłącznie po potwierdzeniu użytkownika (wymaganie F12). */
    suspend fun publish(id: Long) = changeStatus(id, ListingStatus.PUBLISHED) {
        syncManager.enqueueListingPublish(id)
    }

    suspend fun withdraw(id: Long) = changeStatus(id, ListingStatus.WITHDRAWN) {
        syncManager.enqueueListingWithdraw(id)
    }

    suspend fun markSold(id: Long) = changeStatus(id, ListingStatus.SOLD) {
        syncManager.enqueueListingSold(id)
    }

    /** Zleca weryfikację ogłoszenia w rejestrze IRZplus (wymaganie F14). */
    suspend fun requestVerification(id: Long) {
        syncManager.enqueueListingVerify(id)
    }

    /**
     * Usuwa ogłoszenie lokalnie (wraz z zapytaniami) i kolejkuje usunięcie na serwerze.
     * Mapowania zapytań są usuwane jawnie (zob. [SyncDao.deleteInquiryMappingsForListing]).
     */
    suspend fun delete(id: Long) {
        syncDao.deleteInquiryMappingsForListing(id)
        listingDao.deleteById(id)
        syncManager.enqueueListingDelete(id)
    }

    private suspend fun changeStatus(
        id: Long,
        status: ListingStatus,
        enqueue: suspend () -> Unit,
    ) {
        val current = listingDao.getById(id) ?: return
        listingDao.update(
            current.copy(
                status = status,
                publishedAt = current.publishedAt
                    ?: LocalDateTime.now().takeIf { status == ListingStatus.PUBLISHED },
            ),
        )
        enqueue()
    }

    private fun buildDescription(breed: String?, birthDate: String?, earTag: String): String {
        val parts = buildList {
            breed?.let(::add)
            birthDate?.let { add("ur. $it") }
            add("kolczyk $earTag")
        }
        val text = parts.joinToString(", ")
        return text.replaceFirstChar(Char::uppercaseChar) + "."
    }
}
