package com.example.herdmanagementmobile.data.sync

import kotlinx.serialization.Serializable

/**
 * Treść wpisu kolejki odwołującego się do istniejącego rekordu. Identyfikator zdalny
 * i aktualne dane są ustalane w chwili wysyłki.
 */
@Serializable
data class LocalIdPayload(val localId: Long)

/** Służy do odczytu samego pola `id` z treści wpisu zawierającej DTO. */
@Serializable
data class LocalIdOnly(val id: Long = 0L)

/** Treść odpowiedzi błędu backendu FastAPI (`{"detail": "..."}`). */
@Serializable
data class ErrorDetail(val detail: String? = null)

/** Stałe opisujące wpisy kolejki synchronizacji i parametry jej przetwarzania. */
object SyncContract {

    object Entity {
        const val ANIMAL = "animal"
        const val EVENT = "event"
        const val TREATMENT = "treatment"
        const val LISTING = "listing"
        const val INQUIRY = "inquiry"
    }

    object Operation {
        const val CREATE = "CREATE"
        const val UPDATE = "UPDATE"
        const val DELETE = "DELETE"

        /** Zgłoszenie zdarzenia do IRZplus, dodawane wyłącznie po potwierdzeniu użytkownika. */
        const val IRZ_SUBMIT = "IRZ_SUBMIT"

        /** Przekazanie wyniku zgłoszenia IRZplus do backendu aplikacji. */
        const val IRZ_RESULT = "IRZ_RESULT"

        /** Publikacja ogłoszenia, dodawana wyłącznie po potwierdzeniu użytkownika (wymaganie F12). */
        const val PUBLISH = "PUBLISH"

        const val WITHDRAW = "WITHDRAW"
        const val MARK_SOLD = "MARK_SOLD"

        /** Weryfikacja ogłoszenia w rejestrze IRZplus (wymaganie F14). */
        const val VERIFY = "VERIFY"
    }

    object Status {
        const val PENDING = "PENDING"
        const val DONE = "DONE"
        const val FAILED = "FAILED"
    }

    /** Maksymalna liczba prób dla błędów serwera; błędy sieci nie są wliczane do limitu. */
    const val MAX_RETRIES = 5
    const val UNIQUE_WORK_NAME = "herd-sync"
    const val RETRY_WORK_NAME = "herd-sync-retry"
    const val PERIODIC_WORK_NAME = "herd-sync-periodic"

    /** Minimalny interwał zadań cyklicznych dopuszczany przez WorkManager. */
    const val PERIODIC_MINUTES = 15L

    /** Parametry wykładniczego opóźnienia ponowień: 30 s, 60 s, 2 min, 4 min, maks. 5 min. */
    const val RETRY_BASE_SECONDS = 30L
    const val RETRY_MAX_SECONDS = 5 * 60L

    fun retryDelaySeconds(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 10)
        return (RETRY_BASE_SECONDS shl exponent).coerceAtMost(RETRY_MAX_SECONDS)
    }
}
