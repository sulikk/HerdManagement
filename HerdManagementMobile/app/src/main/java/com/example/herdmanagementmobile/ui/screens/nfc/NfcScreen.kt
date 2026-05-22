package com.example.herdmanagementmobile.ui.screens.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.herdmanagementmobile.domain.validation.EarTag

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                nfcAdapter == null -> StatusMessage(
                    title = "NFC niedostępne",
                    message = "To urządzenie nie ma modułu NFC. Skorzystaj ze skanera kodu kreskowego.",
                )

                !nfcAdapter.isEnabled -> StatusMessage(
                    title = "NFC wyłączone",
                    message = "Włącz NFC w ustawieniach systemu i spróbuj ponownie.",
                )

                activity == null -> StatusMessage(
                    title = "Błąd",
                    message = "Nie można uruchomić skanera NFC z tego kontekstu.",
                )

                else -> NfcReaderActive(
                    activity = activity,
                    nfcAdapter = nfcAdapter,
                    onScanned = onScanned,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.NfcReaderActive(
    activity: Activity,
    nfcAdapter: NfcAdapter,
    onScanned: (String) -> Unit,
) {
    val onScannedState by rememberUpdatedState(onScanned)
    var detected by remember { mutableStateOf(false) }

    DisposableEffect(nfcAdapter) {
        val callback = NfcAdapter.ReaderCallback { tag ->
            if (detected) return@ReaderCallback
            val payload = extractEarTagPayload(tag) ?: return@ReaderCallback
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

    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Przyłóż kolczyk lub tag NFC\ndo tyłu telefonu",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "UWAGA: standardowe kolczyki bydlęce LF 134 kHz nie są odczytywane przez wbudowany moduł NFC telefonu. Wymagają zewnętrznego czytnika Bluetooth zgodnego z ISO 11784/11785.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BoxScope.StatusMessage(title: String, message: String) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
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
            // ignoruj — przejdź do fallbacku UID
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
