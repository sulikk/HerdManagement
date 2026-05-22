package com.example.herdmanagementmobile.ui.screens.animals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.Animal

@Composable
fun AnimalListScreen(
    onAnimalClick: (Long) -> Unit,
    viewModel: AnimalListViewModel = hiltViewModel(),
) {
    val animals by viewModel.animals.collectAsState()

    if (animals.isEmpty()) {
        EmptyState()
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(animals, key = Animal::id) { animal ->
                ListItem(
                    headlineContent = { Text(animal.earTag) },
                    supportingContent = { animal.name?.let { Text(it) } },
                    modifier = Modifier.clickable { onAnimalClick(animal.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Brak zwierząt w stadzie.\nDodaj pierwsze zwierzę, skanując kolczyk.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
