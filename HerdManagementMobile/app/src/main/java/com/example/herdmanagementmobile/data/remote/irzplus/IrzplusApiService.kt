package com.example.herdmanagementmobile.data.remote.irzplus

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Klient REST mocka Portalu IRZplus. Ścieżki odwzorowują realne API ARiMR:
 * logowanie -> token Bearer, zgłoszenia pod /dokument/api/prod/..., dane
 * zwierząt pod /zwierze/api/prod/... Reużywa tego samego Retrofit (baseUrl).
 */
interface IrzplusApiService {

    @POST("irzplus/logowanie")
    suspend fun logowanie(@Body body: LoginRequest): LoginResponse

    @GET("irzplus/zwierze/api/prod/zwierzetaProducenta")
    suspend fun pobierzZwierzeta(
        @Header("Authorization") authorization: String,
    ): List<ZwierzeIndywidualneDTO>

    @POST("irzplus/dokument/api/prod/zgloszenieUrodzenia")
    suspend fun zglosUrodzenie(
        @Header("Authorization") authorization: String,
        @Body body: DyspozycjaZgloszenia,
    ): ZlozenieDyspozycjiResponse

    @POST("irzplus/dokument/api/prod/zgloszeniePrzybycia")
    suspend fun zglosPrzybycie(
        @Header("Authorization") authorization: String,
        @Body body: DyspozycjaZgloszenia,
    ): ZlozenieDyspozycjiResponse

    @POST("irzplus/dokument/api/prod/zgloszenieUbycia")
    suspend fun zglosUbycie(
        @Header("Authorization") authorization: String,
        @Body body: DyspozycjaZgloszenia,
    ): ZlozenieDyspozycjiResponse

    @POST("irzplus/dokument/api/prod/zgloszeniePadniecia")
    suspend fun zglosPadniecie(
        @Header("Authorization") authorization: String,
        @Body body: DyspozycjaZgloszenia,
    ): ZlozenieDyspozycjiResponse
}
