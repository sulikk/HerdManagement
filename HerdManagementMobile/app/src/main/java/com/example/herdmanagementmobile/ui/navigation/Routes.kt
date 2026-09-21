package com.example.herdmanagementmobile.ui.navigation

sealed class Route(val path: String) {
    data object AnimalList : Route("animals")

    data object AnimalDetail : Route("animals/{animalId}") {
        const val ARG = "animalId"
        fun build(animalId: Long): String = "animals/$animalId"
    }

    data object AnimalForm : Route("animals/form?animalId={animalId}") {
        const val ARG = "animalId"
        fun build(animalId: Long? = null): String =
            if (animalId == null) "animals/form" else "animals/form?animalId=$animalId"
    }

    data object EventForm : Route("animals/{animalId}/events/new") {
        const val ARG_ANIMAL = "animalId"
        fun build(animalId: Long): String = "animals/$animalId/events/new"
    }

    data object TreatmentForm : Route("animals/{animalId}/treatments/new") {
        const val ARG_ANIMAL = "animalId"
        fun build(animalId: Long): String = "animals/$animalId/treatments/new"
    }

    data object Scanner : Route("scanner") {
        const val RESULT_EAR_TAG = "scanned_ear_tag"
    }

    data object Nfc : Route("nfc")

    data object Compliance : Route("compliance")

    data object Settings : Route("settings")

    data object Statistics : Route("statistics")

    data object Assistant : Route("assistant")

    /** Lista ogłoszeń sprzedażowych hodowcy (zadanie 9). */
    data object Listings : Route("listings")

    data object ListingDetail : Route("listings/{listingId}") {
        const val ARG = "listingId"
        fun build(listingId: Long): String = "listings/$listingId"
    }

    /**
     * Formularz ogłoszenia. Zawsze wychodzi od zwierzęcia — dzięki temu da się
     * wypełnić pola danymi z jego karty (F13). `listingId` ustawione = edycja.
     */
    data object ListingForm : Route("animals/{animalId}/listings/form?listingId={listingId}") {
        const val ARG_ANIMAL = "animalId"
        const val ARG_LISTING = "listingId"
        fun build(animalId: Long, listingId: Long? = null): String =
            if (listingId == null) {
                "animals/$animalId/listings/form"
            } else {
                "animals/$animalId/listings/form?listingId=$listingId"
            }
    }
}
