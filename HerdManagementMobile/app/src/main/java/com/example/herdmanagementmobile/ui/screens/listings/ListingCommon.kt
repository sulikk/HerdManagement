package com.example.herdmanagementmobile.ui.screens.listings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.herdmanagementmobile.domain.model.ListingStatus
import java.text.NumberFormat
import java.util.Locale

/** Zwraca etykietę statusu ogłoszenia. */
fun ListingStatus.label(): String = when (this) {
    ListingStatus.DRAFT -> "Szkic"
    ListingStatus.PUBLISHED -> "Opublikowane"
    ListingStatus.SOLD -> "Sprzedane"
    ListingStatus.WITHDRAWN -> "Wycofane"
}

private val priceFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pl-PL"))

fun formatPrice(value: Double): String = priceFormat.format(value)

/**
 * Odznaka „dane potwierdzone w IRZplus” (wymaganie F14). Brak potwierdzenia jest
 * sygnalizowany jawnie.
 */
@Composable
fun IrzBadge(
    verified: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = if (verified) {
        AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.primary,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        )
    } else {
        AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    AssistChip(
        onClick = {},
        enabled = false,
        colors = colors,
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = if (verified) Icons.Filled.Verified else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        label = {
            Text(
                if (verified) "Dane potwierdzone w IRZplus" else "Niepotwierdzone w IRZplus",
            )
        },
    )
}

/** Wiersz w układzie „etykieta: wartość” na karcie ogłoszenia. */
@Composable
fun InfoRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}
