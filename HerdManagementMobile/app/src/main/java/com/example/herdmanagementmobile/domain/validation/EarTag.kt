package com.example.herdmanagementmobile.domain.validation

/**
 * Numer identyfikacyjny bydła: "PL" + 12 cyfr, gdzie 12 cyfr to
 * 2 cyfry numeru serii + 9 cyfr numeru zwierzęcia + cyfra kontrolna
 * (rozporządzenie MRiRW w sprawie wzorów środków identyfikacji, Dz.U. 2025 poz. 26).
 *
 * Algorytm cyfry kontrolnej nie jest opublikowany w rozporządzeniu — ustalony
 * empirycznie na numerach z katalogów buhajów (383 numery, zgodność > 98%):
 * wagi 3,1,3,1,... dla 11 pierwszych cyfr, cyfra = (10 − suma mod 10) mod 10
 * (ten sam schemat co w kodach EAN/GTIN).
 */
object EarTag {
    private val POLISH_FORMAT = Regex("^PL\\d{12}$")
    private val DIGITS_ONLY = Regex("^\\d{12}$")
    private val WITH_COUNTRY_CODE = Regex("^616\\d{12}$")

    fun isValidFormat(value: String): Boolean = POLISH_FORMAT.matches(value)

    /** Format PL + 12 cyfr oraz poprawna cyfra kontrolna. */
    fun isValid(value: String): Boolean = isValidFormat(value) && hasValidCheckDigit(value)

    /** Zakłada poprawny format — dla innych wartości zwraca false. */
    fun hasValidCheckDigit(value: String): Boolean {
        if (!isValidFormat(value)) return false
        val digits = value.substring(2)
        return checkDigit(digits.substring(0, 11)) == digits[11].digitToInt()
    }

    /** Cyfra kontrolna dla 11 cyfr (seria + numer zwierzęcia). */
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
     * Próbuje sparsować surową wartość z kodu kreskowego do kanonicznego formatu
     * polskiego kolczyka urzędowego: PL + 12 cyfr. Nie sprawdza cyfry kontrolnej.
     *
     * Akceptuje:
     *  - "PL000123456784" — zwraca bez zmian
     *  - "000123456784"  — prefiksuje "PL"
     *  - "616000123456784" — pomija ISO-3166 numeric (616 = Polska, używane na
     *    kolczykach radiowych) i prefiksuje "PL"
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
