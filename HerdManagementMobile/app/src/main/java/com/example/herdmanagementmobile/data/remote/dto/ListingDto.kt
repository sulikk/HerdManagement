package com.example.herdmanagementmobile.data.remote.dto

import com.example.herdmanagementmobile.domain.model.Inquiry
import com.example.herdmanagementmobile.domain.model.Listing
import com.example.herdmanagementmobile.domain.model.ListingDraft
import com.example.herdmanagementmobile.domain.model.ListingStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class ListingDto(
    val id: Long = 0L,
    @SerialName("animal_id") val animalId: Long,
    val price: Double,
    val description: String? = null,
    @SerialName("photo_uri") val photoUri: String? = null,
    val status: String = "draft",
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("irz_verified") val irzVerified: Boolean = false,
    @SerialName("irz_verified_at") val irzVerifiedAt: String? = null,
    @SerialName("irz_message") val irzMessage: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Treść żądań POST i PUT; status i odznaka weryfikacji są ustalane przez backend. */
@Serializable
data class ListingWriteDto(
    @SerialName("animal_id") val animalId: Long,
    val price: Double,
    val description: String? = null,
    @SerialName("photo_uri") val photoUri: String? = null,
)

@Serializable
data class ListingDraftDto(
    @SerialName("animal_id") val animalId: Long,
    @SerialName("ear_tag") val earTag: String,
    val name: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    val breed: String? = null,
    val sex: String = "female",
    @SerialName("herd_id") val herdId: String? = null,
    @SerialName("suggested_description") val suggestedDescription: String,
    @SerialName("existing_listing_id") val existingListingId: Long? = null,
)

/** Odpowiedź `POST /listings/{id}/verify` z wynikiem porównania z rejestrem (wymaganie F14). */
@Serializable
data class ListingVerificationDto(
    @SerialName("listing_id") val listingId: Long,
    @SerialName("irz_verified") val irzVerified: Boolean,
    @SerialName("irz_verified_at") val irzVerifiedAt: String? = null,
    val message: String? = null,
    val discrepancies: List<String> = emptyList(),
)

@Serializable
data class InquiryDto(
    val id: Long = 0L,
    @SerialName("listing_id") val listingId: Long,
    val buyer: String,
    val contact: String? = null,
    val message: String,
    @SerialName("created_at") val createdAt: String? = null,
)

fun ListingDto.toDomain(): Listing = Listing(
    id = id,
    animalId = animalId,
    price = price,
    description = description,
    photoUri = photoUri,
    status = ListingStatus.valueOf(status.uppercase()),
    publishedAt = publishedAt.utcToLocal(),
    irzVerified = irzVerified,
    irzVerifiedAt = irzVerifiedAt.utcToLocal(),
    irzMessage = irzMessage,
    createdAt = createdAt.utcToLocal() ?: LocalDateTime.now(),
)

fun Listing.toWriteDto(): ListingWriteDto = ListingWriteDto(
    animalId = animalId,
    price = price,
    description = description,
    photoUri = photoUri,
)

fun ListingDraftDto.toDomain(): ListingDraft = ListingDraft(
    animalId = animalId,
    earTag = earTag,
    name = name,
    breed = breed,
    suggestedDescription = suggestedDescription,
    existingListingId = existingListingId,
)

fun InquiryDto.toDomain(): Inquiry = Inquiry(
    id = id,
    listingId = listingId,
    buyer = buyer,
    contact = contact,
    message = message,
    createdAt = createdAt.utcToLocal() ?: LocalDateTime.now(),
)
