package com.example.herdmanagementmobile.data.remote

import com.example.herdmanagementmobile.data.remote.dto.toDomain
import com.example.herdmanagementmobile.data.remote.dto.toDto
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.Treatment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Warstwa pośrednia nad [HerdApiService] mapująca DTO na modele domenowe
 * i zwracająca [Result] zamiast zgłaszać wyjątki.
 */
@Singleton
class HerdRemoteDataSource @Inject constructor(
    private val api: HerdApiService,
) {
    suspend fun fetchAnimals(): Result<List<Animal>> = runCatching {
        api.listAnimals().map { it.toDomain() }
    }

    suspend fun pushAnimal(animal: Animal): Result<Animal> = runCatching {
        api.createAnimal(animal.toDto()).toDomain()
    }

    suspend fun deleteAnimal(id: Long): Result<Unit> = runCatching {
        api.deleteAnimal(id)
    }

    suspend fun fetchEvents(animalId: Long): Result<List<AnimalEvent>> = runCatching {
        api.listEvents(animalId).map { it.toDomain() }
    }

    suspend fun pushEvent(event: AnimalEvent): Result<AnimalEvent> = runCatching {
        api.createEvent(event.animalId, event.toDto()).toDomain()
    }

    suspend fun deleteEvent(id: Long): Result<Unit> = runCatching {
        api.deleteEvent(id)
    }

    suspend fun fetchTreatments(animalId: Long): Result<List<Treatment>> = runCatching {
        api.listTreatments(animalId).map { it.toDomain() }
    }

    suspend fun pushTreatment(treatment: Treatment): Result<Treatment> = runCatching {
        api.createTreatment(treatment.animalId, treatment.toDto()).toDomain()
    }

    suspend fun deleteTreatment(id: Long): Result<Unit> = runCatching {
        api.deleteTreatment(id)
    }
}
