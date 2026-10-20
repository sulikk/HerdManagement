package com.example.herdmanagementmobile.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy reguły określającej, które zdarzenia można usunąć z ewidencji.
 *
 * Zdarzenia w stanie [IrzStatus.SUBMITTED] nie mogą być usuwane, ponieważ zgłoszenie
 * mogło zostać już zarejestrowane.
 */
class EventRemovalTest {

    @Test
    fun `niezgloszone i odrzucone wolno usunac`() {
        // Zgłoszenia nie zostały zapisane w rejestrze.
        assertTrue(IrzStatus.PENDING.allowsRemoval)
        assertTrue(IrzStatus.REJECTED.allowsRemoval)
    }

    @Test
    fun `potwierdzonego i wysylanego nie wolno usunac`() {
        // CONFIRMED jest zapisane w rejestrze, SUBMITTED oczekuje na odpowiedź.
        assertFalse(IrzStatus.CONFIRMED.allowsRemoval)
        assertFalse(IrzStatus.SUBMITTED.allowsRemoval)
    }

    @Test
    fun `regula obejmuje dokladnie dwa stany`() {
        // Dodanie nowego statusu wymaga jawnego przypisania go do jednej z grup.
        assertEquals(
            listOf(IrzStatus.PENDING, IrzStatus.REJECTED),
            IrzStatus.entries.filter { it.allowsRemoval },
        )
    }
}
