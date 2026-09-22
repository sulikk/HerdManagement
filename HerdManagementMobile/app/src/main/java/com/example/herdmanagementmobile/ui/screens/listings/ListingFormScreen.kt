package com.example.herdmanagementmobile.ui.screens.listings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Formularz ogłoszenia sprzedażowego (zadanie 9).
 *
 * Realizuje F13: kolczyk, nazwa i rasa przychodzą z karty zwierzęcia i nie są do
 * przepisywania — hodowca uzupełnia wyłącznie cenę i opis, który też ma już szkic.
 * Zapis tworzy **szkic**; publikacja jest osobną decyzją na karcie ogłoszenia (F12).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingFormScreen(
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
    viewModel: ListingFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Edytuj ogłoszenie" else "Wystaw na sprzedaż") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimalSummary(
                earTag = state.earTag,
                name = state.animalName,
                breed = state.breed,
            )

            if (state.blockedByExisting) {
                Text(
                    text = "To zwierzę ma już aktywne ogłoszenie. Wycofaj je albo edytuj " +
                        "istniejące — jedna sztuka nie może być wystawiona dwa razy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = state.price,
                onValueChange = viewModel::onPriceChange,
                label = { Text("Cena (zł) *") },
                singleLine = true,
                isError = state.priceError != null,
                supportingText = state.priceError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Opis") },
                supportingText = { Text("Wstępnie wypełniony danymi z karty zwierzęcia.") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.saving && !state.blockedByExisting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.editing) "Zapisz zmiany" else "Zapisz jako szkic")
            }

            Text(
                text = "Ogłoszenie nie stanie się widoczne od razu — publikujesz je " +
                    "osobno, po sprawdzeniu treści.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnimalSummary(earTag: String, name: String?, breed: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = name?.takeIf { it.isNotBlank() } ?: "Zwierzę",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = listOfNotNull(earTag.takeIf { it.isNotBlank() }, breed).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
