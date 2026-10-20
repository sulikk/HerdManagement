package com.example.herdmanagementmobile.data.remote.dto

import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.Sex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** DTO zwierzęcia zgodne ze schematem backendu (nazwy pól w snake_case, daty w formacie ISO 8601). */
@Serializable
data class AnimalDto(
    val id: Long = 0L,
    @SerialName("ear_tag") val earTag: String,
    val name: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    val breed: String? = null,
    val sex: String = "female",
    @SerialName("mother_ear_tag") val motherEarTag: String? = null,
    @SerialName("herd_id") val herdId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

fun AnimalDto.toDomain(): Animal = Animal(
    id = id,
    earTag = earTag,
    name = name,
    birthDate = birthDate?.let(LocalDate::parse),
    breed = breed,
    sex = Sex.valueOf(sex.uppercase()),
    motherEarTag = motherEarTag,
    herdId = herdId,
)

fun Animal.toDto(): AnimalDto = AnimalDto(
    id = id,
    earTag = earTag,
    name = name,
    birthDate = birthDate?.toString(),
    breed = breed,
    sex = sex.name.lowercase(),
    motherEarTag = motherEarTag,
    herdId = herdId,
)
