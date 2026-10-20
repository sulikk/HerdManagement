package com.example.herdmanagementmobile.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.data.settings.ThemeMode
import kotlinx.coroutines.launch

/**
 * Ekran ustawień. Dane gospodarstwa wymagają walidacji i jawnego zapisu, natomiast
 * ustawienia wyglądu są stosowane natychmiast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FarmSection(
                state = state,
                onProducerChange = viewModel::onProducerChange,
                onHoldingChange = viewModel::onHoldingChange,
                onSave = {
                    viewModel.save {
                        scope.launch { snackbarHostState.showSnackbar("Zapisano dane gospodarstwa.") }
                    }
                },
            )

            AppearanceSection(
                themeMode = appearance.themeMode,
                highContrast = appearance.highContrast,
                dynamicColor = appearance.dynamicColor,
                onThemeMode = viewModel::onThemeMode,
                onHighContrast = viewModel::onHighContrast,
                onDynamicColor = viewModel::onDynamicColor,
            )

            AboutSection()
        }
    }
}

@Composable
private fun FarmSection(
    state: SettingsState,
    onProducerChange: (String) -> Unit,
    onHoldingChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    SettingsCard(title = "Gospodarstwo") {
        Text(
            text = "Te dwa numery identyfikują Cię wobec systemu IRZplus. " +
                "Aplikacja wysyła je z każdym zgłoszeniem zdarzenia.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.producerNumber,
            onValueChange = onProducerChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Numer producenta") },
            singleLine = true,
            isError = state.producerError != null,
        )

        OutlinedTextField(
            value = state.holdingNumber,
            onValueChange = onHoldingChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Numer siedziby stada") },
            singleLine = true,
            isError = state.holdingError != null,
        )

        Button(onClick = onSave, enabled = state.loaded, modifier = Modifier.fillMaxWidth()) {
            Text("Zapisz dane gospodarstwa")
        }
    }
}

@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    highContrast: Boolean,
    dynamicColor: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onHighContrast: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
    SettingsCard(title = "Wygląd") {
        Text(
            text = "Motyw",
            style = MaterialTheme.typography.titleSmall,
        )
        Column(modifier = Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                ChoiceRow(
                    label = mode.label,
                    selected = themeMode == mode,
                    onSelect = { onThemeMode(mode) },
                )
            }
        }

        HorizontalDivider()

        SwitchRow(
            title = "Wysoki kontrast (czarno-biały)",
            description = "Usuwa kolory i zostawia samą różnicę jasności. " +
                "W pełnym słońcu ekran jest wtedy czytelniejszy.",
            checked = highContrast,
            onChange = onHighContrast,
        )

        // Kolorystyka dynamiczna jest dostępna od Androida 12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow(
                title = "Kolory z tapety systemu",
                description = "Dopasowuje barwy aplikacji do motywu Androida. " +
                    "Wyłączone przy wysokim kontraście.",
                checked = dynamicColor,
                enabled = !highContrast,
                onChange = onDynamicColor,
            )
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun AboutSection() {
    SettingsCard(title = "O aplikacji") {
        InfoRow("Nazwa", "Inteligentny asystent hodowcy bydła")
        InfoRow("Przeznaczenie", "Ewidencja stada i zgłoszenia do IRZplus (ARiMR)")
        Text(
            text = "Aplikacja nie zastępuje Portalu IRZplus — pomaga przygotować " +
                "i wysłać zgłoszenia w ustawowym terminie 7 dni.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(0.dp))
            content()
        }
    }
}
