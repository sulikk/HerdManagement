package com.example.herdmanagementmobile.ui.screens.listings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.Inquiry
import com.example.herdmanagementmobile.domain.model.Listing
import com.example.herdmanagementmobile.domain.model.ListingStatus
import com.example.herdmanagementmobile.ui.common.SectionCard
import com.example.herdmanagementmobile.ui.common.dayFormat
import java.time.format.DateTimeFormatter

private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/**
 * Karta ogłoszenia sprzedażowego. Prowadzi przez kolejne etapy: weryfikację danych
 * w rejestrze, publikację, obsługę zapytań i oznaczenie sprzedaży. Publikacja wymaga
 * wcześniejszej weryfikacji (wymagania F12 i F14).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    onBack: () -> Unit,
    onEdit: (animalId: Long, listingId: Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: ListingDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsState()
    val inquiries by viewModel.inquiries.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var confirmPublish by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ogłoszenie") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                actions = {
                    detail?.listing?.let { current ->
                        if (current.isEditable) {
                            IconButton(onClick = { onEdit(current.animalId, current.id) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edytuj")
                            }
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Usuń")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = detail
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OfferHeader(listing = current.listing, animal = current.animal)
            VerificationSection(listing = current.listing, onVerify = viewModel::verify)
            ActionsSection(
                listing = current.listing,
                onPublishRequested = { confirmPublish = true },
                onWithdraw = viewModel::withdraw,
                onMarkSold = viewModel::markSold,
            )
            InquiriesSection(inquiries = inquiries, status = current.listing.status)
        }
    }

    if (confirmPublish) {
        PublishDialog(
            onConfirm = {
                confirmPublish = false
                viewModel.publish()
            },
            onDismiss = { confirmPublish = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć ogłoszenie?") },
            text = { Text("Ogłoszenie i zapytania kupujących zostaną trwale usunięte.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted)
                }) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") }
            },
        )
    }
}

/** Nagłówek ogłoszenia z ceną, statusem oraz danymi zwierzęcia. */
@Composable
private fun OfferHeader(listing: Listing, animal: Animal?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPrice(listing.price),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusChip(listing.status)
            }

            if (animal != null) {
                Text(
                    text = animal.earTag,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = listOfNotNull(
                        animal.name?.takeIf { it.isNotBlank() },
                        animal.breed?.takeIf { it.isNotBlank() },
                        animal.birthDate?.let { "ur. ${it.format(dayFormat)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Zwierzę zostało usunięte z ewidencji.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            listing.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    "wystawiono ${listing.createdAt.format(dayFormat)}",
                    listing.publishedAt?.let { "opublikowano ${it.format(dayFormat)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(status: ListingStatus) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(status.label()) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = when (status) {
                ListingStatus.PUBLISHED -> MaterialTheme.colorScheme.primary
                ListingStatus.SOLD -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    )
}

/** Sekcja weryfikacji w IRZplus (wymaganie F14): odznaka, przyczyna jej braku i ponowne sprawdzenie. */
@Composable
private fun VerificationSection(listing: Listing, onVerify: () -> Unit) {
    SectionCard(
        title = "Weryfikacja w IRZplus",
        hint = "Porównanie danych sztuki z rejestrem państwowym. " +
            "Potwierdzenie jest warunkiem publikacji ogłoszenia.",
    ) {
        IrzBadge(verified = listing.irzVerified)

        listing.irzMessage?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (listing.irzVerified) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        listing.irzVerifiedAt?.let {
            Text(
                text = "Sprawdzono: ${it.format(stamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(onClick = onVerify, modifier = Modifier.fillMaxWidth()) {
            Text(if (listing.irzVerified) "Sprawdź ponownie" else "Sprawdź w IRZplus")
        }
    }
}

@Composable
private fun ActionsSection(
    listing: Listing,
    onPublishRequested: () -> Unit,
    onWithdraw: () -> Unit,
    onMarkSold: () -> Unit,
) {
    SectionCard(title = "Co dalej") {
        when (listing.status) {
            ListingStatus.DRAFT, ListingStatus.WITHDRAWN -> {
                Button(
                    onClick = onPublishRequested,
                    enabled = listing.irzVerified,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Opublikuj ogłoszenie")
                }
                Text(
                    text = if (listing.irzVerified) {
                        "Po publikacji ogłoszenie będzie widoczne dla kupujących."
                    } else {
                        "Najpierw potwierdź dane w IRZplus — bez tego publikacja " +
                            "nie jest możliwa."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (listing.irzVerified) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            ListingStatus.PUBLISHED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onWithdraw, modifier = Modifier.weight(1f)) {
                        Text("Wycofaj")
                    }
                    Button(onClick = onMarkSold, modifier = Modifier.weight(1f)) {
                        Text("Sprzedane")
                    }
                }
                Text(
                    text = "Wycofanie zdejmuje ofertę z giełdy; możesz ją opublikować ponownie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ListingStatus.SOLD -> {
                Text(
                    text = "Ogłoszenie rozliczone jako sprzedane.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Sprzedaż to nie to samo co zgłoszenie ubycia. Zarejestruj ubycie " +
                        "na karcie zwierzęcia i zgłoś je do IRZplus w ciągu 7 dni.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Okno potwierdzenia publikacji ogłoszenia (wymaganie F12). */
@Composable
private fun PublishDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opublikować ogłoszenie?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Ogłoszenie stanie się widoczne dla kupujących i będzie można " +
                        "wysyłać do niego zapytania.",
                )
                Text(
                    text = "Dane zostaną jeszcze raz sprawdzone w rejestrze IRZplus. " +
                        "Jeśli sztuka została w międzyczasie rozliczona, publikacja " +
                        "nie dojdzie do skutku.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Opublikuj") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun InquiriesSection(inquiries: List<Inquiry>, status: ListingStatus) {
    SectionCard(title = "Zapytania kupujących (${inquiries.size})") {
        if (inquiries.isEmpty()) {
            Text(
                text = when (status) {
                    ListingStatus.PUBLISHED -> "Jeszcze nikt nie pytał o tę sztukę."
                    else -> "Zapytania można wysyłać dopiero do opublikowanego ogłoszenia."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        inquiries.forEach { inquiry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = inquiry.buyer, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = inquiry.createdAt.format(stamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inquiry.contact?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(text = inquiry.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}
