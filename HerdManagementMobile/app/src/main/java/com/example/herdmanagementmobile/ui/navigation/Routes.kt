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

    data object Assistant : Route("assistant")
    data object Listings : Route("listings")
}
