package com.example.herdmanagementmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.herdmanagementmobile.data.settings.AppearanceRepository
import com.example.herdmanagementmobile.data.settings.AppearanceSettings
import com.example.herdmanagementmobile.data.sync.SyncManager
import com.example.herdmanagementmobile.ui.navigation.HerdShell
import com.example.herdmanagementmobile.ui.theme.HerdManagementMobileTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var appearance: AppearanceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Wypchnij zaległe wpisy z poprzedniej sesji i od razu pobierz zmiany z backendu.
        syncManager.scheduleSync()
        // Odświeżanie w tle — zmiany powstałe poza telefonem (asystent LLM, inne
        // urządzenie) nie mają własnego wyzwalacza po stronie aplikacji.
        syncManager.schedulePeriodicSync()
        setContent {
            // Wyglad czytamy z ustawien, wiec zmiana motywu dziala od razu,
            // bez restartu aplikacji.
            val look by appearance.settings.collectAsState(initial = AppearanceSettings())
            HerdManagementMobileTheme(
                darkTheme = look.darkTheme(isSystemInDarkTheme()),
                highContrast = look.highContrast,
                dynamicColor = look.dynamicColor,
            ) {
                HerdShell()
            }
        }
    }
}
