package com.example.herdmanagementmobile.ui.screens.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.DeadlineText
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.ui.common.DateField
import com.example.herdmanagementmobile.ui.common.SectionCard
import com.example.herdmanagementmobile.ui.common.PastOrToday
import com.example.herdmanagementmobile.ui.common.dayFormat
import com.example.herdmanagementmobile.ui.common.toLocalDate
import java.time.ZoneOffset

/**
 * Formularz zdarzenia zwierzęcego.
 *
 * Najważniejsze, czego ekran ma nauczyć: od daty zdarzenia biegnie ustawowy termin
 * 7 dni na zgłoszenie do IRZplus. Dlatego termin jest wyliczany i pokazywany od razu
 * po wyborze daty, zanim użytkownik zdecyduje, czy zgłasza teraz, czy później.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventFormScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: EventFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showSubmitDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dodaj zdarzenie") },
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
                        onClick = { if (viewModel.validate()) showSubmitDialog = true },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Zapisz zdarzenie")
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
                title = "Rodzaj zdarzenia",
                hint = "Każde z tych zdarzeń podlega obowiązkowi zgłoszenia do IRZplus.",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventType.entries.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { viewModel.onTypeChange(type) },
                            label = { Text(type.label()) },
                        )
                    }
                }
                Text(
                    text = state.type.hint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Termin") {
                DateField(
                    label = "Data zdarzenia *",
                    date = state.eventDate,
                    onPick = { showDatePicker = true },
                    isError = state.dateError != null,
                    supportingText = state.dateError,
                )
                DeadlineHint(
                    deadline = state.deadline.format(dayFormat),
                    daysLeft = state.daysToDeadline,
                )
            }

            if (state.requiresPartner) {
                val arrival = state.type == EventType.ARRIVAL
                SectionCard(
                    title = "Siedziba partnera",
                    hint = if (arrival) {
                        "Skąd zwierzę przyjechało. Rejestr wymaga tego numeru przy przybyciu."
                    } else {
                        "Dokąd zwierzę trafiło. Rejestr wymaga tego numeru przy ubyciu."
                    },
                ) {
                    OutlinedTextField(
                        value = state.partnerHolding,
                        onValueChange = viewModel::onPartnerHoldingChange,
                        label = {
                            Text(if (arrival) "Siedziba, z której przybyło *" else "Siedziba, do której ubyło *")
                        },
                        placeholder = { Text("PL000111222") },
                        supportingText = { Text(state.partnerError ?: "PL + 9 cyfr") },
                        isError = state.partnerError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionCard(title = "Notatki") {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text("Notatki (opcjonalnie)") },
                    supportingText = { Text("Zostają w ewidencji; nie są wysyłane do IRZplus.") },
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

    if (showSubmitDialog) {
        IrzSubmitDialog(
            title = "Zgłosić do IRZplus?",
            description = "${state.type.label()} z dnia ${state.eventDate.format(dayFormat)} " +
                "zostanie zapisane i zgłoszone do systemu ARiMR (IRZplus). " +
                "Zgłoszenia nie można cofnąć z poziomu aplikacji.\n\n" +
                "Możesz też tylko zapisać zdarzenie i zgłosić je później z karty zwierzęcia — " +
                "termin upływa ${state.deadline.format(dayFormat)}.",
            confirmLabel = "Zapisz i zgłoś",
            onConfirm = {
                showSubmitDialog = false
                viewModel.save(submitToIrz = true, onSuccess = onSaved)
            },
            secondaryLabel = "Tylko zapisz",
            onSecondary = {
                showSubmitDialog = false
                viewModel.save(submitToIrz = false, onSuccess = onSaved)
            },
            onDismiss = { showSubmitDialog = false },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.eventDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
            selectableDates = PastOrToday,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.onDateChange(it.toLocalDate()) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Anuluj") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * Termin zgłoszenia wyliczony z daty zdarzenia.
 *
 * Kolor i treść zmieniają się wraz z pilnością — przy zdarzeniu sprzed tygodnia
 * użytkownik ma od razu wiedzieć, że jest po czasie, a nie dowiadywać się o tym
 * dopiero na ekranie terminów.
 */
@Composable
private fun DeadlineHint(deadline: String, daysLeft: Long) {
    val overdue = daysLeft < 0
    Text(
        text = "Termin zgłoszenia do IRZplus: $deadline — ${DeadlineText.timeLeft(daysLeft)}.",
        style = MaterialTheme.typography.bodySmall,
        color = if (overdue) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

private fun EventType.label(): String = when (this) {
    EventType.BIRTH -> "Urodzenie"
    EventType.ARRIVAL -> "Przybycie"
    EventType.DEPARTURE -> "Ubycie"
    EventType.DEATH -> "Padnięcie"
}

/** Krótkie wyjaśnienie, czego dotyczy dany typ — nazwy bywają mylone. */
private fun EventType.hint(): String = when (this) {
    EventType.BIRTH -> "Urodzenie cielęcia w Twoim gospodarstwie."
    EventType.ARRIVAL -> "Zwierzę przyjechało do Ciebie z innej siedziby stada."
    EventType.DEPARTURE -> "Zwierzę opuściło gospodarstwo — sprzedaż, przekazanie, ubój."
    EventType.DEATH -> "Zwierzę padło w gospodarstwie."
}
