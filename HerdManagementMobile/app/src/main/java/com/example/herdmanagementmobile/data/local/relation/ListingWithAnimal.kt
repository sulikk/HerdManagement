package com.example.herdmanagementmobile.data.local.relation

import com.example.herdmanagementmobile.domain.model.ListingStatus
import java.time.LocalDateTime

/**
 * Projekcja ogłoszenia razem z danymi zwierzęcia — lista ogłoszeń musi pokazywać
 * kolczyk i rasę, a te leżą w tabeli `animals`.
 */
data class ListingWithAnimal(
    val listingId: Long,
    val animalId: Long,
    val price: Double,
    val description: String?,
    val status: ListingStatus,
    val irzVerified: Boolean,
    val irzMessage: String?,
    val createdAt: LocalDateTime,
    val earTag: String,
    val animalName: String?,
    val breed: String?,
    val inquiryCount: Int,
)
