package com.example.herdmanagementmobile.data.sync

import retrofit2.HttpException
import java.io.IOException

/** Jak kolejka ma potraktować błąd wpisu. */
enum class SyncErrorKind {
    /** Brak sieci / timeout — ponawiamy bez limitu (offline-first). */
    NETWORK,

    /** Chwilowy problem serwera (5xx, 408, 429) — ponawiamy do [SyncContract.MAX_RETRIES]. */
    SERVER,

    /** Błąd danych (pozostałe 4xx, błąd parsowania) — ponawianie nic nie da. */
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

/** Czytelny opis błędu do kolumny lastError. */
fun describeSyncError(error: Throwable): String = when (error) {
    is HttpException -> "HTTP ${error.code()}: ${error.response()?.errorBody()?.string()?.take(200) ?: error.message()}"
    else -> error.message ?: error::class.java.simpleName
}
