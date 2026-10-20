package com.example.herdmanagementmobile.di

import javax.inject.Qualifier

/**
 * Kwalifikator adresu backendu (`BuildConfig.BASE_URL`). Adres jest wykorzystywany
 * również przez synchronizację do unieważniania znacznika pobrania po zmianie serwera.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl
