package com.example.herdmanagementmobile.ui.screens.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.herdmanagementmobile.domain.validation.EarTag
import kotlinx.coroutines.delay

/**
 * Odczyt kolczyka elektronicznego przez wbudowany moduł NFC (wymaganie F6).
 *
 * Ekran informuje o ograniczeniu sprzętowym: typowe kolczyki bydlęce pracują w paśmie
 * LF 134 kHz (ISO 11784/11785), które nie jest obsługiwane przez moduły NFC smartfonów.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Odczyt NFC") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                nfcAdapter == null -> Unavailable(
                    title = "To urządzenie nie ma modułu NFC",
                    message = "Numer kolczyka wpisz ręcznie albo użyj skanera kodu kreskowego.",
                )

                !nfcAdapter.isEnabled -> Unavailable(
                    title = "NFC jest wyłączone",
                    message = "Włącz moduł NFC w ustawieniach systemu, a potem wróć na ten ekran.",
                    action = "Otwórz ustawienia NFC" to {
                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    },
                )

                activity == null -> Unavailable(
                    title = "Nie można uruchomić odczytu",
                    message = "Spróbuj ponownie po ponownym otwarciu ekranu.",
                )

                else -> ReaderActive(
                    activity = activity,
                    nfcAdapter = nfcAdapter,
                    onScanned = onScanned,
                )
            }
        }
    }
}

@Composable
private fun ReaderActive(
    activity: Activity,
    nfcAdapter: NfcAdapter,
    onScanned: (String) -> Unit,
) {
    val onScannedState by rememberUpdatedState(onScanned)
    var detected by remember { mutableStateOf(false) }
    var unreadable by remember { mutableStateOf(false) }

    DisposableEffect(nfcAdapter) {
        val callback = NfcAdapter.ReaderCallback { tag ->
            if (detected) return@ReaderCallback
            val payload = extractEarTagPayload(tag)
            if (payload == null) {
                // Odczytany znacznik nie zawiera numeru kolczyka.
                activity.runOnUiThread { unreadable = true }
                return@ReaderCallback
            }
            detected = true
            activity.runOnUiThread { onScannedState(payload) }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        nfcAdapter.enableReaderMode(activity, callback, flags, null)
        onDispose { nfcAdapter.disableReaderMode(activity) }
    }

    LaunchedEffect(unreadable) {
        if (unreadable) {
            delay(3000)
            unreadable = false
        }
    }

    PulsingNfcIcon()
    Spacer(Modifier.height(24.dp))

    Text(
        text = "Przyłóż kolczyk do tyłu telefonu",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Trzymaj nieruchomo przez chwilę. Odczyt nastąpi automatycznie.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(24.dp))

    if (unreadable) {
        NoteCard(
            icon = Icons.Default.ErrorOutline,
            text = "Wykryto tag, ale nie zawiera numeru kolczyka w rozpoznawanym formacie.",
            error = true,
        )
    } else {
        NoteCard(
            icon = Icons.Default.Info,
            text = "Standardowe kolczyki bydlęce LF 134 kHz (ISO 11784/11785) nie są " +
                "odczytywane przez moduł NFC telefonu — wymagają zewnętrznego czytnika. " +
                "Ten ekran obsługuje tagi NFC oraz kolczyki z zapisanym numerem w NDEF.",
        )
    }
}

/** Pulsująca ikona sygnalizująca oczekiwanie na znacznik NFC. */
@Composable
private fun PulsingNfcIcon() {
    val transition = rememberInfiniteTransition(label = "nfc")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun NoteCard(icon: ImageVector, text: String, error: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (error) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun Unavailable(
    title: String,
    message: String,
    action: Pair<String, () -> Unit>? = null,
) {
    Icon(
        imageVector = Icons.Default.Nfc,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    action?.let { (label, onClick) ->
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClick) { Text(label) }
    }
}

private fun extractEarTagPayload(tag: Tag): String? {
    Ndef.get(tag)?.let { ndef ->
        try {
            ndef.connect()
            ndef.ndefMessage?.records?.forEach { record ->
                parseTextRecord(record)?.let { text ->
                    EarTag.parseBarcode(text)?.let { return it }
                }
            }
        } catch (_: Exception) {
            // Błąd odczytu NDEF — zastosowanie identyfikatora UID znacznika.
        } finally {
            runCatching { ndef.close() }
        }
    }

    val uidHex = tag.id.joinToString("") { "%02X".format(it) }
    EarTag.parseBarcode(uidHex)?.let { return it }

    val uidDecimal = tag.id.fold(0L) { acc, b -> acc * 256L + (b.toInt() and 0xFF) }
    EarTag.parseBarcode(uidDecimal.toString())?.let { return it }
    EarTag.parseBarcode(uidDecimal.toString().padStart(15, '0'))?.let { return it }

    return null
}

private fun parseTextRecord(record: NdefRecord): String? {
    if (record.tnf != NdefRecord.TNF_WELL_KNOWN ||
        !record.type.contentEquals(NdefRecord.RTD_TEXT)
    ) return null
    val payload = record.payload
    if (payload.isEmpty()) return null
    val languageLength = payload[0].toInt() and 0x3F
    val textStart = 1 + languageLength
    if (textStart > payload.size) return null
    return String(payload, textStart, payload.size - textStart, Charsets.UTF_8)
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
