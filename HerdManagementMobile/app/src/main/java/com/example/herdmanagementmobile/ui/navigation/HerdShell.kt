package com.example.herdmanagementmobile.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** Główne sekcje aplikacji prezentowane w dolnym pasku nawigacji. */
private enum class Section(
    val route: Route,
    val label: String,
    val icon: ImageVector,
) {
    HERD(Route.AnimalList, "Stado", Icons.Default.Pets),
    COMPLIANCE(Route.Compliance, "Terminy", Icons.Default.EventAvailable),
    LISTINGS(Route.Listings, "Ogłoszenia", Icons.Default.Storefront),
    STATISTICS(Route.Statistics, "Statystyki", Icons.Default.BarChart),
    ASSISTANT(Route.Assistant, "Asystent", Icons.Default.Psychology),
}

/**
 * Powłoka aplikacji: dolny pasek nawigacji i graf ekranów. Pasek jest widoczny
 * wyłącznie na ekranach głównych sekcji.
 */
@Composable
fun HerdShell(
    navController: NavHostController = rememberNavController(),
    viewModel: ShellViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val onSection = Section.entries.any { section ->
        destination?.hierarchy?.any { it.route == section.route.path } == true
    }
    val dueCount by viewModel.dueCount.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (onSection) {
                NavigationBar {
                    Section.entries.forEach { section ->
                        val selected = destination?.hierarchy
                            ?.any { it.route == section.route.path } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.switchTo(section.route) },
                            label = { Text(section.label) },
                            icon = {
                                // Licznik zgłoszeń wymagających reakcji.
                                if (section == Section.COMPLIANCE && dueCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$dueCount") } }) {
                                        Icon(section.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(section.icon, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        HerdNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

/** Przełącza sekcję bez odkładania kolejnych ekranów na stos nawigacji. */
private fun NavHostController.switchTo(route: Route) {
    navigate(route.path) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
