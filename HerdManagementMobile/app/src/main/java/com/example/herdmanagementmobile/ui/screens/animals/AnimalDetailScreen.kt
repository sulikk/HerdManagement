package com.example.herdmanagementmobile.ui.screens.animals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import com.example.herdmanagementmobile.domain.model.Sex
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddEvent: (Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: AnimalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Karta zwierzęcia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                actions = {
                    state.animal?.let { animal ->
                        IconButton(onClick = { onEdit(animal.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edytuj")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Usuń")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            state.animal?.let { animal ->
                ExtendedFloatingActionButton(
                    onClick = { onAddEvent(animal.id) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Zdarzenie") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                state.animal == null -> Text(
                    text = "Nie znaleziono zwierzęcia.",
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> AnimalDetailContent(animal = state.animal!!, events = state.events)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Usunąć zwierzę?") },
            text = {
                Text("Operacja nieodwracalna. Usunięte zostaną także powiązane zdarzenia, leczenia i pomiary.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDeleted)
                }) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun AnimalDetailContent(animal: Animal, events: List<AnimalEvent>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = animal.earTag,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
        )
        animal.name?.let { Text(it, style = MaterialTheme.typography.titleMedium) }

        HorizontalDivider()

        DetailRow("Płeć", animal.sex.label())
        animal.birthDate?.let {
            DetailRow("Data urodzenia", it.format(DateTimeFormatter.ISO_LOCAL_DATE))
        }
        animal.breed?.let { DetailRow("Rasa", it) }
        animal.motherEarTag?.let { DetailRow("Kolczyk matki", it) }
        animal.herdId?.let { DetailRow("ID siedziby stada", it) }

        HorizontalDivider()

        Text("Zdarzenia", style = MaterialTheme.typography.titleMedium)
        if (events.isEmpty()) {
            Text(
                text = "Brak zarejestrowanych zdarzeń. Kliknij \"Zdarzenie\", aby dodać pierwsze.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            events.forEach { EventRow(it) }
        }
    }
}

@Composable
private fun EventRow(event: AnimalEvent) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.type.label(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = event.eventDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AssistChip(
            onClick = {},
            label = { Text(event.irzStatus.label()) },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = event.irzStatus.color(),
            ),
        )
        event.notes?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun Sex.label(): String = when (this) {
    Sex.FEMALE -> "Samica"
    Sex.MALE -> "Samiec"
    Sex.OTHER -> "Inne"
}

private fun EventType.label(): String = when (this) {
    EventType.BIRTH -> "Urodzenie"
    EventType.ARRIVAL -> "Przybycie"
    EventType.DEPARTURE -> "Ubycie"
    EventType.DEATH -> "Padnięcie"
}

private fun IrzStatus.label(): String = when (this) {
    IrzStatus.PENDING -> "Oczekuje na zgłoszenie"
    IrzStatus.SUBMITTED -> "Wysłano do IRZplus"
    IrzStatus.CONFIRMED -> "Potwierdzono"
    IrzStatus.REJECTED -> "Odrzucono"
}

@Composable
private fun IrzStatus.color() = when (this) {
    IrzStatus.PENDING -> MaterialTheme.colorScheme.tertiary
    IrzStatus.SUBMITTED -> MaterialTheme.colorScheme.primary
    IrzStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
    IrzStatus.REJECTED -> MaterialTheme.colorScheme.error
}
