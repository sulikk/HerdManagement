package com.example.herdmanagementmobile.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Konwersja znaczników czasu między backendem (UTC) a telefonem (czas lokalny).
 *
 * Testy pilnują realnego błędu: data zatwierdzenia ogłoszenia w IRZplus pokazywała
 * się o dwie godziny wcześniej, bo napis `2026-09-21T13:41:50` z backendu oznacza
 * godzinę UTC, a aplikacja traktowała go jak czas lokalny.
 *
 * Asercje liczą oczekiwany wynik przez [ZoneId.systemDefault], więc test jest
 * poprawny niezależnie od strefy maszyny, na której się uruchamia.
 */
class TimestampsTest {

    private fun expectedLocal(instant: String): LocalDateTime =
        Instant.parse(instant).atZone(ZoneId.systemDefault()).toLocalDateTime()

    @Test
    fun `znacznik bez strefy jest traktowany jako UTC`() {
        assertEquals(
            expectedLocal("2026-09-21T13:41:50Z"),
            "2026-09-21T13:41:50".utcToLocal(),
        )
    }

    @Test
    fun `znacznik z sufiksem Z jest przeliczany na czas lokalny`() {
        assertEquals(
            expectedLocal("2026-09-21T22:06:39Z"),
            "2026-09-21T22:06:39Z".utcToLocal(),
        )
    }

    @Test
    fun `znacznik z jawnym przesunieciem strefowym zachowuje ten sam moment`() {
        // 15:41+02:00 to ten sam moment co 13:41 UTC.
        assertEquals(
            expectedLocal("2026-09-21T13:41:50Z"),
            "2026-09-21T15:41:50+02:00".utcToLocal(),
        )
    }

    @Test
    fun `ulamki sekundy nie psuja parsowania`() {
        assertEquals(
            expectedLocal("2026-09-21T13:41:50.102422Z"),
            "2026-09-21T13:41:50.102422".utcToLocal(),
        )
    }

    @Test
    fun `pusty i niepoprawny napis daje null`() {
        assertNull(null.utcToLocal())
        assertNull("".utcToLocal())
        assertNull("   ".utcToLocal())
        assertNull("wczoraj".utcToLocal())
    }

    @Test
    fun `czas lokalny jest sprowadzany do UTC przy wysylce`() {
        val local = expectedLocal("2026-09-21T13:41:50Z")
        assertEquals("2026-09-21T13:41:50", local.localToUtc())
    }

    @Test
    fun `konwersja tam i z powrotem nie przesuwa czasu`() {
        // To był sedno błędu: wysyłany czas lokalny wracał powiększony o offset.
        val local = LocalDateTime.of(2026, 6, 15, 9, 30, 0)
        assertEquals(local, local.localToUtc().utcToLocal())
    }
}
