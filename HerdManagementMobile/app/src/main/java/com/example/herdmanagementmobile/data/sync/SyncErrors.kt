package com.example.herdmanagementmobile.data.sync

import retrofit2.HttpException
import java.io.IOException

/** Kategoria błędu wpisu kolejki, określająca strategię ponawiania. */
enum class SyncErrorKind {
    /** Brak połączenia lub przekroczenie czasu — ponawiany bez limitu. */
    NETWORK,

    /** Przejściowy błąd serwera (5xx, 408, 429) — ponawiany do [SyncContract.MAX_RETRIES] razy. */
    SERVER,

    /** Błąd danych (pozostałe 4xx, błąd parsowania) — nie jest ponawiany. */
    PERMANENT,
}

fun classifySyncError(error: Throwable): SyncErrorKind = when (error) {
    is HttpException -> when (error.code()) {
        408, 429, in 500..599 -> SyncErrorKind.SERVER
        else -> SyncErrorKind.PERMANENT
    }
    is IOException -> SyncErrorKind.NETWORK
    else -> SyncErrorKind.PERMANENT
}

/** Zwraca opis błędu zapisywany w kolumnie `lastError`. */
fun describeSyncError(error: Throwable): String = when (error) {
    is HttpException -> "HTTP ${error.code()}: ${error.response()?.errorBody()?.string()?.take(200) ?: error.message()}"
    else -> error.message ?: error::class.java.simpleName
}
