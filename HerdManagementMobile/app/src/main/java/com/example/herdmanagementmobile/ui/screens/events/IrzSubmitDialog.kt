package com.example.herdmanagementmobile.ui.screens.events

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Jawne potwierdzenie zgłoszenia do IRZplus (wymaganie F12: żadne zgłoszenie
 * nie jest wysyłane bez zgody użytkownika).
 */
@Composable
fun IrzSubmitDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Anuluj") }
                if (secondaryLabel != null) {
                    TextButton(onClick = onSecondary) { Text(secondaryLabel) }
                }
            }
        },
    )
}
