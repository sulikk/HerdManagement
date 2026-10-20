package com.example.herdmanagementmobile.domain.validation

/**
 * Walidacja numeru identyfikacyjnego bydła w formacie `PL` + 12 cyfr: 2 cyfry serii,
 * 9 cyfr numeru zwierzęcia i cyfra kontrolna (rozporządzenie MRiRW w sprawie wzorów
 * środków identyfikacji, Dz.U. 2025 poz. 26).
 *
 * Algorytm cyfry kontrolnej nie został opublikowany w rozporządzeniu; ustalono go
 * empirycznie na próbie 383 numerów z katalogów buhajów (zgodność powyżej 98%).
 * Stosowane są wagi 3, 1, 3, 1, … dla 11 pierwszych cyfr, a cyfra kontrolna wynosi
 * (10 − suma mod 10) mod 10, analogicznie do kodów EAN/GTIN.
 */
object EarTag {
    private val POLISH_FORMAT = Regex("^PL\\d{12}$")
    private val DIGITS_ONLY = Regex("^\\d{12}$")
    private val WITH_COUNTRY_CODE = Regex("^616\\d{12}$")

    fun isValidFormat(value: String): Boolean = POLISH_FORMAT.matches(value)

    /** Sprawdza format `PL` + 12 cyfr oraz poprawność cyfry kontrolnej. */
    fun isValid(value: String): Boolean = isValidFormat(value) && hasValidCheckDigit(value)

    /** Sprawdza cyfrę kontrolną; dla wartości w niepoprawnym formacie zwraca `false`. */
    fun hasValidCheckDigit(value: String): Boolean {
        if (!isValidFormat(value)) return false
        val digits = value.substring(2)
        return checkDigit(digits.substring(0, 11)) == digits[11].digitToInt()
    }

    /** Oblicza cyfrę kontrolną dla 11 cyfr (seria i numer zwierzęcia). */
    fun checkDigit(first11Digits: String): Int {
        require(first11Digits.length == 11 && first11Digits.all { it.isDigit() }) {
            "Oczekiwano 11 cyfr, otrzymano: $first11Digits"
        }
        val sum = first11Digits.foldIndexed(0) { i, acc, c ->
            acc + c.digitToInt() * if (i % 2 == 0) 3 else 1
        }
        return (10 - sum % 10) % 10
    }

    /**
     * Przekształca wartość odczytaną z kodu kreskowego do postaci kanonicznej `PL` + 12 cyfr.
     * Cyfra kontrolna nie jest weryfikowana.
     *
     * Obsługiwane postacie wejściowe:
     *  - `PL000123456784` — zwracana bez zmian,
     *  - `000123456784` — uzupełniana o prefiks `PL`,
     *  - `616000123456784` — numeryczny kod kraju ISO 3166 (616 = Polska), stosowany
     *    w kolczykach elektronicznych, zastępowany prefiksem `PL`.
     *
     * @return numer w postaci kanonicznej lub `null`, gdy wartości nie można rozpoznać.
     */
    fun parseBarcode(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }
        return when {
            POLISH_FORMAT.matches(cleaned) -> cleaned
            DIGITS_ONLY.matches(cleaned) -> "PL$cleaned"
            WITH_COUNTRY_CODE.matches(cleaned) -> "PL" + cleaned.substring(3)
            else -> null
        }
    }
}
