package com.example.herdmanagementmobile.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarTagTest {

    /** Rzeczywiste numery buhajów z publicznych katalogów (MCHiRZ, PZHiPBM). */
    private val realTags = listOf(
        "PL005506613419", "PL005544460631", "PL005544460945", "PL005458914206",
        "PL005539084644", "PL005514104091", "PL005553856104", "PL005481628606",
        "PL005546525222", "PL005499385546", "PL005520898182", "PL005440823394",
        "PL005000168286", "PL005002311710", "PL005308805029", "PL005223287726",
    )

    @Test
    fun `rzeczywiste numery mają poprawną cyfrę kontrolną`() {
        realTags.forEach { assertTrue(it, EarTag.isValid(it)) }
    }

    @Test
    fun `zmiana dowolnej cyfry psuje cyfrę kontrolną`() {
        val tag = "PL005506613419"
        for (pos in 2 until tag.length) {
            val original = tag[pos].digitToInt()
            val changed = tag.substring(0, pos) + ((original + 1) % 10) + tag.substring(pos + 1)
            assertFalse(changed, EarTag.hasValidCheckDigit(changed))
        }
    }

    @Test
    fun `zamiana sąsiednich cyfr jest wykrywana`() {
        // PL0055|06|613419 -> PL0055|60|613419. Wagi 3/1 wykrywają zamianę,
        // o ile cyfry nie różnią się o 5 (ograniczenie schematu EAN).
        assertFalse(EarTag.hasValidCheckDigit("PL005560613419"))
    }

    @Test
    fun `obliczenie cyfry kontrolnej`() {
        assertEquals(9, EarTag.checkDigit("00550661341"))
        assertEquals(4, EarTag.checkDigit("00012345678"))
        assertEquals(0, EarTag.checkDigit("00000000000"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checkDigit wymaga 11 cyfr`() {
        EarTag.checkDigit("123")
    }

    @Test
    fun `zły format nie przechodzi walidacji`() {
        listOf("", "PL123", "DE005506613419", "PL00550661341X", "005506613419").forEach {
            assertFalse(it, EarTag.isValid(it))
            assertFalse(it, EarTag.hasValidCheckDigit(it))
        }
    }

    @Test
    fun `parseBarcode normalizuje warianty zapisu`() {
        assertEquals("PL005506613419", EarTag.parseBarcode("PL005506613419"))
        assertEquals("PL005506613419", EarTag.parseBarcode("pl 0055 0661 3419"))
        assertEquals("PL005506613419", EarTag.parseBarcode("005506613419"))
        assertEquals("PL005506613419", EarTag.parseBarcode("616005506613419"))
        assertNull(EarTag.parseBarcode("12345"))
        assertNull(EarTag.parseBarcode(null))
    }
}
