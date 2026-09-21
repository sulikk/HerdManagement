package com.example.herdmanagementmobile.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.herdmanagementmobile.domain.model.Inquiry
import com.example.herdmanagementmobile.domain.model.Listing
import com.example.herdmanagementmobile.domain.model.ListingStatus
import java.time.LocalDateTime

@Entity(
    tableName = "listings",
    foreignKeys = [
        ForeignKey(
            entity = AnimalEntity::class,
            parentColumns = ["id"],
            childColumns = ["animalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("animalId")],
)
data class ListingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val animalId: Long,
    val price: Double,
    val description: String?,
    val photoUri: String? = null,
    val status: ListingStatus = ListingStatus.DRAFT,
    val publishedAt: LocalDateTime? = null,
    val irzVerified: Boolean,
    val irzVerifiedAt: LocalDateTime? = null,
    val irzMessage: String? = null,
    val createdAt: LocalDateTime,
) {
    fun toDomain(): Listing = Listing(
        id = id,
        animalId = animalId,
        price = price,
        description = description,
        photoUri = photoUri,
        status = status,
        publishedAt = publishedAt,
        irzVerified = irzVerified,
        irzVerifiedAt = irzVerifiedAt,
        irzMessage = irzMessage,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(listing: Listing): ListingEntity = ListingEntity(
            id = listing.id,
            animalId = listing.animalId,
            price = listing.price,
            description = listing.description,
            photoUri = listing.photoUri,
            status = listing.status,
            publishedAt = listing.publishedAt,
            irzVerified = listing.irzVerified,
            irzVerifiedAt = listing.irzVerifiedAt,
            irzMessage = listing.irzMessage,
            createdAt = listing.createdAt,
        )
    }
}

@Entity(
    tableName = "inquiries",
    foreignKeys = [
        ForeignKey(
            entity = ListingEntity::class,
            parentColumns = ["id"],
            childColumns = ["listingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("listingId")],
)
data class InquiryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val listingId: Long,
    val buyer: String,
    val contact: String?,
    val message: String,
    val createdAt: LocalDateTime,
) {
    fun toDomain(): Inquiry = Inquiry(
        id = id,
        listingId = listingId,
        buyer = buyer,
        contact = contact,
        message = message,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(inquiry: Inquiry): InquiryEntity = InquiryEntity(
            id = inquiry.id,
            listingId = inquiry.listingId,
            buyer = inquiry.buyer,
            contact = inquiry.contact,
            message = inquiry.message,
            createdAt = inquiry.createdAt,
        )
    }
}
