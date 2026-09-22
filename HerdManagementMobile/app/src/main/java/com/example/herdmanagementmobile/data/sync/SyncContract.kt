package com.example.herdmanagementmobile.data.sync

import kotlinx.serialization.Serializable

/**
 * Payload operacji odwołujących się do istniejącego rekordu (DELETE, IRZ_SUBMIT, IRZ_RESULT):
 * wystarczy lokalne id — zdalne bierzemy z mapy id, aktualne dane z lokalnej bazy.
 */
@Serializable
data class LocalIdPayload(val localId: Long)

/** Odczyt samego pola `id` z payloadu DTO (pozostałe pola ignorowane przez Json). */
@Serializable
data class LocalIdOnly(val id: Long = 0L)

/** Komunikat bledu z backendu FastAPI (`{"detail": "..."}`). */
@Serializable
data class ErrorDetail(val detail: String? = null)

/** Wspólne stałe opisujące wpisy w kolejce synchronizacji. */
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

        /** Zgłoszenie zdarzenia do IRZplus — tylko po potwierdzeniu przez użytkownika. */
        const val IRZ_SUBMIT = "IRZ_SUBMIT"

        /** Przekazanie wyniku zgłoszenia IRZ do backendu aplikacji. */
        const val IRZ_RESULT = "IRZ_RESULT"

        /** Publikacja ogłoszenia — wyłącznie po jawnym potwierdzeniu (F12). */
        const val PUBLISH = "PUBLISH"

        const val WITHDRAW = "WITHDRAW"
        const val MARK_SOLD = "MARK_SOLD"

        /** Weryfikacja ogłoszenia wobec rejestru IRZplus — odznaka F14. */
        const val VERIFY = "VERIFY"
    }

    object Status {
        const val PENDING = "PENDING"
        const val DONE = "DONE"
        const val FAILED = "FAILED"
    }

    /** Limit prób dla błędów serwera (5xx). Brak sieci nie wyczerpuje limitu. */
    const val MAX_RETRIES = 5
    const val UNIQUE_WORK_NAME = "herd-sync"
    const val RETRY_WORK_NAME = "herd-sync-retry"
    const val PERIODIC_WORK_NAME = "herd-sync-periodic"

    /** 15 min to minimum, jakie dopuszcza WorkManager dla zadan cyklicznych. */
    const val PERIODIC_MINUTES = 15L

    /** Backoff ponowień: 30 s, 60 s, 2 min, 4 min, maksymalnie 5 min. */
    const val RETRY_BASE_SECONDS = 30L
    const val RETRY_MAX_SECONDS = 5 * 60L

    fun retryDelaySeconds(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 10)
        return (RETRY_BASE_SECONDS shl exponent).coerceAtMost(RETRY_MAX_SECONDS)
    }
}
