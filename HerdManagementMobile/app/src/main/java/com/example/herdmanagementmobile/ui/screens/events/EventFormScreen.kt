package com.example.herdmanagementmobile.ui.screens.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.DeadlineText
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.validation.EventConflict
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
    var showConflictDialog by remember { mutableStateOf(false) }

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
                    // Powód blokady musi być przy przycisku, który nie działa. Samo
                    // ostrzeżenie w karcie rodzaju zdarzenia bywa odwinięte poza ekran,
                    // a wyłączony przycisk bez wyjaśnienia wygląda jak awaria aplikacji.
                    if (state.blocked) {
                        state.conflict?.let { conflict ->
                            Text(
                                text = "Nie można zapisać: ${conflict.warning(state.type)}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Button(
                        // Sprzeczność z ewidencją wymaga potwierdzenia, zanim dojdzie do
                        // pytania o zgłoszenie — inaczej hodowca kliknie „Zapisz i zgłoś”
                        // i dopiero rejestr powie mu, że taka sztuka jest już zapisana.
                        onClick = {
                            if (viewModel.validate()) {
                                if (state.conflict != null) {
                                    showConflictDialog = true
                                } else {
                                    showSubmitDialog = true
                                }
                            }
                        },
                        enabled = !state.isSaving && !state.blocked,
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
                state.conflict?.let { conflict ->
                    ConflictWarning(
                        text = conflict.warning(state.type) + if (conflict.blocking) {
                            " Usuń tamto zgłoszenie na karcie zwierzęcia albo wybierz inny " +
                                "rodzaj zdarzenia."
                        } else {
                            ""
                        },
                    )
                }
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

    // Gdy sprzeczność zniknie (historia zmieniła się w tle), okno nie ma czego
    // dotyczyć — zamykamy je efektem, a nie zapisem stanu w trakcie kompozycji.
    LaunchedEffect(state.conflict) {
        if (state.conflict == null) showConflictDialog = false
    }

    state.conflict?.let { conflict ->
        // Blokady nie da się przekliknąć, więc nie ma dla niej okna z „Dodaj mimo to”.
        if (!showConflictDialog || conflict.blocking) return@let
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null) },
            title = { Text("Sprzeczne z ewidencją") },
            text = {
                Text(
                    conflict.warning(state.type) +
                        "\n\nJeśli uzupełniasz ewidencję wstecz albo wiesz, że poprzedni " +
                        "wpis jest błędny, możesz dodać zdarzenie mimo to.",
                )
            },
            // Akcją główną jest poprawa wpisu: w tych przypadkach rejestr prawie zawsze
            // odrzuci zgłoszenie, więc wymuszenie zapisu ma być wyborem świadomym,
            // a nie ruchem, który robi się odruchowo największym przyciskiem.
            confirmButton = {
                Button(onClick = { showConflictDialog = false }) { Text("Popraw") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConflictDialog = false
                    showSubmitDialog = true
                }) { Text("Dodaj mimo to") }
            },
        )
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
 * Ostrzeżenie o sprzeczności z ewidencją, widoczne od razu po wyborze rodzaju
 * zdarzenia — zanim użytkownik wypełni resztę formularza.
 */
@Composable
private fun ConflictWarning(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Treść ostrzeżenia zależy też od zdarzenia, które użytkownik właśnie dodaje:
 * ta sama historia inaczej kłóci się z ubyciem, a inaczej z padnięciem.
 */
private fun EventConflict.warning(type: EventType): String {
    val date = conflictingDate.format(dayFormat)
    return when (kind) {
        EventConflict.Kind.ALREADY_BORN ->
            "Ta sztuka ma już zarejestrowane urodzenie z $date. Rejestr IRZplus " +
                "przyjmuje urodzenie jednej sztuki tylko raz — drugie zgłoszenie odrzuci."

        EventConflict.Kind.ALREADY_ARRIVED ->
            "Ta sztuka przyjechała do gospodarstwa $date — skoro przybyła z innej " +
                "siedziby, nie urodziła się u Ciebie. Urodzenie i przybycie wykluczają się."

        EventConflict.Kind.ALREADY_DEAD ->
            "Ta sztuka jest już zarejestrowana jako padła ($date). Drugie zgłoszenie " +
                "padnięcia rejestr odrzuci."

        EventConflict.Kind.ALREADY_LEFT -> when (type) {
            EventType.DEATH -> "Według ewidencji ta sztuka opuściła gospodarstwo $date " +
                "— padnięcie zgłasza wtedy jej nowy posiadacz."

            EventType.BIRTH -> "Ta sztuka ma w ewidencji wyjście z gospodarstwa ($date), " +
                "a urodzenie jest zawsze pierwszym zdarzeniem w jej historii."

            else -> "Według ewidencji ta sztuka opuściła gospodarstwo $date " +
                "i nie może ubyć ponownie."
        }

        EventConflict.Kind.ALREADY_IN_HERD ->
            "Według ewidencji ta sztuka jest już w stadzie (ostatnie zdarzenie: $date). " +
                "Przybycie zgłasza się dla sztuki, która dopiero do Ciebie trafiła."
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
