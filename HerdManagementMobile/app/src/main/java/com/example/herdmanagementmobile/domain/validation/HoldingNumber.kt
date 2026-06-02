package com.example.herdmanagementmobile.domain.validation

/**
 * Walidacja identyfikatorów gospodarstwa wg konwencji ARiMR:
 * - numer siedziby stada: "PL" + 9 cyfr (numer identyfikacyjny posiadacza),
 * - numer producenta (EP): 9 cyfr.
 */
object HoldingNumber {
    private val HOLDING_FORMAT = Regex("^PL\\d{9}$")
    private val PRODUCER_FORMAT = Regex("^\\d{9}$")

    fun isValidHolding(value: String): Boolean = HOLDING_FORMAT.matches(value)
    fun isValidProducer(value: String): Boolean = PRODUCER_FORMAT.matches(value)
}
