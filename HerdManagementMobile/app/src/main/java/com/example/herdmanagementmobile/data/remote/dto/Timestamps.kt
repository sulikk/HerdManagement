package com.example.herdmanagementmobile.data.remote.dto

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Konwertuje znacznik czasu z backendu na czas lokalny urządzenia.
 *
 * Backend przechowuje czas w UTC, często bez oznaczenia strefy (np. `2026-09-21T13:41:50`
 * oznacza 13:41 UTC). Obsługiwane są obie postacie: ze strefą i bez niej.
 */
fun String?.utcToLocal(): LocalDateTime? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        OffsetDateTime.parse(raw)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
    }.recoverCatching {
        LocalDateTime.parse(raw)
            .atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
    }.getOrNull()
}

/** Konwertuje czas lokalny urządzenia na znacznik UTC w formacie oczekiwanym przez backend. */
fun LocalDateTime.localToUtc(): String =
    atZone(ZoneId.systemDefault())
        .withZoneSameInstant(ZoneOffset.UTC)
        .toLocalDateTime()
        .toString()
