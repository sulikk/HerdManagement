package com.example.herdmanagementmobile.di

import com.example.herdmanagementmobile.BuildConfig
import com.example.herdmanagementmobile.data.remote.HerdApiService
import com.example.herdmanagementmobile.data.remote.irzplus.IrzplusApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Bazowy adres backendu. 10.0.2.2 to host maszyny widziany z emulatora Androida.
     * Na fizycznym telefonie ustaw IP komputera w sieci LAN (np. http://192.168.0.10:8000/).
     */
    private const val BASE_URL = "http://192.168.1.120:8000/"

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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
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
}
