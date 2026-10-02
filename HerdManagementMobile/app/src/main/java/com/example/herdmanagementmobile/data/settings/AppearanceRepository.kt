package com.example.herdmanagementmobile.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

enum class ThemeMode(val label: String) {
    SYSTEM("Jak w systemie"),
    LIGHT("Jasny"),
    DARK("Ciemny"),
}

/**
 * Ustawienia wyglądu aplikacji.
 *
 * [highContrast] to tryb czarno-biały pomyślany pod pracę w polu — wymaganie N2
 * mówi o wysokim kontraście, a ekran telefonu w pełnym słońcu gubi subtelne
 * różnice odcieni. Monochromatyczna paleta jest wtedy czytelniejsza niż
 * kolorowa, bo liczy się jasność, nie barwa.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val highContrast: Boolean = false,
    /** Kolory pobrane z tapety systemu (Material You, Android 12+). */
    val dynamicColor: Boolean = true,
) {
    /** Czy użyć ciemnego wariantu, przy danym stanie motywu systemowego. */
    fun darkTheme(systemInDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

@Singleton
class AppearanceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val contrastKey = booleanPreferencesKey("high_contrast")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")

    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map { prefs ->
        AppearanceSettings(
            themeMode = prefs[themeKey]
                ?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            highContrast = prefs[contrastKey] ?: false,
            dynamicColor = prefs[dynamicKey] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appearanceDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.appearanceDataStore.edit { it[contrastKey] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.appearanceDataStore.edit { it[dynamicKey] = enabled }
    }
}
