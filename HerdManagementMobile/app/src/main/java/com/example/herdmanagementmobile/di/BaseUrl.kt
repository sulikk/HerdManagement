package com.example.herdmanagementmobile.di

import javax.inject.Qualifier

/**
 * Adres backendu, z którym rozmawia aplikacja (`BuildConfig.BASE_URL`).
 *
 * Wstrzykujemy go jawnie, bo poza warstwą sieci potrzebuje go też synchronizacja:
 * po zmianie serwera (inne IP, odtworzona baza) znacznik „pobrano do…” z poprzedniego
 * adresu jest bezwartościowy i trzeba go wyzerować, żeby telefon zaciągnął pełną migawkę.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl
