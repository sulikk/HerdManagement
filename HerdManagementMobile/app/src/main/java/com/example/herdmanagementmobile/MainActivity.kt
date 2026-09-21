package com.example.herdmanagementmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.herdmanagementmobile.data.sync.SyncManager
import com.example.herdmanagementmobile.ui.navigation.HerdNavHost
import com.example.herdmanagementmobile.ui.theme.HerdManagementMobileTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Wypchnij zaległe wpisy z poprzedniej sesji i od razu pobierz zmiany z backendu.
        syncManager.scheduleSync()
        // Odświeżanie w tle — zmiany powstałe poza telefonem (asystent LLM, inne
        // urządzenie) nie mają własnego wyzwalacza po stronie aplikacji.
        syncManager.schedulePeriodicSync()
        setContent {
            HerdManagementMobileTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()
                    HerdNavHost(
                        navController = navController,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}
