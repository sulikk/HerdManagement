package com.example.herdmanagementmobile.ui.screens.animals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.Sex
import com.example.herdmanagementmobile.ui.common.DateField
import com.example.herdmanagementmobile.ui.common.SectionCard
import com.example.herdmanagementmobile.ui.common.PastOrToday
import com.example.herdmanagementmobile.ui.common.toLocalDate
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Formularz dodawania i edycji zwierzęcia. Pola pogrupowano tematycznie, a akcje
 * skanowania umieszczono bezpośrednio przy polach, które wypełniają. Przycisk zapisu
 * znajduje się w dolnym pasku, dostępnym bez przewijania.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalFormScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    onScanClick: () -> Unit,
    onNfcClick: () -> Unit,
    scannedEarTag: String?,
    onScanResultConsumed: () -> Unit,
    viewModel: AnimalFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(scannedEarTag) {
        if (scannedEarTag != null) {
            viewModel.onScanned(scannedEarTag)
            onScanResultConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEdit) "Edytuj zwierzę" else "Dodaj zwierzę") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    state.saveError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Default,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { viewModel.save(onSaved) },
                        enabled = !state.isSaving && state.earTag.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (viewModel.isEdit) "Zapisz zmiany" else "Dodaj zwierzę")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(
                title = "Identyfikacja",
                hint = "Numer z kolczyka urzędowego. Możesz go zeskanować albo odczytać z chipa NFC.",
            ) {
                OutlinedTextField(
                    value = state.earTag,
                    onValueChange = viewModel::onEarTagChange,
                    label = { Text("Numer kolczyka *") },
                    placeholder = { Text("PL000123456784") },
                    supportingText = {
                        Text(
                            state.earTagError
                                ?: "PL + 12 cyfr; ostatnia jest cyfrą kontrolną.",
                        )
                    },
                    isError = state.earTagError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                viewModel.onScanRequested(ScanTarget.EAR_TAG)
                                onScanClick()
                            }) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Skanuj kolczyk zwierzęcia",
                                )
                            }
                            IconButton(onClick = {
                                // Ustawienie celu zapobiega przypisaniu odczytu NFC do pola matki.
                                viewModel.onScanRequested(ScanTarget.EAR_TAG)
                                onNfcClick()
                            }) {
                                Icon(Icons.Default.Nfc, contentDescription = "Odczytaj z NFC")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Dane zwierzęcia") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Imię") },
                    supportingText = { Text("Pomaga rozpoznać sztukę na liście.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                DateField(
                    label = "Data urodzenia *",
                    date = state.birthDate,
                    onPick = { showDatePicker = true },
                    isError = state.birthDateError != null,
                    supportingText = state.birthDateError
                        ?: "Wymagana przy zgłoszeniu urodzenia do IRZplus.",
                )

                OutlinedTextField(
                    value = state.breed,
                    onValueChange = viewModel::onBreedChange,
                    label = { Text("Rasa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(text = "Płeć", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Sex.entries.forEachIndexed { index, sex ->
                        SegmentedButton(
                            selected = state.sex == sex,
                            onClick = { viewModel.onSexChange(sex) },
                            shape = SegmentedButtonDefaults.itemShape(index, Sex.entries.size),
                        ) { Text(sex.label()) }
                    }
                }
            }

            SectionCard(
                title = "Pochodzenie",
                hint = "Numer matki jest wymagany przy zgłoszeniu urodzenia do IRZplus.",
            ) {
                OutlinedTextField(
                    value = state.motherEarTag,
                    onValueChange = viewModel::onMotherEarTagChange,
                    label = { Text("Kolczyk matki") },
                    placeholder = { Text("PL000123456784") },
                    isError = state.motherError != null,
                    supportingText = {
                        Text(state.motherError ?: "Możesz go zeskanować, tak jak kolczyk zwierzęcia.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                viewModel.onScanRequested(ScanTarget.MOTHER)
                                onScanClick()
                            }) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Skanuj kolczyk matki",
                                )
                            }
                            IconButton(onClick = {
                                viewModel.onScanRequested(ScanTarget.MOTHER)
                                onNfcClick()
                            }) {
                                Icon(
                                    Icons.Default.Nfc,
                                    contentDescription = "Odczytaj kolczyk matki z NFC",
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Lokalizacja") {
                OutlinedTextField(
                    value = state.herdId,
                    onValueChange = viewModel::onHerdIdChange,
                    label = { Text("Numer siedziby stada") },
                    placeholder = { Text("PL000111222") },
                    supportingText = {
                        Text(state.herdIdError ?: "PL + 9 cyfr. Domyślnie z ustawień gospodarstwa.")
                    },
                    isError = state.herdIdError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = "* pola wymagane",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.birthDate
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli(),
            selectableDates = PastOrToday,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onBirthDateChange(datePickerState.selectedDateMillis?.toLocalDate())
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Anuluj") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun Sex.label(): String = when (this) {
    Sex.FEMALE -> "Samica"
    Sex.MALE -> "Samiec"
    Sex.OTHER -> "Inne"
}
