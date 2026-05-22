package com.example.herdmanagementmobile.domain.validation

object EarTag {
    private val POLISH_FORMAT = Regex("^PL\\d{12}$")
    private val DIGITS_ONLY = Regex("^\\d{12}$")
    private val WITH_COUNTRY_CODE = Regex("^616\\d{12}$")

    fun isValidFormat(value: String): Boolean = POLISH_FORMAT.matches(value)

    /**
     * Próbuje sparsować surową wartość z kodu kreskowego do kanonicznego formatu
     * polskiego kolczyka urzędowego: PL + 12 cyfr.
     *
     * Akceptuje:
     *  - "PL000123456789" — zwraca bez zmian
     *  - "000123456789"  — prefiksuje "PL"
     *  - "616000123456789" — pomija ISO-3166 numeric (616 = Polska, używane na
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

    // TODO: walidacja cyfry kontrolnej (modulo 10 z wagami) wg dokumentacji ARiMR.
}
