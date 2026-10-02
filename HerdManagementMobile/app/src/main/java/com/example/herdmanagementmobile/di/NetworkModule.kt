package com.example.herdmanagementmobile.di

import com.example.herdmanagementmobile.BuildConfig
import com.example.herdmanagementmobile.data.remote.HerdApiService
import com.example.herdmanagementmobile.data.remote.assistant.AssistantApiService
import com.example.herdmanagementmobile.data.remote.irzplus.IrzplusApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Bazowy adres backendu — z `local.properties` (klucz `herd.baseUrl`) przez
     * `BuildConfig`. Emulator widzi host maszyny jako 10.0.2.2; fizyczny telefon
     * potrzebuje IP komputera w sieci LAN, a to zmienia się z każdą siecią, więc
     * adres nie może leżeć w kodzie.
     */
    @Provides
    @Singleton
    @BaseUrl
    fun provideBaseUrl(): String = BuildConfig.BASE_URL

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(AssistantTimeoutInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
        @BaseUrl baseUrl: String,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideHerdApiService(retrofit: Retrofit): HerdApiService =
        retrofit.create(HerdApiService::class.java)

    @Provides
    @Singleton
    fun provideIrzplusApiService(retrofit: Retrofit): IrzplusApiService =
        retrofit.create(IrzplusApiService::class.java)

    @Provides
    @Singleton
    fun provideAssistantApiService(retrofit: Retrofit): AssistantApiService =
        retrofit.create(AssistantApiService::class.java)
}

/**
 * Odpowiedź asystenta powstaje dopiero po rozmowie backendu z modelem językowym
 * (często kilkadziesiąt sekund), więc dla ścieżek /assistant wydłużamy czas
 * oczekiwania. Pozostałe wywołania zostają przy krótkim timeoucie.
 */
private object AssistantTimeoutInterceptor : Interceptor {
    private const val ASSISTANT_READ_TIMEOUT_SECONDS = 120

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.startsWith("/assistant")) {
            return chain.proceed(request)
        }
        return chain
            .withReadTimeout(ASSISTANT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withWriteTimeout(ASSISTANT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .proceed(request)
    }
}
