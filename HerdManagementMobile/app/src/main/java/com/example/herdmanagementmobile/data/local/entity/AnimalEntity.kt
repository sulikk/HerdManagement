package com.example.herdmanagementmobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.Sex
import java.time.LocalDate

@Entity(
    tableName = "animals",
    indices = [Index(value = ["earTag"], unique = true)],
)
data class AnimalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val earTag: String,
    val name: String?,
    val birthDate: LocalDate?,
    val breed: String?,
    val sex: Sex,
    val motherEarTag: String?,
    val herdId: String?,
) {
    fun toDomain(): Animal = Animal(
        id = id,
        earTag = earTag,
        name = name,
        birthDate = birthDate,
        breed = breed,
        sex = sex,
        motherEarTag = motherEarTag,
        herdId = herdId,
    )

    companion object {
        fun fromDomain(animal: Animal): AnimalEntity = AnimalEntity(
            id = animal.id,
            earTag = animal.earTag,
            name = animal.name,
            birthDate = animal.birthDate,
            breed = animal.breed,
            sex = animal.sex,
            motherEarTag = animal.motherEarTag,
            herdId = animal.herdId,
        )
    }
}
