package com.example.herdmanagementmobile.ui.screens.compliance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.ComplianceItem
import com.example.herdmanagementmobile.domain.model.DeadlineText
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import com.example.herdmanagementmobile.domain.model.allowsRemoval
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * Ekran terminów zgłoszeń do IRZplus (wymaganie F10). Zawiera podsumowanie liczbowe
 * oraz listę zdarzeń pogrupowaną według pilności. Pilność jest sygnalizowana
 * jednocześnie kolorem, ikoną i tekstem, co zapewnia dostępność dla osób
 * z zaburzeniami widzenia barw.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComplianceScreen(
    onBack: (() -> Unit)? = null,
    onItemClick: (Long) -> Unit,
    viewModel: ComplianceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val today = LocalDate.now()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Usunięcie zgłoszenia wymaga potwierdzenia.
    var pendingDelete by remember { mutableStateOf<ComplianceItem?>(null) }
    var confirmClearRejected by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminy zgłoszeń") },
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
        if (state.waiting.isEmpty() && state.rejected.isEmpty()) {
            AllClear(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { TabLabel("Do zgłoszenia", state.waiting.size, highlight = false) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { TabLabel("Do poprawy", state.rejected.size, highlight = true) },
                )
            }

            if (selectedTab == 0) {
                WaitingTab(
                    items = state.waiting,
                    today = today,
                    onItemClick = onItemClick,
                    onDelete = { pendingDelete = it },
                )
            } else {
                RejectedTab(
                    items = state.rejected,
                    today = today,
                    onItemClick = onItemClick,
                    onDelete = { pendingDelete = it },
                    onClearAll = { confirmClearRejected = true },
                )
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { Text("Usunąć zgłoszenie?") },
            text = {
                Text(
                    "${item.type.label()} z ${item.eventDate.format(dayFormat)} " +
                        "(${item.earTag}) zniknie z ewidencji i z listy terminów.\n\n" +
                        "W rejestrze IRZplus nic się nie zmieni — to zgłoszenie nigdy nie " +
                        "zostało tam przyjęte.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item.eventId)
                    pendingDelete = null
                }) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Anuluj") }
            },
        )
    }

    if (confirmClearRejected) {
        val count = state.rejected.size
        AlertDialog(
            onDismissRequest = { confirmClearRejected = false },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { Text("Usunąć wszystkie odrzucone?") },
            text = {
                Text(
                    "Z ewidencji zniknie $count ${zgloszenia(count)} odrzucone przez IRZplus. " +
                        "Żaden wpis w rejestrze nie zostanie zmieniony, bo żadne z nich nie " +
                        "zostało tam przyjęte.\n\nTej operacji nie da się cofnąć.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllRejected()
                    confirmClearRejected = false
                }) { Text("Usuń wszystkie") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearRejected = false }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun TabLabel(label: String, count: Int, highlight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Badge(
                containerColor = if (highlight) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            ) { Text("$count") }
        }
    }
}

/** Lista zdarzeń oczekujących na zgłoszenie. */
@Composable
private fun WaitingTab(
    items: List<ComplianceItem>,
    today: LocalDate,
    onItemClick: (Long) -> Unit,
    onDelete: (ComplianceItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyTab(
            icon = Icons.Default.CheckCircle,
            title = "Nic nie czeka na zgłoszenie",
            description = "Wszystkie zdarzenia zostały wysłane do IRZplus.",
        )
        return
    }

    val overdue = items.filter { it.urgency(today) == ComplianceItem.Urgency.OVERDUE }
    val urgent = items.filter { it.urgency(today) == ComplianceItem.Urgency.URGENT }
    val onTime = items.filter { it.urgency(today) == ComplianceItem.Urgency.ON_TIME }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "summary") {
            Summary(overdue = overdue.size, urgent = urgent.size, onTime = onTime.size)
        }
        urgencySection("Po terminie", overdue, today, onItemClick, onDelete)
        urgencySection("Pilne — termin w ciągu 2 dni", urgent, today, onItemClick, onDelete)
        urgencySection("W terminie", onTime, today, onItemClick, onDelete)
    }
}

/** Lista zgłoszeń odrzuconych przez rejestr wraz z przyczyną odmowy i terminem ustawowym. */
@Composable
private fun RejectedTab(
    items: List<ComplianceItem>,
    today: LocalDate,
    onItemClick: (Long) -> Unit,
    onDelete: (ComplianceItem) -> Unit,
    onClearAll: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyTab(
            icon = Icons.Default.CheckCircle,
            title = "Brak odrzuconych zgłoszeń",
            description = "Rejestr nie zakwestionował żadnego z wysłanych zdarzeń.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "rejected-intro") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${items.size} ${zgloszenia(items.size)} odrzucone przez IRZplus",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Popraw wskazane dane na karcie zwierzęcia, a potem wyślij " +
                            "zgłoszenie ponownie. Termin 7 dni biegnie dalej.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onClearAll) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Usuń wszystkie odrzucone")
                    }
                }
            }
        }

        items(items, key = { it.eventId }) { item ->
            ComplianceRow(
                item = item,
                today = today,
                onClick = { onItemClick(item.animalId) },
                onDelete = { onDelete(item) },
            )
            HorizontalDivider()
        }
    }
}

/** Podsumowanie liczby zgłoszeń zaległych, pilnych i terminowych. */
@Composable
private fun Summary(overdue: Int, urgent: Int, onTime: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (overdue > 0) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when {
                    overdue > 0 -> "$overdue ${zgloszenia(overdue)} po ustawowym terminie"
                    urgent > 0 -> "$urgent ${zgloszenia(urgent)} do wysłania w ciągu 2 dni"
                    else -> "Wszystko w terminie"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (overdue > 0) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Counter("Po terminie", overdue, MaterialTheme.colorScheme.error)
                Counter("Pilne", urgent, MaterialTheme.colorScheme.tertiary)
                Counter("W terminie", onTime, MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Zdarzenie zgłasza się do IRZplus w ciągu 7 dni od jego wystąpienia.",
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue > 0) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, color: Color) {
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = if (value > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.urgencySection(
    title: String,
    items: List<ComplianceItem>,
    today: LocalDate,
    onItemClick: (Long) -> Unit,
    onDelete: (ComplianceItem) -> Unit,
) {
    if (items.isEmpty()) return
    stickyHeader(key = "header-$title") {
        Text(
            text = "$title (${items.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
    items(items, key = { it.eventId }) { item ->
        ComplianceRow(
            item = item,
            today = today,
            onClick = { onItemClick(item.animalId) },
            onDelete = { onDelete(item) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun ComplianceRow(
    item: ComplianceItem,
    today: LocalDate,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val urgency = item.urgency(today)
    val (icon, tint) = urgency.appearance()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.earTag,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                )
                item.animalName?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = " · $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "${item.type.label()} z ${item.eventDate.format(dayFormat)}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "${DeadlineText.timeLeft(item.daysLeft(today))} " +
                    "(termin ${item.deadline.format(dayFormat)})",
                style = MaterialTheme.typography.bodySmall,
                color = tint,
            )

            item.statusNote()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.irzStatus == IrzStatus.REJECTED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        // Usunięcie jest dostępne wyłącznie dla zgłoszeń niezapisanych w rejestrze.
        if (item.irzStatus.allowsRemoval) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Usuń zgłoszenie " +
                        "${item.type.label().lowercase()} z ${item.eventDate.format(dayFormat)}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComplianceItem.Urgency.appearance(): Pair<ImageVector, Color> = when (this) {
    ComplianceItem.Urgency.OVERDUE -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
    ComplianceItem.Urgency.URGENT -> Icons.Default.Schedule to MaterialTheme.colorScheme.tertiary
    ComplianceItem.Urgency.ON_TIME -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
}

/** Zwraca wskazówkę dotyczącą dalszego postępowania lub `null`, gdy nie jest wymagane działanie. */
private fun ComplianceItem.statusNote(): String? = when (irzStatus) {
    IrzStatus.REJECTED -> irzMessage?.let { "Odrzucone przez IRZplus: $it" }
        ?: "Odrzucone przez IRZplus — popraw dane i zgłoś ponownie."

    IrzStatus.PENDING -> "Niezgłoszone — otwórz kartę zwierzęcia, aby wysłać."
    IrzStatus.SUBMITTED -> "Wysyłanie do IRZplus…"
    IrzStatus.CONFIRMED -> null
}

@Composable
private fun AllClear(modifier: Modifier = Modifier) = EmptyTab(
    icon = Icons.Default.CheckCircle,
    title = "Brak terminów do pilnowania",
    description = "Wszystkie zdarzenia są zgłoszone i potwierdzone przez IRZplus.",
    modifier = modifier,
)

@Composable
private fun EmptyTab(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Zwraca odmianę rzeczownika „zgłoszenie” dla podanej liczby. */
private fun zgloszenia(count: Int): String {
    val last = count % 10
    val lastTwo = count % 100
    return when {
        count == 1 -> "zgłoszenie"
        last in 2..4 && lastTwo !in 12..14 -> "zgłoszenia"
        else -> "zgłoszeń"
    }
}

private fun EventType.label(): String = when (this) {
    EventType.BIRTH -> "Urodzenie"
    EventType.ARRIVAL -> "Przybycie"
    EventType.DEPARTURE -> "Ubycie"
    EventType.DEATH -> "Padnięcie"
}
