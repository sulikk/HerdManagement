package com.example.herdmanagementmobile.domain.model

/**
 * Opisy terminów w języku polskim z poprawną odmianą rzeczowników i czasowników
 * przy liczebnikach, np. „został 1 dzień”, „zostały 2 dni”, „zostało 5 dni”.
 */
object DeadlineText {

    /** Zwraca formę „dzień” dla liczby 1, a „dni” w pozostałych przypadkach. */
    fun days(count: Long): String = if (count == 1L) "dzień" else "dni"

    /** Zwraca odpowiednią formę: „miesiąc”, „miesiące” lub „miesięcy”. */
    fun months(count: Long): String {
        val last = count % 10
        val lastTwo = count % 100
        return when {
            count == 1L -> "miesiąc"
            last in 2..4 && lastTwo !in 12..14 -> "miesiące"
            else -> "miesięcy"
        }
    }

    /**
     * Zwraca odpowiednią formę: „rok”, „lata” lub „lat”. Liczby kończące się na 2–4
     * przyjmują formę „lata”, z wyjątkiem 12–14.
     */
    fun years(count: Int): String {
        val last = count % 10
        val lastTwo = count % 100
        return when {
            count == 1 -> "rok"
            last in 2..4 && lastTwo !in 12..14 -> "lata"
            else -> "lat"
        }
    }

    /**
     * Zwraca formę czasownika „zostać” uzgodnioną z liczebnikiem. Liczby kończące się
     * na 2–4 (z wyjątkiem 12–14) przyjmują formę „Zostały”, pozostałe — „Zostało”.
     */
    fun remainVerb(count: Long): String {
        val last = count % 10
        val lastTwo = count % 100
        return when {
            count == 1L -> "Został"
            last in 2..4 && lastTwo !in 12..14 -> "Zostały"
            else -> "Zostało"
        }
    }

    /** Zwraca opis czasu pozostałego do terminu, np. „Zostały 3 dni” lub „Termin dziś”. */
    fun timeLeft(daysLeft: Long): String = when {
        daysLeft == 0L -> "Termin dziś"
        daysLeft < 0 -> overdue(daysLeft)
        else -> "${remainVerb(daysLeft)} $daysLeft ${days(daysLeft)}"
    }

    /** Zwraca opis przekroczenia terminu, np. „Po terminie o 1 dzień”. */
    fun overdue(daysLeft: Long): String {
        val late = if (daysLeft < 0) -daysLeft else daysLeft
        return "Po terminie o $late ${days(late)}"
    }
}
