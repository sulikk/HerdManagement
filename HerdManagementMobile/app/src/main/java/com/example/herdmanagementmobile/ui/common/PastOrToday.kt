package com.example.herdmanagementmobile.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Daty do dziś włącznie.
 *
 * Zdarzenie zwierzęce i zabieg to zapisy tego, co się wydarzyło — data z przyszłości
 * jest zawsze pomyłką. Blokujemy ją już w kalendarzu, zamiast odrzucać przy zapisie;
 * rejestr IRZplus i tak zwróciłby wtedy błąd ERR_DATA_PRZYSZLA.
 */
@OptIn(ExperimentalMaterial3Api::class)
object PastOrToday : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !utcTimeMillis.toLocalDate().isAfter(LocalDate.now())

    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}

/** Konwersja z licznika milisekund używanego przez `DatePicker`. */
fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
