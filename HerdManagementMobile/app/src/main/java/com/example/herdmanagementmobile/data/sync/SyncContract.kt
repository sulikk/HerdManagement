package com.example.herdmanagementmobile.data.sync

import kotlinx.serialization.Serializable

/** Payload operacji DELETE — wystarczy lokalne id, zdalne bierzemy z mapy id. */
@Serializable
data class DeletePayload(val localId: Long)

/** Wspólne stałe opisujące wpisy w kolejce synchronizacji. */
object SyncContract {

    object Entity {
        const val ANIMAL = "animal"
        const val EVENT = "event"
        const val TREATMENT = "treatment"
    }

    object Operation {
        const val CREATE = "CREATE"
        const val UPDATE = "UPDATE"
        const val DELETE = "DELETE"
    }

    object Status {
        const val PENDING = "PENDING"
        const val DONE = "DONE"
        const val FAILED = "FAILED"
    }

    const val MAX_RETRIES = 5
    const val UNIQUE_WORK_NAME = "herd-sync"
}
