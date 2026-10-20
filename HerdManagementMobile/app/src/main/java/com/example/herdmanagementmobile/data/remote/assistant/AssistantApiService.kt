package com.example.herdmanagementmobile.data.remote.assistant

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Interfejs REST asystenta LLM (router `/assistant` backendu). */
interface AssistantApiService {

    @GET("assistant/status")
    suspend fun status(): AssistantStatusDto

    @POST("assistant/chat")
    suspend fun chat(@Body body: ChatRequestDto): ChatResponseDto

    @POST("assistant/confirm")
    suspend fun confirm(@Body body: ConfirmRequestDto): ChatResponseDto
}
