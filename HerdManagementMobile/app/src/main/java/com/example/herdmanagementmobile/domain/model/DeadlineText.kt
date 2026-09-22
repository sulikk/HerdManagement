package com.example.herdmanagementmobile.domain.model

/**
 * Opisy terminów po polsku, z poprawną odmianą liczebników.
 *
 * Polszczyzna wymaga trzech form naraz — rzeczownika i czasownika zależnych od
 * liczby: „został 1 dzień”, „zostały 2 dni”, „zostało 5 dni”. Sklejanie tego
 * w locie („Zostały 1 dni”) widać od razu i psuje odbiór całej aplikacji,
 * a reguła jest na tyle nieoczywista, że zasługuje na własne testy.
 */
object DeadlineText {

    /** Rzeczownik: „dzień” tylko dla jedynki, poza tym zawsze „dni”. */
    fun days(count: Long): String = if (count == 1L) "dzień" else "dni"

    /** Odmiana „miesiąc / miesiące / miesięcy” — ta sama reguła co przy latach. */
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
     * Odmiana „rok / lata / lat”.
     *
     * Tu, w przeciwieństwie do „dni”, forma dla grupy 2–4 jest inna niż dla
     * reszty — i znowu nastki (12–14) idą z liczbą dużą, mimo końcówki 2–4.
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
     * Forma czasownika przy liczebniku.
     *
     * Grupa 2–4 (z wyjątkiem nastek: 12, 13, 14) bierze liczbę mnogą „zostały”,
     * reszta — formę nijaką „zostało”.
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

    /** Pełny opis czasu do terminu, np. „Zostały 3 dni” albo „Termin dziś”. */
    fun timeLeft(daysLeft: Long): String = when {
        daysLeft == 0L -> "Termin dziś"
        daysLeft < 0 -> overdue(daysLeft)
        else -> "${remainVerb(daysLeft)} $daysLeft ${days(daysLeft)}"
    }

    /** Opis przekroczenia terminu, np. „Po terminie o 1 dzień”. */
    fun overdue(daysLeft: Long): String {
        val late = if (daysLeft < 0) -daysLeft else daysLeft
        return "Po terminie o $late ${days(late)}"
    }
}
