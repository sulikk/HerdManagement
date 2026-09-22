package com.example.herdmanagementmobile.ui.screens.animals

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.ComplianceItem
import com.example.herdmanagementmobile.domain.model.DeadlineText
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import com.example.herdmanagementmobile.domain.model.Sex
import com.example.herdmanagementmobile.domain.model.Treatment
import com.example.herdmanagementmobile.domain.stats.AgeGroup
import com.example.herdmanagementmobile.ui.common.SectionCard
import com.example.herdmanagementmobile.ui.screens.events.IrzSubmitDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private enum class DetailTab { DANE, ZDARZENIA, LECZENIA }

/**
 * Karta zwierzęcia podzielona na zakładki.
 *
 * Wcześniej wszystko leżało w jednym przewijanym `Column`: przy stu leczeniach
 * Compose budował sto wierszy naraz, a hodowca przewijał kilometr ekranu, żeby
 * dojść do historii zabiegów. Teraz każda zakładka ma własną leniwą listę
 * (renderowane jest tylko to, co widać), a pozycje są pogrupowane po latach
 * z przyklejonym nagłówkiem — przy długiej historii to jedyny sposób, żeby
 * wiedzieć, na jakiej wysokości listy się jest.
 *
 * Trwająca karencja jest pokazana nad zakładkami, niezależnie od wybranej —
 * to informacja bezpieczeństwa (mleko i mięso poza obrotem), więc nie może
 * zależeć od tego, gdzie akurat patrzy użytkownik.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddEvent: (Long) -> Unit,
    onAddTreatment: (Long) -> Unit,
    onSellClick: (animalId: Long, existingListingId: Long?) -> Unit,
    onDeleted: () -> Unit,
    viewModel: AnimalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var eventToSubmit by remember { mutableStateOf<AnimalEvent?>(null) }
    var selectedTab by remember { mutableIntStateOf(DetailTab.DANE.ordinal) }

    val animal = state.animal
    val tab = DetailTab.entries[selectedTab]

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
                    animal?.let {
                        IconButton(onClick = { onEdit(it.id) }) {
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
            // Przycisk dodawania należy do zakładki, którą widać — jeden zamiast
            // dwóch „plusów” rozsianych po treści.
            if (animal != null && tab != DetailTab.DANE) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (tab == DetailTab.ZDARZENIA) {
                            onAddEvent(animal.id)
                        } else {
                            onAddTreatment(animal.id)
                        }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(if (tab == DetailTab.ZDARZENIA) "Zdarzenie" else "Leczenie") },
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            animal == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Nie znaleziono zwierzęcia.") }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                IdentityHeader(state, animal)
                WithdrawalBanner(state.treatments)

                TabRow(selectedTabIndex = selectedTab) {
                    TabWithCount("Dane", null, selectedTab == 0) { selectedTab = 0 }
                    TabWithCount("Zdarzenia", state.events.size, selectedTab == 1) { selectedTab = 1 }
                    TabWithCount("Leczenia", state.treatments.size, selectedTab == 2) { selectedTab = 2 }
                }

                when (tab) {
                    DetailTab.DANE -> DataTab(
                        state = state,
                        animal = animal,
                        onSell = { onSellClick(animal.id, state.activeListingId) },
                    )

                    DetailTab.ZDARZENIA -> EventsTab(
                        events = state.events,
                        onSubmitToIrz = { eventToSubmit = it },
                    )

                    DetailTab.LECZENIA -> TreatmentsTab(state.treatments)
                }
            }
        }
    }

    eventToSubmit?.let { event ->
        val retry = event.irzStatus == IrzStatus.REJECTED
        IrzSubmitDialog(
            title = if (retry) "Zgłosić ponownie do IRZplus?" else "Zgłosić do IRZplus?",
            description = "${event.type.label()} z dnia ${event.eventDate.format(dayFormat)} " +
                "(${animal?.earTag.orEmpty()}) zostanie zgłoszone do systemu ARiMR (IRZplus). " +
                "Zgłoszenia nie można cofnąć z poziomu aplikacji." +
                if (retry) "\n\nUpewnij się, że poprawiono dane wskazane w powodzie odrzucenia." else "",
            confirmLabel = "Zgłoś",
            onConfirm = {
                viewModel.submitToIrz(event.id)
                eventToSubmit = null
            },
            onDismiss = { eventToSubmit = null },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Usunąć zwierzę?") },
            text = {
                Text(
                    "Operacja nieodwracalna. Usunięte zostaną także powiązane zdarzenia, " +
                        "leczenia i pomiary.",
                )
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

/** Kolczyk i imię zostają na ekranie niezależnie od zakładki — to tożsamość sztuki. */
@Composable
private fun IdentityHeader(state: AnimalDetailState, animal: Animal) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = animal.earTag,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
        )

        val subtitle = listOfNotNull(
            animal.name?.takeIf { it.isNotBlank() },
            animal.breed?.takeIf { it.isNotBlank() },
            state.ageMonths?.let { formatAge(it) },
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Grupa użytkowa zamiast samej płci: „Byczek” niesie i płeć, i wiek.
            state.ageGroup?.let { group ->
                InfoChip(text = group.label(animal), tint = MaterialTheme.colorScheme.primary)
            }
            if (!state.inHerd) {
                InfoChip(
                    text = "Poza stadem",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.activeListingId != null) {
                InfoChip(text = "Wystawione", tint = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, tint: Color) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(labelColor = tint),
    )
}

/** Wiek opisowo: do dwóch lat w miesiącach, potem w latach z resztą. */
private fun formatAge(months: Long): String {
    if (months < 24) return "$months ${DeadlineText.months(months)}"
    val years = (months / 12).toInt()
    val rest = months % 12
    return if (rest == 0L) {
        "$years ${DeadlineText.years(years)}"
    } else {
        "$years ${DeadlineText.years(years)} $rest mies."
    }
}

/**
 * Ostrzeżenie o trwającej karencji — widoczne zawsze, nie tylko w zakładce leczeń.
 * Mleko i mięso sztuki na karencji nie mogą trafić do obrotu, więc ta informacja
 * nie może być schowana za kliknięciem.
 */
@Composable
private fun WithdrawalBanner(treatments: List<Treatment>) {
    val today = LocalDate.now()
    val until = treatments.mapNotNull { it.withdrawalUntil }
        .filter { it.isAfter(today) }
        .maxOrNull() ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Karencja do ${until.format(dayFormat)} — mleko i mięso poza obrotem.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun TabWithCount(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (count != null && count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge { Text("$count") }
                }
            }
        },
    )
}

// --- Zakładka: dane -----------------------------------------------------

@Composable
private fun DataTab(state: AnimalDetailState, animal: Animal, onSell: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Identyfikacja") {
            DetailRow("Numer kolczyka", animal.earTag)
            DetailRow("Płeć", animal.sex.label())
            animal.birthDate?.let {
                DetailRow(
                    label = "Data urodzenia",
                    value = it.format(dayFormat) +
                        (state.ageMonths?.let { m -> " (${formatAge(m)})" } ?: ""),
                )
            }
            animal.breed?.takeIf { it.isNotBlank() }?.let { DetailRow("Rasa", it) }
        }

        SectionCard("Pochodzenie i lokalizacja") {
            DetailRow(
                label = "Kolczyk matki",
                value = animal.motherEarTag?.takeIf { it.isNotBlank() } ?: "Nie podano",
            )
            DetailRow(
                label = "Numer siedziby stada",
                value = animal.herdId?.takeIf { it.isNotBlank() } ?: "Nie podano",
            )
        }

        SectionCard(
            title = "Sprzedaż",
            hint = if (state.activeListingId != null) {
                "Ta sztuka ma już aktywne ogłoszenie."
            } else {
                "Formularz wypełni się danymi z tej karty."
            },
        ) {
            OutlinedButton(
                onClick = onSell,
                enabled = state.inHerd || state.activeListingId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.activeListingId != null) "Otwórz ogłoszenie" else "Wystaw na sprzedaż",
                )
            }
            if (!state.inHerd && state.activeListingId == null) {
                Text(
                    text = "Sztuka jest poza stadem — rejestr nie potwierdzi jej danych, " +
                        "więc ogłoszenia nie da się opublikować.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- Zakładka: zdarzenia ------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventsTab(events: List<AnimalEvent>, onSubmitToIrz: (AnimalEvent) -> Unit) {
    if (events.isEmpty()) {
        EmptyTab("Brak zarejestrowanych zdarzeń.\nDodaj pierwsze przyciskiem poniżej.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Zapas na dole, żeby pływający przycisk nie zasłaniał ostatniej pozycji.
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        events.groupBy { it.eventDate.year }.forEach { (year, inYear) ->
            stickyHeader(key = "event-$year") { YearHeader(year) }
            items(inYear, key = { it.id }) { event ->
                EventRow(event = event, onSubmitToIrz = { onSubmitToIrz(event) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EventRow(event: AnimalEvent, onSubmitToIrz: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.type.label(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = event.eventDate.format(dayFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Dopóki zdarzenie nie jest potwierdzone, biegnie ustawowy termin —
        // ta sama informacja co na ekranie terminów, tylko przy konkretnej sztuce.
        if (event.irzStatus != IrzStatus.CONFIRMED) {
            val daysLeft = ChronoUnit.DAYS.between(
                LocalDate.now(),
                event.eventDate.plusDays(ComplianceItem.REPORTING_DAYS),
            )
            Text(
                text = DeadlineText.timeLeft(daysLeft) + " na zgłoszenie do IRZplus",
                style = MaterialTheme.typography.bodySmall,
                color = if (daysLeft < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(event.irzStatus.label()) },
                colors = AssistChipDefaults.assistChipColors(labelColor = event.irzStatus.color()),
            )
            when (event.irzStatus) {
                IrzStatus.PENDING -> TextButton(onClick = onSubmitToIrz) { Text("Zgłoś do IRZplus") }
                IrzStatus.REJECTED -> TextButton(onClick = onSubmitToIrz) { Text("Zgłoś ponownie") }
                IrzStatus.SUBMITTED, IrzStatus.CONFIRMED -> Unit
            }
        }

        // Komunikat rejestru ma sens przy każdym stanie poza potwierdzonym —
        // przy odrzuceniu mówi wprost, co poprawić.
        if (event.irzStatus != IrzStatus.CONFIRMED) {
            event.irzMessage?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        event.irzDocumentNumber?.let {
            Text(
                text = "Nr dokumentu IRZ: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        event.partnerHoldingNumber?.let {
            Text(
                text = "Siedziba partnera: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        event.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// --- Zakładka: leczenia -------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreatmentsTab(treatments: List<Treatment>) {
    if (treatments.isEmpty()) {
        EmptyTab("Brak zarejestrowanych leczeń.\nDodaj pierwsze przyciskiem poniżej.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        treatments.groupBy { it.treatmentDate.year }.forEach { (year, inYear) ->
            stickyHeader(key = "treatment-$year") { YearHeader(year) }
            items(inYear, key = { it.id }) { treatment ->
                TreatmentRow(treatment)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TreatmentRow(treatment: Treatment) {
    val today = LocalDate.now()
    val inWithdrawal = treatment.withdrawalUntil?.isAfter(today) == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = treatment.drug,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = treatment.treatmentDate.format(dayFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val details = listOfNotNull(
            treatment.dose?.takeIf { it.isNotBlank() }?.let { "Dawka: $it" },
            treatment.vet?.takeIf { it.isNotBlank() }?.let { "Lek. wet.: $it" },
        )
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        treatment.withdrawalUntil?.let { until ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        if (inWithdrawal) {
                            "Karencja do ${until.format(dayFormat)}"
                        } else {
                            "Karencja zakończona"
                        },
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = if (inWithdrawal) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            )
        }

        treatment.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// --- Elementy wspólne ---------------------------------------------------

/** Przyklejony nagłówek roku — przy długiej historii mówi, gdzie się jest. */
@Composable
private fun YearHeader(year: Int) {
    Text(
        text = year.toString(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmptyTab(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/** Nazwa grupy użytkowej dla pojedynczej sztuki. */
private fun AgeGroup.label(animal: Animal): String = when (this) {
    AgeGroup.CALVES -> "Cielę"
    AgeGroup.HEIFERS -> "Jałówka"
    AgeGroup.YOUNG_BULLS -> "Byczek"
    AgeGroup.COWS -> "Krowa"
    AgeGroup.BULLS -> "Buhaj"
    AgeGroup.OTHER_SEX -> "Płeć nieokreślona"
    // Bez daty urodzenia nie da się przypisać grupy — zostaje sama płeć.
    AgeGroup.UNKNOWN_AGE -> animal.sex.label()
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
    IrzStatus.PENDING -> "Nie zgłoszono"
    IrzStatus.SUBMITTED -> "Wysyłanie do IRZplus…"
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
