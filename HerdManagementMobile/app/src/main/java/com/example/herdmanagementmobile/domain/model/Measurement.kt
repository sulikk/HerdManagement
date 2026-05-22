package com.example.herdmanagementmobile.domain.model

import java.time.LocalDate

enum class MeasurementType { WEIGHT, BCS }

data class Measurement(
    val id: Long = 0L,
    val animalId: Long,
    val type: MeasurementType,
    val value: Double,
    val measurementDate: LocalDate,
    val photoUri: String? = null,
)
