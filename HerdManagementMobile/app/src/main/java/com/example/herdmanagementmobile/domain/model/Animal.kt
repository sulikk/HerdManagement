package com.example.herdmanagementmobile.domain.model

import java.time.LocalDate

enum class Sex { FEMALE, MALE, OTHER }

data class Animal(
    val id: Long = 0L,
    val earTag: String,
    val name: String? = null,
    val birthDate: LocalDate? = null,
    val breed: String? = null,
    val sex: Sex = Sex.FEMALE,
    val motherEarTag: String? = null,
    val herdId: String? = null,
)
