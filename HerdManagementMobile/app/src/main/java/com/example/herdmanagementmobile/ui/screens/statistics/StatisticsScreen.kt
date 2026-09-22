package com.example.herdmanagementmobile.ui.screens.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.herdmanagementmobile.domain.model.DeadlineText
import com.example.herdmanagementmobile.domain.model.IrzStatus
import com.example.herdmanagementmobile.domain.model.Sex
import com.example.herdmanagementmobile.domain.stats.AgeGroup
import com.example.herdmanagementmobile.domain.stats.HerdStats
import com.example.herdmanagementmobile.domain.stats.MonthlyMovement
import com.example.herdmanagementmobile.ui.common.SectionCard
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

private val PL = Locale.forLanguageTag("pl-PL")

/** Kolory wykresów — zwalidowane pod kątem daltonizmu dla obu motywów. */
private data class ChartColors(
    val inflow: Color,
    val outflow: Color,
    val bar: Color,
    val grid: Color,
    val good: Color = Color(0xFF0CA30C),
    val warning: Color = Color(0xFFFAB219),
    val critical: Color = Color(0xFFD03B3B),
)

@Composable
private fun chartColors(): ChartColors = if (isSystemInDarkTheme()) {
    ChartColors(inflow = Color(0xFF3987E5), outflow = Color(0xFFE66767), bar = Color(0xFF3987E5), grid = Color(0xFF383835))
} else {
    ChartColors(inflow = Color(0xFF2A78D6), outflow = Color(0xFFE34948), bar = Color(0xFF2A78D6), grid = Color(0xFFC3C2B7))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statystyki stada") },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
            )
        },
    ) { padding ->
        val current = stats
        when {
            current == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            current.inHerd == 0 && current.leftHerd == 0 -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Brak danych do statystyk.\nDodaj zwierzęta, aby zobaczyć podsumowanie stada.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            else -> StatisticsContent(
                stats = current,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private enum class StatsTab(val label: String) {
    OVERVIEW("Przegląd"),
    STRUCTURE("Struktura"),
    MOVEMENT("Ruch"),
    IRZ("IRZplus"),
}

/**
 * Statystyki rozłożone na zakładki.
 *
 * Wcześniej wszystkie sekcje leżały jedna pod drugą w jednym przewijaniu —
 * cztery różne pytania („ile mam sztuk”, „jaka jest struktura”, „co się działo
 * przez rok”, „co zalega w IRZ”) konkurowały o tę samą przestrzeń. Każde z nich
 * ma teraz własną zakładkę, a pierwsza odpowiada na najczęstsze z nich bez
 * przewijania.
 */
@Composable
private fun StatisticsContent(stats: HerdStats, modifier: Modifier = Modifier) {
    val colors = chartColors()
    var selected by rememberSaveable { mutableStateOf(0) }
    val tab = StatsTab.entries[selected]

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selected, edgePadding = 16.dp) {
            StatsTab.entries.forEachIndexed { index, entry ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(entry.label) },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (tab) {
                StatsTab.OVERVIEW -> OverviewTab(stats)
                StatsTab.STRUCTURE -> StructureTab(stats, colors)
                StatsTab.MOVEMENT -> SectionCard("Ruch stada — ostatnie 12 miesięcy") {
                    MovementSection(
                        movements = stats.movements,
                        totals = stats.movementTotals,
                        colors = colors,
                    )
                }

                StatsTab.IRZ -> IrzTab(stats, colors)
            }
        }
    }
}

/**
 * Odpowiedź na pytanie „jak stoi stado” bez przewijania i bez wykresu.
 * Liczba jest tu formą docelową — wykres nie dodałby niczego do pojedynczej wartości.
 */
@Composable
private fun OverviewTab(stats: HerdStats) {
    val colors = chartColors()
    val pending = (stats.irzStatusCounts[IrzStatus.PENDING] ?: 0) +
        (stats.irzStatusCounts[IrzStatus.SUBMITTED] ?: 0)
    val rejected = stats.irzStatusCounts[IrzStatus.REJECTED] ?: 0

    HeroNumber(
        value = stats.inHerd.toString(),
        label = "sztuk w stadzie",
        caption = if (stats.leftHerd > 0) {
            "Poza stadem (ubyłe lub padłe): ${stats.leftHerd}"
        } else {
            "Stan na dziś"
        },
    )

    StatTileRow {
        StatTile("W karencji", stats.animalsInWithdrawal.toString())
        StatTile("Leczenia (rok)", stats.treatmentsLast12Months.toString())
        StatTile("Średni wiek", stats.averageAgeMonths.asAge())
    }

    // Bilans roku jest ciekawszy niż suma ruchów: mówi, czy stado rośnie.
    val totals = stats.movementTotals
    val net = totals.inflow - totals.outflow
    SectionCard("Bilans 12 miesięcy") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Counter("Przybyło", totals.inflow, colors.inflow)
            Counter("Ubyło", totals.outflow, colors.outflow)
            Counter(
                label = "Zmiana netto",
                value = net,
                color = if (net < 0) colors.outflow else colors.inflow,
                prefix = if (net > 0) "+" else "",
            )
        }
    }

    // Sekcja pojawia się tylko wtedy, gdy jest co zrobić — pusta lista zadań
    // nie musi zajmować miejsca.
    if (pending > 0 || rejected > 0) {
        SectionCard("Wymaga uwagi") {
            if (pending > 0) {
                StatusRow(
                    Icons.Default.Schedule,
                    colors.warning,
                    "Zdarzenia czekające na zgłoszenie",
                    pending,
                )
            }
            if (rejected > 0) {
                StatusRow(
                    Icons.Default.Error,
                    colors.critical,
                    "Zgłoszenia odrzucone przez rejestr",
                    rejected,
                )
            }
            Caption("Zdarzenie trzeba zgłosić do IRZplus w ciągu 7 dni od jego wystąpienia.")
        }
    }
}

@Composable
private fun StructureTab(stats: HerdStats, colors: ChartColors) {
    SectionCard(
        title = "Grupy użytkowe",
        hint = "Podział według wieku i płci — tak jak opisuje się stado w hodowli.",
    ) {
        // Kategorie puste z natury (nieokreślona płeć, brak daty urodzenia)
        // pokazujemy tylko wtedy, gdy faktycznie coś w nich jest.
        val optional = setOf(AgeGroup.OTHER_SEX, AgeGroup.UNKNOWN_AGE)
        val rows = AgeGroup.entries
            .map { group -> group to (stats.ageGroups[group] ?: 0) }
            .filter { (group, count) -> count > 0 || group !in optional }
            .map { (group, count) -> group.label() to count }

        HorizontalBars(rows = rows, color = colors.bar)
        if (stats.leftHerd > 0) {
            Caption("Poza stadem (ubyłe lub padłe): ${stats.leftHerd}")
        }
    }

    SectionCard("Płeć i wiek") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Counter("Samice", stats.sexCounts[Sex.FEMALE] ?: 0, colors.bar)
            Counter("Samce", stats.sexCounts[Sex.MALE] ?: 0, colors.outflow)
            val other = stats.sexCounts[Sex.OTHER] ?: 0
            if (other > 0) {
                Counter("Nieokreślona", other, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Caption("Średni wiek stada: ${stats.averageAgeMonths.asAge()}")
    }

    if (stats.breeds.isNotEmpty()) {
        SectionCard("Rasy") {
            HorizontalBars(rows = stats.breeds, color = colors.bar)
            Caption("Wykaz obejmuje sztuki obecnie w stadzie.")
        }
    }
}

@Composable
private fun IrzTab(stats: HerdStats, colors: ChartColors) {
    val counts = stats.irzStatusCounts

    // Terminowość to najbliższa miara celu aplikacji: czy hodowca zdąża w 7 dni.
    SectionCard("Terminowość zgłoszeń") {
        val percent = stats.reporting.onTimePercent
        if (percent == null) {
            Caption("Nie wysłano jeszcze żadnego zgłoszenia — nie ma czego podsumować.")
        } else {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.displaySmall,
                color = when {
                    percent >= 90 -> colors.good
                    percent >= 70 -> colors.warning
                    else -> colors.critical
                },
            )
            Text("zgłoszeń wysłanych w ustawowym terminie", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Counter("W terminie", stats.reporting.onTime, colors.good)
                Counter("Po terminie", stats.reporting.late, colors.critical)
            }
            Caption("Liczone są wyłącznie zdarzenia już wysłane do rejestru.")
        }
    }

    SectionCard("Zgłoszenia do IRZplus") {
        StatusRow(Icons.Default.CheckCircle, colors.good, "Potwierdzone", counts[IrzStatus.CONFIRMED] ?: 0)
        StatusRow(
            Icons.Default.Schedule,
            colors.warning,
            "Oczekujące na wysłanie lub odpowiedź",
            (counts[IrzStatus.PENDING] ?: 0) + (counts[IrzStatus.SUBMITTED] ?: 0),
        )
        StatusRow(Icons.Default.Error, colors.critical, "Odrzucone", counts[IrzStatus.REJECTED] ?: 0)
        Caption(
            "Odrzucone zgłoszenia wymagają poprawienia danych i ponownej wysyłki " +
                "z karty zwierzęcia.",
        )
    }
}

// --- Elementy ------------------------------------------------------------

/**
 * Liczba wiodąca. Jedna wartość nie potrzebuje wykresu — potrzebuje rozmiaru
 * i podpisu, który mówi, czego dotyczy i na kiedy jest aktualna.
 */
@Composable
private fun HeroNumber(value: String, label: String, caption: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(value, style = MaterialTheme.typography.displaySmall)
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Caption(caption)
        }
    }
}

/** Liczba z podpisem — używana w rzędach podsumowań. */
@Composable
private fun Counter(label: String, value: Int, color: Color, prefix: String = "") {
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = "$prefix$value",
            style = MaterialTheme.typography.headlineSmall,
            color = if (value == 0) MaterialTheme.colorScheme.onSurfaceVariant else color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Wiek w miesiącach na czytelny opis — „26 mies.” mówi mniej niż „2 lata 2 mies.”. */
private fun Int?.asAge(): String {
    val months = this ?: return "—"
    val years = months / 12
    val rest = months % 12
    return when {
        years == 0 -> "$months mies."
        rest == 0 -> "$years ${DeadlineText.years(years)}"
        else -> "$years ${DeadlineText.years(years)} $rest mies."
    }
}

/**
 * Rząd kafelków o jednakowej wysokości.
 *
 * Same `weight(1f)` wyrównuje tylko szerokość — kafelek z dłuższym podpisem
 * łamał tekst na dwie linie i był wyższy od sąsiadów, przez co rząd wyglądał
 * na krzywy. `IntrinsicSize.Min` każe wszystkim przyjąć wysokość najwyższego.
 */
@Composable
private fun StatTileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun RowScope.StatTile(label: String, value: String) {
    Card(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Poziome słupki jednego koloru z etykietą i wartością przy każdym słupku. */
@Composable
private fun HorizontalBars(rows: List<Pair<String, Int>>, color: Color) {
    val maxValue = max(1, rows.maxOfOrNull { it.second } ?: 0)
    rows.forEach { (label, value) ->
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(value.toString(), style = MaterialTheme.typography.bodyMedium)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            ) {
                if (value > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.toFloat() / maxValue)
                            .height(10.dp)
                            .background(color, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun MovementSection(
    movements: List<MonthlyMovement>,
    totals: MonthlyMovement,
    colors: ChartColors,
) {
    var selected by rememberSaveable { mutableStateOf(movements.lastIndex) }
    val selectedMonth = movements.getOrNull(selected)

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(colors.inflow, "Przybyło")
        LegendItem(colors.outflow, "Ubyło")
    }

    MovementChart(
        movements = movements,
        selected = selected,
        colors = colors,
        onSelect = { selected = it },
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        movements.forEachIndexed { i, m ->
            Text(
                text = m.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, PL),
                style = MaterialTheme.typography.labelSmall,
                color = if (i == selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }

    selectedMonth?.let { m ->
        val name = m.month.month.getDisplayName(TextStyle.FULL_STANDALONE, PL)
            .replaceFirstChar { it.uppercase() }
        Text("$name ${m.month.year}", style = MaterialTheme.typography.titleSmall)
        MovementBreakdown(m)
    }

    Spacer(Modifier.height(4.dp))
    Text("Razem w 12 miesiącach", style = MaterialTheme.typography.titleSmall)
    MovementBreakdown(totals)
}

@Composable
private fun MovementBreakdown(m: MonthlyMovement) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        BreakdownCell("Urodzenia", m.births, Modifier.weight(1f))
        BreakdownCell("Przybycia", m.arrivals, Modifier.weight(1f))
        BreakdownCell("Ubycia", m.departures, Modifier.weight(1f))
        BreakdownCell("Padnięcia", m.deaths, Modifier.weight(1f))
    }
}

@Composable
private fun BreakdownCell(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Słupki rozbieżne: w górę przybyło (urodzenia + przybycia), w dół ubyło
 * (ubycia + padnięcia). Dotknięcie kolumny wybiera miesiąc do podglądu.
 */
@Composable
private fun MovementChart(
    movements: List<MonthlyMovement>,
    selected: Int,
    colors: ChartColors,
    onSelect: (Int) -> Unit,
) {
    val maxValue = max(1, movements.maxOfOrNull { max(it.inflow, it.outflow) } ?: 0)
    val description = remember(movements) {
        movements.joinToString("; ") {
            "${it.month.month.getDisplayName(TextStyle.FULL_STANDALONE, PL)}: " +
                "przybyło ${it.inflow}, ubyło ${it.outflow}"
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = "Ruch stada. $description" }
            .pointerInput(movements.size) {
                detectTapGestures { offset ->
                    val slot = size.width / movements.size.toFloat()
                    onSelect((offset.x / slot).toInt().coerceIn(0, movements.lastIndex))
                }
            },
    ) {
        val slot = size.width / movements.size
        val barWidth = slot * 0.55f
        val mid = size.height / 2f
        val scale = (mid - 4.dp.toPx()) / maxValue
        val radius = CornerRadius(4.dp.toPx())

        // Zaznaczenie wybranego miesiąca — tło kolumny.
        if (selected in movements.indices) {
            drawRect(
                color = colors.grid.copy(alpha = 0.25f),
                topLeft = Offset(slot * selected, 0f),
                size = Size(slot, size.height),
            )
        }

        movements.forEachIndexed { i, m ->
            val left = slot * i + (slot - barWidth) / 2f
            val alpha = if (i == selected) 1f else 0.75f
            if (m.inflow > 0) {
                val top = mid - m.inflow * scale
                drawPath(
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(left, top, left + barWidth, mid - 1.dp.toPx()),
                                topLeft = radius,
                                topRight = radius,
                            ),
                        )
                    },
                    color = colors.inflow.copy(alpha = alpha),
                )
            }
            if (m.outflow > 0) {
                val bottom = mid + m.outflow * scale
                drawPath(
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(left, mid + 1.dp.toPx(), left + barWidth, bottom),
                                bottomLeft = radius,
                                bottomRight = radius,
                            ),
                        )
                    },
                    color = colors.outflow.copy(alpha = alpha),
                )
            }
        }

        // Linia bazowa (zero).
        drawLine(
            color = colors.grid,
            start = Offset(0f, mid),
            end = Offset(size.width, mid),
            strokeWidth = 1.dp.toPx(),
        )
    }
    Caption("Skala: do $maxValue szt. w miesiącu. Dotknij kolumny, aby zobaczyć szczegóły.")
}

@Composable
private fun StatusRow(icon: ImageVector, tint: Color, label: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

private fun AgeGroup.label(): String = when (this) {
    AgeGroup.CALVES -> "Cielęta (do 6 mies.)"
    AgeGroup.HEIFERS -> "Jałówki (6–24 mies.)"
    AgeGroup.YOUNG_BULLS -> "Byczki (6–24 mies.)"
    AgeGroup.COWS -> "Krowy (od 24 mies.)"
    AgeGroup.BULLS -> "Buhaje (od 24 mies.)"
    AgeGroup.OTHER_SEX -> "Płeć nieokreślona"
    AgeGroup.UNKNOWN_AGE -> "Bez daty urodzenia"
}
