package com.example.herdmanagementmobile.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Ogranicza wybór w kalendarzu do dat nie późniejszych niż bieżąca. Zdarzenia i zabiegi
 * dotyczą przeszłości, a IRZplus odrzuca daty przyszłe (błąd ERR_DATA_PRZYSZLA).
 */
@OptIn(ExperimentalMaterial3Api::class)
object PastOrToday : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !utcTimeMillis.toLocalDate().isAfter(LocalDate.now())

    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}

/** Konwertuje liczbę milisekund (UTC) zwracaną przez `DatePicker` na [LocalDate]. */
fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
