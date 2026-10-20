package com.example.herdmanagementmobile.ui.screens.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.AssistantAction

/**
 * Ekran rozmowy z asystentem LLM. Model działa po stronie backendu, a każda akcja
 * modyfikująca dane jest prezentowana jako propozycja wymagająca zatwierdzenia
 * w oknie dialogowym wyszczególniającym zapisywane dane.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    onBack: (() -> Unit)? = null,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.entries.size, state.busy) {
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
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                        }
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

                if (state.fresh) {
                    item(key = "suggestions") {
                        Suggestions(enabled = !state.inputBlocked, onPick = viewModel::ask)
                    }
                }

                if (state.busy) {
                    item(key = "typing") { TypingIndicator() }
                }
            }

            HorizontalDivider()
            InputBar(
                value = state.input,
                blocked = state.inputBlocked,
                awaitingDecision = state.pending != null,
                onValueChange = viewModel::onInputChange,
                onSend = viewModel::send,
            )
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

/** Przykładowe pytania wyświetlane na początku rozmowy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(enabled: Boolean, onPick: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "Na przykład:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SUGGESTED_PROMPTS.forEach { prompt ->
                AssistChip(
                    onClick = { onPick(prompt) },
                    enabled = enabled,
                    label = { Text(prompt) },
                )
            }
        }
    }
}

/** Wskaźnik oczekiwania na odpowiedź modelu. */
@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Asystent sprawdza dane…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(entry: ChatEntry) {
    if (entry.author == ChatAuthor.SYSTEM) {
        SystemNote(entry)
        return
    }

    val fromUser = entry.author == ChatAuthor.USER
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = if (fromUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (fromUser) 16.dp else 4.dp,
                bottomEnd = if (fromUser) 4.dp else 16.dp,
            ),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

/** Wpis systemowy informujący o wyniku akcji asystenta. */
@Composable
private fun SystemNote(entry: ChatEntry) {
    val (icon, tint) = entry.tone.appearance()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageTone.appearance(): Pair<ImageVector, Color> = when (this) {
    MessageTone.SUCCESS -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
    MessageTone.REJECTED -> Icons.Default.Block to MaterialTheme.colorScheme.onSurfaceVariant
    MessageTone.ERROR -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
    MessageTone.NEUTRAL -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun InputBar(
    value: String,
    blocked: Boolean,
    awaitingDecision: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (awaitingDecision) {
            Text(
                text = "Rozmowa czeka na Twoją decyzję w sprawie zaproponowanego zapisu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Napisz do asystenta") },
                enabled = !blocked,
                maxLines = 4,
                shape = MaterialTheme.shapes.large,
            )
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !blocked,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Wyślij")
            }
        }
    }
}

/** Okno dialogowe zatwierdzenia akcji zaproponowanej przez asystenta. */
@Composable
private fun ConfirmActionDialog(
    action: AssistantAction,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
        title = { Text("Zatwierdzić zapis w ewidencji?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(action.description)

                if (action.params.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            action.params.forEach { (label, value) ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Zapis trafia do ewidencji gospodarstwa. Zgłoszenie do IRZplus " +
                        "to osobna czynność — wykonasz ją z karty zwierzęcia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onApprove) { Text("Zatwierdź") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Odrzuć") } },
    )
}
