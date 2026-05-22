package com.example.herdmanagementmobile.ui.screens.treatments

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class DatePickerTarget { TREATMENT, WITHDRAWAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentFormScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: TreatmentFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var activePicker by remember { mutableStateOf<DatePickerTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dodaj leczenie") },
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
                value = state.drug,
                onValueChange = viewModel::onDrugChange,
                label = { Text("Lek *") },
                placeholder = { Text("np. Amoksycylina") },
                isError = state.drugError != null,
                supportingText = { state.drugError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.dose,
                onValueChange = viewModel::onDoseChange,
                label = { Text("Dawka") },
                placeholder = { Text("np. 5 ml IM") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.vet,
                onValueChange = viewModel::onVetChange,
                label = { Text("Lekarz weterynarii") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(
                label = "Data leczenia",
                value = state.treatmentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                onClick = { activePicker = DatePickerTarget.TREATMENT },
            )

            DateField(
                label = "Karencja do",
                value = state.withdrawalUntil
                    ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    ?: "Nie ustawiono",
                onClick = { activePicker = DatePickerTarget.WITHDRAWAL },
                onClear = state.withdrawalUntil?.let {
                    { viewModel.onWithdrawalChange(null) }
                },
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notatki") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            state.saveError?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.isSaving && state.drug.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Zapisz leczenie")
            }
        }
    }

    activePicker?.let { target ->
        val initialDate = when (target) {
            DatePickerTarget.TREATMENT -> state.treatmentDate
            DatePickerTarget.WITHDRAWAL -> state.withdrawalUntil ?: state.treatmentDate
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date: LocalDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        when (target) {
                            DatePickerTarget.TREATMENT -> viewModel.onDateChange(date)
                            DatePickerTarget.WITHDRAWAL -> viewModel.onWithdrawalChange(date)
                        }
                    }
                    activePicker = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { activePicker = null }) { Text("Anuluj") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text = "$label: $value", modifier = Modifier.weight(1f))
        if (onClear != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = "Wyczyść datę")
            }
        }
    }
}
