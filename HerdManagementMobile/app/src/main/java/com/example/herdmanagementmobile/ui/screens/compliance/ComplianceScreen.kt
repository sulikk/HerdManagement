package com.example.herdmanagementmobile.ui.screens.compliance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.ComplianceItem
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceScreen(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: ComplianceViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zgodność z IRZ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Wszystkie zdarzenia zgłoszone i potwierdzone.\nBrak terminów do pilnowania.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            val overdue = items.filter { it.urgency(today) == ComplianceItem.Urgency.OVERDUE }
            val urgent = items.filter { it.urgency(today) == ComplianceItem.Urgency.URGENT }
            val onTime = items.filter { it.urgency(today) == ComplianceItem.Urgency.ON_TIME }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                section("Po terminie", overdue, today, onItemClick)
                section("Pilne (≤ 2 dni)", urgent, today, onItemClick)
                section("W terminie", onTime, today, onItemClick)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<ComplianceItem>,
    today: LocalDate,
    onItemClick: (Long) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$title") {
        Text(
            text = "$title (${items.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    items(items, key = { it.eventId }) { item ->
        ComplianceRow(item = item, today = today, onClick = { onItemClick(item.animalId) })
        HorizontalDivider()
    }
}

@Composable
private fun ComplianceRow(
    item: ComplianceItem,
    today: LocalDate,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.earTag,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = item.type.label(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item.animalName?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeadlineChip(item = item, today = today)
            Text(
                text = "termin: ${item.deadline.format(DateTimeFormatter.ISO_LOCAL_DATE)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (item.irzStatus) {
            IrzStatus.REJECTED -> item.irzMessage?.let {
                Text(
                    text = "Odrzucono przez IRZ: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            IrzStatus.PENDING -> Text(
                text = "Nie zgłoszono — otwórz kartę zwierzęcia, aby zgłosić.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IrzStatus.SUBMITTED -> Text(
                text = "Wysyłanie do IRZplus…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IrzStatus.CONFIRMED -> Unit
        }
    }
}

@Composable
private fun DeadlineChip(item: ComplianceItem, today: LocalDate) {
    val days = item.daysLeft(today)
    val (label, color) = when (item.urgency(today)) {
        ComplianceItem.Urgency.OVERDUE -> "Po terminie o ${-days} dni" to MaterialTheme.colorScheme.error
        ComplianceItem.Urgency.URGENT -> {
            val t = if (days == 0L) "Termin dziś" else "Zostały $days dni"
            t to MaterialTheme.colorScheme.tertiary
        }
        ComplianceItem.Urgency.ON_TIME -> "Zostało $days dni" to MaterialTheme.colorScheme.primary
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
    )
}

private fun EventType.label(): String = when (this) {
    EventType.BIRTH -> "Urodzenie"
    EventType.ARRIVAL -> "Przybycie"
    EventType.DEPARTURE -> "Ubycie"
    EventType.DEATH -> "Padnięcie"
}
