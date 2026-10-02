package com.example.herdmanagementmobile.domain.search

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Wspólne narzędzia wyszukiwania tekstowego dla list w aplikacji.
 *
 * Ten sam sposób dopasowania obowiązuje na liście stada i na liście ogłoszeń —
 * gdyby każdy ekran normalizował tekst po swojemu, „latka” raz znajdowałoby
 * „Łatkę”, a raz nie.
 */
internal val POLISH: Locale = Locale.forLanguageTag("pl-PL")

/** Porównuje napisy po polsku — inaczej „Ł” lądowałoby za „Z”. */
internal val POLISH_COLLATOR: Collator = Collator.getInstance(POLISH).apply {
    strength = Collator.SECONDARY
}

private val DIACRITICS = "\\p{Mn}+".toRegex()

/**
 * Sprowadza tekst do postaci porównywalnej: małe litery bez znaków diakrytycznych.
 * Dzięki temu „latka” znajduje „Łatka”, a „simentalska” — „Simentalską”.
 *
 * `ł` obsługujemy osobno: w przeciwieństwie do `ą` czy `ę` nie rozkłada się
 * w normalizacji NFD na literę bazową i znak diakrytyczny.
 */
internal fun String.foldForSearch(): String =
    Normalizer.normalize(trim().lowercase(POLISH), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace('ł', 'l')
