package com.example.herdmanagementmobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * Monochromatyczna paleta wysokiego kontrastu (wymaganie N2), przeznaczona do pracy
 * w pełnym słońcu. Statusy w aplikacji nie są przekazywane wyłącznie kolorem,
 * dlatego paleta nie ogranicza prezentowanych informacji.
 */
private val HighContrastLight = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF2B2B2B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF2B2B2B),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF1F1F1F),
    surfaceContainerLow = Color(0xFFF5F5F5),
    outline = Color(0xFF2B2B2B),
    error = Color.Black,
    onError = Color.White,
    errorContainer = Color(0xFFDCDCDC),
    onErrorContainer = Color.Black,
)

private val HighContrastDark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2E2E2E),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFDDDDDD),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFDDDDDD),
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFE6E6E6),
    surfaceContainerLow = Color(0xFF141414),
    outline = Color(0xFFCCCCCC),
    error = Color.White,
    onError = Color.Black,
    errorContainer = Color(0xFF2E2E2E),
    onErrorContainer = Color.White,
)

@Composable
fun HerdManagementMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Tryb wysokiego kontrastu; ma pierwszeństwo przed kolorystyką dynamiczną. */
    highContrast: Boolean = false,
    /** Kolorystyka dynamiczna (Android 12+). */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        highContrast -> if (darkTheme) HighContrastDark else HighContrastLight

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}