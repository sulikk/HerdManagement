package com.example.herdmanagementmobile.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testy odmiany rzeczowników i czasowników przy liczebnikach w opisach terminów,
 * ze szczególnym uwzględnieniem liczb 12–14.
 */
class DeadlineTextTest {

    @Test
    fun `jedynka bierze liczbe pojedyncza`() {
        assertEquals("Został 1 dzień", DeadlineText.timeLeft(1))
    }

    @Test
    fun `dwa trzy cztery biora forme mnoga`() {
        assertEquals("Zostały 2 dni", DeadlineText.timeLeft(2))
        assertEquals("Zostały 3 dni", DeadlineText.timeLeft(3))
        assertEquals("Zostały 4 dni", DeadlineText.timeLeft(4))
    }

    @Test
    fun `piec i wiecej bierze forme nijaka`() {
        assertEquals("Zostało 5 dni", DeadlineText.timeLeft(5))
        assertEquals("Zostało 7 dni", DeadlineText.timeLeft(7))
    }

    @Test
    fun `nastki zachowuja sie jak liczby duze`() {
        // Liczby 12–14 przyjmują formę „zostało”.
        assertEquals("Zostało 12 dni", DeadlineText.timeLeft(12))
        assertEquals("Zostało 13 dni", DeadlineText.timeLeft(13))
        assertEquals("Zostało 14 dni", DeadlineText.timeLeft(14))
    }

    @Test
    fun `dziesiatki powyzej nastek wracaja do formy mnogiej`() {
        assertEquals("Zostały 22 dni", DeadlineText.timeLeft(22))
        assertEquals("Zostały 33 dni", DeadlineText.timeLeft(33))
        assertEquals("Zostało 25 dni", DeadlineText.timeLeft(25))
    }

    @Test
    fun `zero to termin dzisiaj`() {
        assertEquals("Termin dziś", DeadlineText.timeLeft(0))
    }

    @Test
    fun `przekroczony termin opisany jest osobno`() {
        assertEquals("Po terminie o 1 dzień", DeadlineText.timeLeft(-1))
        assertEquals("Po terminie o 3 dni", DeadlineText.timeLeft(-3))
        assertEquals("Po terminie o 11 dni", DeadlineText.timeLeft(-11))
    }

    @Test
    fun `miesiace odmieniaja sie trzystopniowo`() {
        assertEquals("miesiąc", DeadlineText.months(1))
        assertEquals("miesiące", DeadlineText.months(3))
        assertEquals("miesięcy", DeadlineText.months(5))
        assertEquals("miesięcy", DeadlineText.months(13))
        assertEquals("miesiące", DeadlineText.months(22))
        assertEquals("miesięcy", DeadlineText.months(0))
    }

    @Test
    fun `lata odmieniaja sie trzystopniowo`() {
        assertEquals("rok", DeadlineText.years(1))
        assertEquals("lata", DeadlineText.years(2))
        assertEquals("lata", DeadlineText.years(4))
        assertEquals("lat", DeadlineText.years(5))
        assertEquals("lat", DeadlineText.years(11))
        assertEquals("lat", DeadlineText.years(12))
        assertEquals("lat", DeadlineText.years(14))
        assertEquals("lata", DeadlineText.years(22))
    }

    @Test
    fun `rzeczownik odmienia sie tylko dla jedynki`() {
        assertEquals("dzień", DeadlineText.days(1))
        assertEquals("dni", DeadlineText.days(0))
        assertEquals("dni", DeadlineText.days(2))
        assertEquals("dni", DeadlineText.days(21))
    }
}
