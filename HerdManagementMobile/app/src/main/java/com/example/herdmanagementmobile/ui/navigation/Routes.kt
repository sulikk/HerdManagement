package com.example.herdmanagementmobile.ui.navigation

sealed class Route(val path: String) {
    data object AnimalList : Route("animals")
    data object AnimalDetail : Route("animals/{animalId}") {
        const val ARG = "animalId"
        fun build(animalId: Long): String = "animals/$animalId"
    }
    data object Scanner : Route("scanner")
    data object Assistant : Route("assistant")
    data object Listings : Route("listings")
}
