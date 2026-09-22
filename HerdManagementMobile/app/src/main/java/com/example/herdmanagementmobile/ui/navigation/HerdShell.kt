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

/**
 * Główne sekcje aplikacji — pozycje dolnego paska nawigacji.
 *
 * Material przewiduje pasek dla trzech do pięciu równorzędnych miejsc i dokładnie
 * tyle ich tu jest. Wcześniej wszystkie siedziały jako ikony w pasku górnym obok
 * chipa synchronizacji, co na telefonie oznaczało sześć celów dotyku w jednym
 * rzędzie — wbrew wymaganiu N2 (obsługa w rękawicach).
 */
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
 * Powłoka aplikacji: dolna nawigacja plus graf ekranów.
 *
 * Pasek pokazuje się wyłącznie na ekranach głównych. Na kartach, formularzach
 * i skanerze znika — tam użytkownik jest „w głąb” zadania i ma wracać przyciskiem
 * wstecz, a nie przeskakiwać do innej sekcji.
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
                                // Licznik zaległych terminów zgłoszeń — hodowca widzi,
                                // że coś go goni, bez wchodzenia w sekcję.
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

/**
 * Przejście między sekcjami bez narastania historii.
 *
 * Bez `popUpTo` każde kliknięcie w pasku dokładałoby ekran na stos i wyjście
 * z aplikacji wymagałoby tylu naciśnięć „wstecz”, ile było przełączeń.
 */
private fun NavHostController.switchTo(route: Route) {
    navigate(route.path) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
