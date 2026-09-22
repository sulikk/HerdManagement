package com.example.herdmanagementmobile.ui.screens.listings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.data.local.relation.ListingWithAnimal
import java.time.format.DateTimeFormatter

private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * Lista ogłoszeń sprzedażowych hodowcy (zadanie 9).
 *
 * Zakładki odwzorowują cykl życia ogłoszenia: szkic czeka na decyzję o publikacji
 * (F12), opublikowane jest widoczne dla kupujących, zakończone to sprzedane
 * i wycofane. Filtry działają w obrębie zakładki — hodowca szuka konkretnej sztuki
 * wśród swoich ofert, nie w całej giełdzie.
 *
 * Nowe ogłoszenie zakłada się z karty zwierzęcia, nie stąd: formularz ma być
 * wypełniony danymi sztuki (F13), więc musi wiedzieć, której dotyczy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingListScreen(
    onBack: (() -> Unit)? = null,
    onListingClick: (Long) -> Unit,
    viewModel: ListingListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val group = ListingGroup.entries[selectedTab]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ogłoszenia") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyListings(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                ListingGroup.entries.forEachIndexed { index, entry ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { TabLabel(entry.label, state.totalIn(entry)) },
                    )
                }
            }

            SearchField(
                query = state.filter.query,
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
            )
            FilterRow(
                filter = state.filter,
                onSortSelected = viewModel::onSortSelected,
                onToggleVerified = viewModel::toggleVerifiedOnly,
            )

            val visible = state.visibleIn(group)
            ResultCount(shown = visible.size, total = state.totalIn(group))

            when {
                state.totalIn(group) == 0 -> EmptyGroup(group)
                visible.isEmpty() -> NoMatches()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.listingId }) { listing ->
                        ListingRow(
                            listing = listing,
                            onClick = { onListingClick(listing.listingId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabLabel(label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Badge { Text("$count") }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
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

/** Sortowanie i zawężenie do potwierdzonych — jeden rząd nad listą. */
@Composable
private fun FilterRow(
    filter: ListingFilter,
    onSortSelected: (ListingSortField) -> Unit,
    onToggleVerified: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ListingSortField.entries.forEach { field ->
            val active = filter.sort.field == field
            FilterChip(
                selected = active,
                onClick = { onSortSelected(field) },
                label = { Text(field.label) },
                trailingIcon = if (active) {
                    {
                        Icon(
                            imageVector = if (filter.sort.ascending) {
                                Icons.Default.ArrowUpward
                            } else {
                                Icons.Default.ArrowDownward
                            },
                            contentDescription = if (filter.sort.ascending) "Rosnąco" else "Malejąco",
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }

        FilterChip(
            selected = filter.verifiedOnly,
            onClick = onToggleVerified,
            label = { Text("Potwierdzone w IRZplus") },
            leadingIcon = {
                Icon(
                    Icons.Default.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun ResultCount(shown: Int, total: Int) {
    if (total == 0) return
    Text(
        text = if (shown == total) "$total ${ogloszenia(total)}" else "Pokazano $shown z $total",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ListingRow(listing: ListingWithAnimal, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = listing.animalName?.takeIf { it.isNotBlank() } ?: listing.earTag,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatPrice(listing.price),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    listing.earTag,
                    listing.breed?.takeIf { it.isNotBlank() },
                    "wystawiono ${listing.createdAt.format(dayFormat)}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IrzBadge(verified = listing.irzVerified)
                if (listing.inquiryCount > 0) {
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "${listing.inquiryCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// --- Stany puste --------------------------------------------------------

@Composable
private fun EmptyListings(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Brak ogłoszeń", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ogłoszenie zakładasz z karty zwierzęcia — przyciskiem " +
                "„Wystaw na sprzedaż”. Formularz wypełni się danymi sztuki.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Zakładka jest pusta z natury — to co innego niż wynik odfiltrowany do zera. */
@Composable
private fun EmptyGroup(group: ListingGroup) {
    val message = when (group) {
        ListingGroup.DRAFTS -> "Brak szkiców. Nowe ogłoszenie zakładasz z karty zwierzęcia."
        ListingGroup.PUBLISHED -> "Nic nie jest opublikowane. Szkic staje się widoczny " +
            "dopiero po zatwierdzeniu publikacji."

        ListingGroup.CLOSED -> "Nic jeszcze nie zostało sprzedane ani wycofane."
    }
    CenteredNote(message)
}

@Composable
private fun NoMatches() = CenteredNote(
    "Żadne ogłoszenie w tej zakładce nie pasuje do filtrów.",
)

@Composable
private fun CenteredNote(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Odmiana: „1 ogłoszenie”, „2 ogłoszenia”, „5 ogłoszeń”. */
private fun ogloszenia(count: Int): String {
    val last = count % 10
    val lastTwo = count % 100
    return when {
        count == 1 -> "ogłoszenie"
        last in 2..4 && lastTwo !in 12..14 -> "ogłoszenia"
        else -> "ogłoszeń"
    }
}
