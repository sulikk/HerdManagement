package com.example.herdmanagementmobile.ui.screens.treatments

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.herdmanagementmobile.domain.model.DeadlineText
import com.example.herdmanagementmobile.ui.common.DateField
import com.example.herdmanagementmobile.ui.common.SectionCard
import com.example.herdmanagementmobile.ui.common.PastOrToday
import com.example.herdmanagementmobile.ui.common.toLocalDate
import java.time.LocalDate
import java.time.ZoneOffset

private enum class DatePickerTarget { TREATMENT, WITHDRAWAL }

/**
 * Formularz leczenia (wymaganie F3). Po wyborze daty końca karencji prezentowany jest
 * czas jej trwania.
 */
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
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    state.saveError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { viewModel.save(onSaved) },
                        enabled = !state.isSaving && state.drug.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Zapisz leczenie")
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
                title = "Zabieg",
                hint = "Wpis trafia do dokumentacji leczenia gospodarstwa.",
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
                    placeholder = { Text("np. 5 ml domięśniowo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.vet,
                    onValueChange = viewModel::onVetChange,
                    label = { Text("Lekarz weterynarii") },
                    placeholder = { Text("np. lek. wet. A. Nowak") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(
                title = "Karencja",
                hint = "Okres, w którym mleko i mięso zwierzęcia nie mogą trafić do obrotu. " +
                    "Długość podaje ulotka leku albo lekarz weterynarii.",
            ) {
                DateField(
                    label = "Data zabiegu *",
                    date = state.treatmentDate,
                    onPick = { activePicker = DatePickerTarget.TREATMENT },
                    isError = state.dateError != null,
                    supportingText = state.dateError,
                )

                DateField(
                    label = "Karencja do",
                    date = state.withdrawalUntil,
                    onPick = { activePicker = DatePickerTarget.WITHDRAWAL },
                    placeholder = "Nie ustawiono",
                    isError = state.withdrawalError != null,
                    supportingText = state.withdrawalError ?: withdrawalSummary(state),
                    onClear = { viewModel.onWithdrawalChange(null) },
                )
            }

            SectionCard(title = "Notatki") {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text("Notatki (opcjonalnie)") },
                    placeholder = { Text("np. rozpoznanie, ćwiartka, zalecenia") },
                    minLines = 3,
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
            // Ograniczenie do dat przeszłych dotyczy wyłącznie daty zabiegu.
            selectableDates = if (target == DatePickerTarget.TREATMENT) {
                PastOrToday
            } else {
                NotBeforeTreatment(state.treatmentDate)
            },
        )
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = millis.toLocalDate()
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

/** Zwraca opis karencji: czas trwania i informację, czy nadal trwa. */
private fun withdrawalSummary(state: TreatmentFormState): String {
    val days = state.withdrawalDays ?: return "Zostaw puste, jeśli lek nie wymaga karencji."
    val length = "Karencja potrwa $days ${DeadlineText.days(days)} od dnia zabiegu"
    val remaining = state.withdrawalUntil?.let { until ->
        if (until.isBefore(LocalDate.now())) " — już zakończona." else "."
    } ?: "."
    return length + remaining
}

/** Ogranicza wybór daty końca karencji do dni nie wcześniejszych niż data zabiegu. */
@OptIn(ExperimentalMaterial3Api::class)
private class NotBeforeTreatment(
    private val treatmentDate: LocalDate,
) : androidx.compose.material3.SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !utcTimeMillis.toLocalDate().isBefore(treatmentDate)

    override fun isSelectableYear(year: Int): Boolean = year >= treatmentDate.year
}
