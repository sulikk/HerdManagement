package com.example.herdmanagementmobile.domain.model

import java.time.LocalDateTime

data class Listing(
    val id: Long = 0L,
    val animalId: Long,
    val price: Double,
    val description: String? = null,
    val irzVerified: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
