package com.example.herdmanagementmobile.domain.search

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Ustawienia regionalne języka polskiego, wspólne dla wyszukiwania i sortowania list. */
internal val POLISH: Locale = Locale.forLanguageTag("pl-PL")

/** Komparator napisów zgodny z polskim porządkiem alfabetycznym. */
internal val POLISH_COLLATOR: Collator = Collator.getInstance(POLISH).apply {
    strength = Collator.SECONDARY
}

private val DIACRITICS = "\\p{Mn}+".toRegex()

/**
 * Normalizuje tekst do wyszukiwania: małe litery bez znaków diakrytycznych.
 *
 * Litera `ł` jest zamieniana jawnie, ponieważ normalizacja NFD nie rozkłada jej
 * na literę bazową i znak diakrytyczny.
 */
internal fun String.foldForSearch(): String =
    Normalizer.normalize(trim().lowercase(POLISH), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace('ł', 'l')
