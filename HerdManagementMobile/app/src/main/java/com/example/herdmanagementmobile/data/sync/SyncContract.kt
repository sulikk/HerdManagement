package com.example.herdmanagementmobile.data.sync

/** Wspólne stałe opisujące wpisy w kolejce synchronizacji. */
object SyncContract {

    object Entity {
        const val ANIMAL = "animal"
        const val EVENT = "event"
        const val TREATMENT = "treatment"
    }

    object Operation {
        const val CREATE = "CREATE"
    }

    object Status {
        const val PENDING = "PENDING"
        const val DONE = "DONE"
        const val FAILED = "FAILED"
    }

    const val MAX_RETRIES = 5
    const val UNIQUE_WORK_NAME = "herd-sync"
}
