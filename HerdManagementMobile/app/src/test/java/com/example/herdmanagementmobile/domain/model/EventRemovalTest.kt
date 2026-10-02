package com.example.herdmanagementmobile.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy reguły decydującej, które zgłoszenie wolno usunąć z ewidencji.
 *
 * Reguła jest cicha, ale pilnuje zgodności aplikacji z rejestrem państwowym, więc
 * nie może zostać przy okazji „uproszczona” do `status != CONFIRMED`. Różnica
 * dotyczy [IrzStatus.SUBMITTED]: zgłoszenie jest w drodze i jeszcze nie wiadomo,
 * czy rejestr je przyjmie, a usunięcie wpisu w tym momencie zostawiłoby hodowcę
 * bez śladu po zgłoszeniu, które mogło zostać zarejestrowane.
 */
class EventRemovalTest {

    @Test
    fun `niezgloszone i odrzucone wolno usunac`() {
        // W rejestrze nic po nich nie zostało, więc ewidencja niczego nie zafałszuje.
        assertTrue(IrzStatus.PENDING.allowsRemoval)
        assertTrue(IrzStatus.REJECTED.allowsRemoval)
    }

    @Test
    fun `potwierdzonego i wysylanego nie wolno usunac`() {
        // CONFIRMED jest w rejestrze; SUBMITTED czeka na jego odpowiedź.
        assertFalse(IrzStatus.CONFIRMED.allowsRemoval)
        assertFalse(IrzStatus.SUBMITTED.allowsRemoval)
    }

    @Test
    fun `regula obejmuje dokladnie dwa stany`() {
        // Gdyby doszedł nowy status, ten test wymusi świadomą decyzję, a nie
        // milczące wpadnięcie do jednej z grup.
        assertEquals(
            listOf(IrzStatus.PENDING, IrzStatus.REJECTED),
            IrzStatus.entries.filter { it.allowsRemoval },
        )
    }
}
