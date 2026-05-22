package com.example.herdmanagementmobile.domain.model

import java.time.LocalDate

data class Treatment(
    val id: Long = 0L,
    val animalId: Long,
    val drug: String,
    val dose: String? = null,
    val vet: String? = null,
    val treatmentDate: LocalDate,
    val withdrawalUntil: LocalDate? = null,
    val notes: String? = null,
)
