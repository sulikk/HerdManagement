package com.example.herdmanagementmobile.data.remote

import com.example.herdmanagementmobile.data.remote.dto.AnimalDto
import com.example.herdmanagementmobile.data.remote.dto.EventDto
import com.example.herdmanagementmobile.data.remote.dto.TreatmentDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import com.example.herdmanagementmobile.data.remote.dto.IrzResultDto
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Klient REST backendu Herd Management (FastAPI).
 * Ścieżki odpowiadają routerom: animals, events, treatments.
 */
interface HerdApiService {

    @GET("animals")
    suspend fun listAnimals(): List<AnimalDto>

    @GET("animals/{id}")
    suspend fun getAnimal(@Path("id") id: Long): AnimalDto

    @POST("animals")
    suspend fun createAnimal(@Body body: AnimalDto): AnimalDto

    @PUT("animals/{id}")
    suspend fun updateAnimal(@Path("id") id: Long, @Body body: AnimalDto): AnimalDto

    @DELETE("animals/{id}")
    suspend fun deleteAnimal(@Path("id") id: Long)

    @GET("animals/{animalId}/events")
    suspend fun listEvents(@Path("animalId") animalId: Long): List<EventDto>

    @POST("animals/{animalId}/events")
    suspend fun createEvent(
        @Path("animalId") animalId: Long,
        @Body body: EventDto,
    ): EventDto

    @PATCH("events/{id}/irz")
    suspend fun updateEventIrz(@Path("id") id: Long, @Body body: IrzResultDto): EventDto

    @DELETE("events/{id}")
    suspend fun deleteEvent(@Path("id") id: Long)

    @GET("animals/{animalId}/treatments")
    suspend fun listTreatments(@Path("animalId") animalId: Long): List<TreatmentDto>

    @POST("animals/{animalId}/treatments")
    suspend fun createTreatment(
        @Path("animalId") animalId: Long,
        @Body body: TreatmentDto,
    ): TreatmentDto

    @DELETE("treatments/{id}")
    suspend fun deleteTreatment(@Path("id") id: Long)
}
