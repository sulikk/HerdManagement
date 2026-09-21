package com.example.herdmanagementmobile.domain.model

import java.time.LocalDateTime

/**
 * Cykl życia ogłoszenia sprzedażowego (zadanie 9).
 *
 * Rozdzielenie [DRAFT] i [PUBLISHED] realizuje wymaganie F12: ogłoszenie nie staje
 * się widoczne samo z siebie, potrzebna jest osobna decyzja hodowcy.
 */
enum class ListingStatus { DRAFT, PUBLISHED, SOLD, WITHDRAWN }

/**
 * Ogłoszenie sprzedażowe. Dane zwierzęcia (kolczyk, rasa, data urodzenia) nie są
 * tu kopiowane — ogłoszenie wskazuje kartę zwierzęcia, więc giełda i ewidencja
 * rejestrowa korzystają z jednego źródła prawdy.
 */
data class Listing(
    val id: Long = 0L,
    val animalId: Long,
    val price: Double,
    val description: String? = null,
    val photoUri: String? = null,
    val status: ListingStatus = ListingStatus.DRAFT,
    val publishedAt: LocalDateTime? = null,
    /** Odznaka „dane potwierdzone w IRZplus” (F14). */
    val irzVerified: Boolean = false,
    val irzVerifiedAt: LocalDateTime? = null,
    /** Powód braku odznaki — hodowca widzi, co poprawić. */
    val irzMessage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    val isEditable: Boolean get() = status != ListingStatus.SOLD
    val acceptsInquiries: Boolean get() = status == ListingStatus.PUBLISHED
}

/** Zapytanie kupującego o ogłoszenie — powstaje na serwerze, do telefonu trafia synchronizacją. */
data class Inquiry(
    val id: Long = 0L,
    val listingId: Long,
    val buyer: String,
    val contact: String? = null,
    val message: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * Dane karty zwierzęcia do wstępnego wypełnienia formularza ogłoszenia (F13).
 * Hodowca nie przepisuje ręcznie kolczyka ani rasy.
 */
data class ListingDraft(
    val animalId: Long,
    val earTag: String,
    val name: String? = null,
    val breed: String? = null,
    val suggestedDescription: String,
    val existingListingId: Long? = null,
)
