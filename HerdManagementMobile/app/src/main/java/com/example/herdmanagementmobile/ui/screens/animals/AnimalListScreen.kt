package com.example.herdmanagementmobile.ui.screens.animals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.search.AnimalSort
import com.example.herdmanagementmobile.domain.search.AnimalSortField
import java.time.format.DateTimeFormatter

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalListScreen(
    onAnimalClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: AnimalListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stado") },
                actions = {
                    SyncStatusChip(status = syncStatus, onClick = viewModel::syncNow)
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Dodaj") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.total > 0) {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onClear = viewModel::clearQuery,
                )
                SortChips(sort = state.sort, onSelect = viewModel::onSortSelected)
                ResultCount(shown = state.animals.size, total = state.total)
            }

            when {
                state.total == 0 -> EmptyState()
                state.animals.isEmpty() -> NoMatches(query = state.query)
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.animals, key = Animal::id) { animal ->
                        AnimalRow(animal = animal, onClick = { onAnimalClick(animal.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        placeholder = { Text("Szukaj: kolczyk, imię, rasa") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Wyczyść wyszukiwanie")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

/**
 * Wybór kryterium sortowania. Ponowny wybór aktywnego kryterium odwraca kierunek,
 * sygnalizowany strzałką.
 */
@Composable
private fun SortChips(sort: AnimalSort, onSelect: (AnimalSortField) -> Unit) {
    val options = listOf(
        AnimalSortField.EAR_TAG to "Kolczyk",
        AnimalSortField.NAME to "Imię",
        AnimalSortField.BREED to "Rasa",
        AnimalSortField.BIRTH_DATE to "Wiek",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (field, label) ->
            val active = sort.field == field
            FilterChip(
                selected = active,
                onClick = { onSelect(field) },
                label = { Text(label) },
                trailingIcon = if (active) {
                    {
                        Icon(
                            imageVector = if (sort.ascending) {
                                Icons.Default.ArrowUpward
                            } else {
                                Icons.Default.ArrowDownward
                            },
                            contentDescription = if (sort.ascending) "Rosnąco" else "Malejąco",
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ResultCount(shown: Int, total: Int) {
    Text(
        text = if (shown == total) "$total szt. w ewidencji" else "Pokazano $shown z $total",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Pozycja listy zwierząt z numerem kolczyka, imieniem, rasą i datą urodzenia. */
@Composable
private fun AnimalRow(animal: Animal, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(text = animal.earTag, fontFamily = FontFamily.Monospace)
        },
        supportingContent = {
            val details = listOfNotNull(
                animal.name?.takeIf { it.isNotBlank() },
                animal.breed?.takeIf { it.isNotBlank() },
                animal.birthDate?.format(dateFormat),
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "))
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Wskaźnik stanu synchronizacji; kliknięcie wyzwala synchronizację ręczną. Stan
 * „Zsynchronizowano” oznacza pustą kolejkę wysyłkową i udane pobranie zmian z serwera.
 */
@Composable
private fun SyncStatusChip(status: SyncStatus, onClick: () -> Unit) {
    val pull = status.pull
    val (label, icon) = when {
        status.failed > 0 -> "Błędy: ${status.failed}" to Icons.Default.CloudOff
        status.pending > 0 -> "Do wysłania: ${status.pending}" to Icons.Default.CloudSync
        pull.error != null -> "Brak danych z serwera" to Icons.Default.CloudOff
        pull.neverPulled -> "Nie pobrano z serwera" to Icons.Default.CloudOff
        else -> "Zsynchronizowano ${pull.lastPullAt?.format(PULL_TIME) ?: ""}".trim() to
            Icons.Default.CloudDone
    }
    val alarming = status.failed > 0 || pull.error != null || pull.neverPulled
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        colors = if (alarming) {
            AssistChipDefaults.assistChipColors(
                labelColor = MaterialTheme.colorScheme.error,
                leadingIconContentColor = MaterialTheme.colorScheme.error,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** Format godziny ostatniego pobrania zmian. */
private val PULL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Brak zwierząt w stadzie.\nKliknij \"Dodaj\", aby zarejestrować pierwsze.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Komunikat pustej listy, rozróżniający brak zwierząt od braku wyników wyszukiwania. */
@Composable
private fun NoMatches(query: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Żadna sztuka nie pasuje do „$query”.\nSprawdź fragment kolczyka, imię albo rasę.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
