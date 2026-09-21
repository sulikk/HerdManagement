package com.example.herdmanagementmobile.ui.screens.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.AssistantAction

/**
 * Czat z asystentem hodowcy (Zadanie 8). Model pracuje po stronie backendu;
 * każda akcja zmieniająca dane wraca tu jako propozycja do zatwierdzenia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) {
            listState.animateScrollToItem(state.entries.lastIndex)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asystent") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::newConversation) {
                        Icon(Icons.Default.Autorenew, contentDescription = "Nowa rozmowa")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.entries, key = ChatEntry::id) { entry ->
                    ChatBubble(entry)
                }
            }

            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Napisz do asystenta") },
                    enabled = !state.busy && state.pending == null,
                    maxLines = 4,
                )
                IconButton(
                    onClick = viewModel::send,
                    enabled = state.input.isNotBlank() && !state.busy && state.pending == null,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Wyślij")
                }
            }
        }
    }

    state.pending?.let { action ->
        ConfirmActionDialog(
            action = action,
            onApprove = { viewModel.decide(approved = true) },
            onReject = { viewModel.decide(approved = false) },
        )
    }
}

@Composable
private fun ChatBubble(entry: ChatEntry) {
    val alignment = if (entry.author == ChatAuthor.USER) Alignment.CenterEnd else Alignment.CenterStart
    val background = when (entry.author) {
        ChatAuthor.USER -> MaterialTheme.colorScheme.primaryContainer
        ChatAuthor.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant
        ChatAuthor.SYSTEM -> Color.Transparent
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        if (entry.author == ChatAuthor.SYSTEM) {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else {
            Surface(
                color = background,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

/** „Agent proponuje — użytkownik zatwierdza”: bez tej zgody backend nic nie zapisze. */
@Composable
private fun ConfirmActionDialog(
    action: AssistantAction,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
        title = { Text("Asystent proponuje zmianę") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(action.description)
                action.params.forEach { (label, value) ->
                    Text(
                        text = "$label: $value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onApprove) { Text("Zatwierdź") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Odrzuć") } },
    )
}
