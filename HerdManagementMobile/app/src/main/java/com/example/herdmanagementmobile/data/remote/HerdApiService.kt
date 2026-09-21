package com.example.herdmanagementmobile.data.remote

import com.example.herdmanagementmobile.data.remote.dto.AnimalDto
import com.example.herdmanagementmobile.data.remote.dto.EventDto
import com.example.herdmanagementmobile.data.remote.dto.InquiryDto
import com.example.herdmanagementmobile.data.remote.dto.IrzResultDto
import com.example.herdmanagementmobile.data.remote.dto.ListingDraftDto
import com.example.herdmanagementmobile.data.remote.dto.ListingDto
import com.example.herdmanagementmobile.data.remote.dto.ListingVerificationDto
import com.example.herdmanagementmobile.data.remote.dto.ListingWriteDto
import com.example.herdmanagementmobile.data.remote.dto.SyncChangesDto
import com.example.herdmanagementmobile.data.remote.dto.TreatmentDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

    /**
     * Zmiany na backendzie od znacznika [since] (pusty = pełna migawka).
     * Źródło danych, których telefon sam nie wprowadził: wpisy asystenta LLM,
     * poprawki zrobione w backendzie, dane z innego urządzenia.
     */
    @GET("sync/changes")
    suspend fun getChanges(@Query("since") since: String? = null): SyncChangesDto

    // --- Ogłoszenia sprzedażowe (zadanie 9) ---------------------------

    /** Dane karty zwierzęcia do wstępnego wypełnienia formularza (F13). */
    @GET("animals/{animalId}/listing-draft")
    suspend fun getListingDraft(@Path("animalId") animalId: Long): ListingDraftDto

    @POST("listings")
    suspend fun createListing(@Body body: ListingWriteDto): ListingDto

    @PUT("listings/{id}")
    suspend fun updateListing(@Path("id") id: Long, @Body body: ListingWriteDto): ListingDto

    /** Jawna publikacja — wymaganie F12. */
    @POST("listings/{id}/publish")
    suspend fun publishListing(@Path("id") id: Long): ListingDto

    @POST("listings/{id}/withdraw")
    suspend fun withdrawListing(@Path("id") id: Long): ListingDto

    @POST("listings/{id}/sold")
    suspend fun markListingSold(@Path("id") id: Long): ListingDto

    /** Weryfikacja wobec rejestru IRZplus — nadaje odznakę (F14). */
    @POST("listings/{id}/verify")
    suspend fun verifyListing(@Path("id") id: Long): ListingVerificationDto

    @DELETE("listings/{id}")
    suspend fun deleteListing(@Path("id") id: Long)

    @GET("listings/{id}/inquiries")
    suspend fun listInquiries(@Path("id") id: Long): List<InquiryDto>
}
