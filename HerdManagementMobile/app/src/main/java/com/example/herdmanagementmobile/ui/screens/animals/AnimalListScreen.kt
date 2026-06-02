package com.example.herdmanagementmobile.ui.screens.animals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.Animal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalListScreen(
    onAnimalClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onComplianceClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: AnimalListViewModel = hiltViewModel(),
) {
    val animals by viewModel.animals.collectAsState()
    val pendingSync by viewModel.pendingSyncCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stado") },
                actions = {
                    SyncStatusChip(pendingCount = pendingSync)
                    IconButton(onClick = onComplianceClick) {
                        Icon(
                            Icons.Default.EventAvailable,
                            contentDescription = "Zgodność z IRZ",
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Ustawienia gospodarstwa",
                        )
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
        if (animals.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(animals, key = Animal::id) { animal ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = animal.earTag,
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                        supportingContent = { animal.name?.let { Text(it) } },
                        modifier = Modifier.clickable { onAnimalClick(animal.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SyncStatusChip(pendingCount: Int) {
    val synced = pendingCount == 0
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(if (synced) "Zsynchronizowano" else "Do wysłania: $pendingCount")
        },
        leadingIcon = {
            Icon(
                imageVector = if (synced) Icons.Default.CloudDone else Icons.Default.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        modifier = Modifier.padding(end = 8.dp),
    )
}

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
