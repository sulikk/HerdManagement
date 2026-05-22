package com.example.herdmanagementmobile.ui.screens.animals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.Sex
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
            viewModel.onEarTagChange(scannedEarTag)
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.earTag,
                onValueChange = viewModel::onEarTagChange,
                label = { Text("Numer kolczyka *") },
                placeholder = { Text("PL000123456789") },
                supportingText = { Text(state.earTagError ?: "Format: PL + 12 cyfr") },
                isError = state.earTagError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScanClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Skanuj kod")
                }
                OutlinedButton(onClick = onNfcClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Nfc, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("NFC")
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Imię") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.birthDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        ?: "Data urodzenia (opcjonalnie)",
                )
            }

            OutlinedTextField(
                value = state.breed,
                onValueChange = viewModel::onBreedChange,
                label = { Text("Rasa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Płeć",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Sex.entries.forEachIndexed { index, sex ->
                    SegmentedButton(
                        selected = state.sex == sex,
                        onClick = { viewModel.onSexChange(sex) },
                        shape = SegmentedButtonDefaults.itemShape(index, Sex.entries.size),
                    ) { Text(sex.label()) }
                }
            }

            OutlinedTextField(
                value = state.motherEarTag,
                onValueChange = viewModel::onMotherEarTagChange,
                label = { Text("Kolczyk matki") },
                placeholder = { Text("PL000123456789") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.herdId,
                onValueChange = viewModel::onHerdIdChange,
                label = { Text("ID siedziby stada") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.saveError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Default,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.isSaving && state.earTag.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (viewModel.isEdit) "Zapisz zmiany" else "Dodaj zwierzę")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.birthDate
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = datePickerState.selectedDateMillis?.let { millis ->
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    viewModel.onBirthDateChange(date)
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
