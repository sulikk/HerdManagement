package com.example.herdmanagementmobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.herdmanagementmobile.ui.screens.animals.AnimalDetailScreen
import com.example.herdmanagementmobile.ui.screens.animals.AnimalFormScreen
import com.example.herdmanagementmobile.ui.screens.animals.AnimalListScreen
import com.example.herdmanagementmobile.ui.screens.assistant.AssistantScreen
import com.example.herdmanagementmobile.ui.screens.compliance.ComplianceScreen
import com.example.herdmanagementmobile.ui.screens.events.EventFormScreen
import com.example.herdmanagementmobile.ui.screens.listings.ListingDetailScreen
import com.example.herdmanagementmobile.ui.screens.listings.ListingFormScreen
import com.example.herdmanagementmobile.ui.screens.listings.ListingListScreen
import com.example.herdmanagementmobile.ui.screens.nfc.NfcScreen
import com.example.herdmanagementmobile.ui.screens.scanner.ScannerScreen
import com.example.herdmanagementmobile.ui.screens.settings.SettingsScreen
import com.example.herdmanagementmobile.ui.screens.statistics.StatisticsScreen
import com.example.herdmanagementmobile.ui.screens.treatments.TreatmentFormScreen

@Composable
fun HerdNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.AnimalList.path,
        modifier = modifier,
    ) {
        composable(Route.AnimalList.path) {
            AnimalListScreen(
                onAnimalClick = { animalId ->
                    navController.navigate(Route.AnimalDetail.build(animalId))
                },
                onAddClick = {
                    navController.navigate(Route.AnimalForm.build())
                },
                onSettingsClick = {
                    navController.navigate(Route.Settings.path)
                },
            )
        }
        composable(
            route = Route.AnimalDetail.path,
            arguments = listOf(
                navArgument(Route.AnimalDetail.ARG) { type = NavType.LongType },
            ),
        ) {
            AnimalDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { animalId ->
                    navController.navigate(Route.AnimalForm.build(animalId))
                },
                onAddEvent = { animalId ->
                    navController.navigate(Route.EventForm.build(animalId))
                },
                onAddTreatment = { animalId ->
                    navController.navigate(Route.TreatmentForm.build(animalId))
                },
                onSellClick = { animalId, existingListingId ->
                    if (existingListingId != null) {
                        navController.navigate(Route.ListingDetail.build(existingListingId))
                    } else {
                        navController.navigate(Route.ListingForm.build(animalId))
                    }
                },
                onDeleted = {
                    navController.popBackStack(Route.AnimalList.path, inclusive = false)
                },
            )
        }
        composable(
            route = Route.AnimalForm.path,
            arguments = listOf(
                navArgument(Route.AnimalForm.ARG) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val scannedEarTag by entry.savedStateHandle
                .getStateFlow<String?>(Route.Scanner.RESULT_EAR_TAG, null)
                .collectAsState()

            AnimalFormScreen(
                scannedEarTag = scannedEarTag,
                onScanResultConsumed = {
                    entry.savedStateHandle[Route.Scanner.RESULT_EAR_TAG] = null
                },
                onScanClick = { navController.navigate(Route.Scanner.path) },
                onNfcClick = { navController.navigate(Route.Nfc.path) },
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = Route.EventForm.path,
            arguments = listOf(
                navArgument(Route.EventForm.ARG_ANIMAL) { type = NavType.LongType },
            ),
        ) {
            EventFormScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = Route.TreatmentForm.path,
            arguments = listOf(
                navArgument(Route.TreatmentForm.ARG_ANIMAL) { type = NavType.LongType },
            ),
        ) {
            TreatmentFormScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Route.Scanner.path) {
            ScannerScreen(
                onScanned = { earTag ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Route.Scanner.RESULT_EAR_TAG, earTag)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Route.Nfc.path) {
            NfcScreen(
                onScanned = { earTag ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Route.Scanner.RESULT_EAR_TAG, earTag)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Route.Compliance.path) {
            ComplianceScreen(
                onItemClick = { animalId ->
                    navController.navigate(Route.AnimalDetail.build(animalId))
                },
            )
        }
        composable(Route.Settings.path) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Statistics.path) {
            StatisticsScreen()
        }
        composable(Route.Assistant.path) {
            AssistantScreen()
        }
        composable(Route.Listings.path) {
            ListingListScreen(
                onListingClick = { listingId ->
                    navController.navigate(Route.ListingDetail.build(listingId))
                },
            )
        }
        composable(
            route = Route.ListingDetail.path,
            arguments = listOf(
                navArgument(Route.ListingDetail.ARG) { type = NavType.LongType },
            ),
        ) {
            ListingDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { animalId, listingId ->
                    navController.navigate(Route.ListingForm.build(animalId, listingId))
                },
                onDeleted = {
                    navController.popBackStack(Route.Listings.path, inclusive = false)
                },
            )
        }
        composable(
            route = Route.ListingForm.path,
            arguments = listOf(
                navArgument(Route.ListingForm.ARG_ANIMAL) { type = NavType.LongType },
                navArgument(Route.ListingForm.ARG_LISTING) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            ListingFormScreen(
                onSaved = { listingId ->
                    // Karta ogłoszenia umożliwia publikację (F12) i weryfikację w IRZplus (F14).
                    navController.navigate(Route.ListingDetail.build(listingId)) {
                        popUpTo(Route.ListingForm.path) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
