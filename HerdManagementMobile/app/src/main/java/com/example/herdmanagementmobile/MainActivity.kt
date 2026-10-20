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
        syncManager.scheduleSync()
        // Cykliczne pobieranie zmian wprowadzonych poza urządzeniem (np. przez asystenta LLM).
        syncManager.schedulePeriodicSync()
        setContent {
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
