package com.example.herdmanagementmobile.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.herdmanagementmobile.domain.model.Treatment
import java.time.LocalDate

@Entity(
    tableName = "treatments",
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
data class TreatmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val animalId: Long,
    val drug: String,
    val dose: String?,
    val vet: String?,
    val treatmentDate: LocalDate,
    val withdrawalUntil: LocalDate?,
    val notes: String?,
) {
    fun toDomain(): Treatment = Treatment(
        id = id,
        animalId = animalId,
        drug = drug,
        dose = dose,
        vet = vet,
        treatmentDate = treatmentDate,
        withdrawalUntil = withdrawalUntil,
        notes = notes,
    )

    companion object {
        fun fromDomain(t: Treatment): TreatmentEntity = TreatmentEntity(
            id = t.id,
            animalId = t.animalId,
            drug = t.drug,
            dose = t.dose,
            vet = t.vet,
            treatmentDate = t.treatmentDate,
            withdrawalUntil = t.withdrawalUntil,
            notes = t.notes,
        )
    }
}
