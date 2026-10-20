package com.example.herdmanagementmobile.data.local.relation

import com.example.herdmanagementmobile.domain.model.ListingStatus
import java.time.LocalDateTime

/** Projekcja ogłoszenia wraz z danymi zwierzęcia, wykorzystywana na liście ogłoszeń. */
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
