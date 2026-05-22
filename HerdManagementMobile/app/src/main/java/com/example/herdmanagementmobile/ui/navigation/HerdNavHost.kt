package com.example.herdmanagementmobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.herdmanagementmobile.ui.screens.animals.AnimalListScreen

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
            )
        }
        // TODO: AnimalDetail, Scanner, Assistant, Listings — dorobimy w kolejnych etapach.
    }
}
