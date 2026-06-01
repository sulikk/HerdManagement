package com.example.herdmanagementmobile.data.remote.dto

import com.example.herdmanagementmobile.domain.model.Treatment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class TreatmentDto(
    val id: Long = 0L,
    @SerialName("animal_id") val animalId: Long,
    val drug: String,
    val dose: String? = null,
    val vet: String? = null,
    @SerialName("treatment_date") val treatmentDate: String,
    @SerialName("withdrawal_until") val withdrawalUntil: String? = null,
    val notes: String? = null,
)

fun TreatmentDto.toDomain(): Treatment = Treatment(
    id = id,
    animalId = animalId,
    drug = drug,
    dose = dose,
    vet = vet,
    treatmentDate = LocalDate.parse(treatmentDate),
    withdrawalUntil = withdrawalUntil?.let(LocalDate::parse),
    notes = notes,
)

fun Treatment.toDto(): TreatmentDto = TreatmentDto(
    id = id,
    animalId = animalId,
    drug = drug,
    dose = dose,
    vet = vet,
    treatmentDate = treatmentDate.toString(),
    withdrawalUntil = withdrawalUntil?.toString(),
    notes = notes,
)
