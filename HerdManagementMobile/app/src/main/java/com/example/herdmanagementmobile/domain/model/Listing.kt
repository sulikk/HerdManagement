package com.example.herdmanagementmobile.domain.model

import java.time.LocalDateTime

/**
 * Status ogłoszenia sprzedażowego. Rozdzielenie [DRAFT] i [PUBLISHED] realizuje
 * wymaganie F12 — publikacja wymaga odrębnej decyzji użytkownika.
 */
enum class ListingStatus { DRAFT, PUBLISHED, SOLD, WITHDRAWN }

/**
 * Ogłoszenie sprzedażowe. Dane zwierzęcia nie są kopiowane — ogłoszenie odwołuje się
 * do karty zwierzęcia, która stanowi jedyne źródło tych danych.
 */
data class Listing(
    val id: Long = 0L,
    val animalId: Long,
    val price: Double,
    val description: String? = null,
    val photoUri: String? = null,
    val status: ListingStatus = ListingStatus.DRAFT,
    val publishedAt: LocalDateTime? = null,
    /** Odznaka „dane potwierdzone w IRZplus” (wymaganie F14). */
    val irzVerified: Boolean = false,
    val irzVerifiedAt: LocalDateTime? = null,
    /** Przyczyna braku odznaki weryfikacji, prezentowana użytkownikowi. */
    val irzMessage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    val isEditable: Boolean get() = status != ListingStatus.SOLD
    val acceptsInquiries: Boolean get() = status == ListingStatus.PUBLISHED
}

/** Zapytanie kupującego dotyczące ogłoszenia; tworzone po stronie serwera i pobierane synchronizacją. */
data class Inquiry(
    val id: Long = 0L,
    val listingId: Long,
    val buyer: String,
    val contact: String? = null,
    val message: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/** Dane karty zwierzęcia służące do wstępnego wypełnienia formularza ogłoszenia (wymaganie F13). */
data class ListingDraft(
    val animalId: Long,
    val earTag: String,
    val name: String? = null,
    val breed: String? = null,
    val suggestedDescription: String,
    val existingListingId: Long? = null,
)
