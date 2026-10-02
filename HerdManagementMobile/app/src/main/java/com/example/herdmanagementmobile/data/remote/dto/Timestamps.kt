package com.example.herdmanagementmobile.data.remote.dto

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Przeliczanie znaczników czasu między backendem a telefonem.
 *
 * Backend liczy czas w UTC, ale zapisuje go w SQLite bez oznaczenia strefy —
 * `2026-09-21T13:41:50` oznacza godzinę 13:41 **UTC**, czyli 15:41 w Polsce
 * (CEST = UTC+2). Wcześniej aplikacja parsowała taki napis wprost na
 * [LocalDateTime] i pokazywała go jako czas lokalny, więc każda data z serwera
 * była o dwie godziny do tyłu.
 *
 * Konwersja musi działać w obie strony: wysyłając własny znacznik do backendu
 * trzeba go najpierw sprowadzić do UTC, inaczej serwer zapisze czas lokalny
 * jako uniwersalny i błąd wróci powiększony.
 */

/** Znacznik z backendu (UTC, ze strefą albo bez) na czas lokalny telefonu. */
fun String?.utcToLocal(): LocalDateTime? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        // Postać ze strefą: „…Z” albo „…+02:00”.
        OffsetDateTime.parse(raw)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
    }.recoverCatching {
        // Postać bez strefy — backend zawsze podaje wtedy UTC.
        LocalDateTime.parse(raw)
            .atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
    }.getOrNull()
}

/** Czas lokalny telefonu na znacznik UTC w formacie oczekiwanym przez backend. */
fun LocalDateTime.localToUtc(): String =
    atZone(ZoneId.systemDefault())
        .withZoneSameInstant(ZoneOffset.UTC)
        .toLocalDateTime()
        .toString()
