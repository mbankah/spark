package efa

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Widgets (section 23: WG1 to WG10, B2). The built-in widgets are text only: no badge, no count, no animation (WG7). Adding or enlarging one takes effect at the
 * next 00:00, like icons and the grid; removing or shrinking one takes effect at once (WG4). A widget whose data is old says so and never shows a wrong number
 * (WG9). Android widgets from other apps (WG5, WG6) are not part of this.
 */
enum class WidgetKind(val label: String, val byDefault: Boolean) {
    BUFFER("Buffer", true), NOW_NEXT("Now and next", true), FREE_TO_SPEND("Free to spend", true), DAY_PLAN("Day plan", true),
    SHOPPING("Shopping list", false), QUICK_ADD("Quick-add", false), WEEK_GLANCE("Week glance", false);

    /** B2: the Buffer is always visible, so it cannot be removed. */
    val removable: Boolean get() = this != BUFFER
}

enum class WidgetSize(val cols: Int, val rows: Int, val label: String) {
    ONE_BY_ONE(1, 1, "1x1"), TWO_BY_ONE(2, 1, "2x1"), TWO_BY_TWO(2, 2, "2x2");
    val cells: Int get() = cols * rows
}

/**
 * One placed widget. [activeFrom] is the first day it shows; [nextSize] and [nextFrom] hold an enlargement that has not started (WG4). [position] orders the
 * list.
 */
data class Placement(val kind: WidgetKind, val size: WidgetSize, val position: Int, val activeFrom: LocalDate, val nextSize: WidgetSize? = null, val nextFrom: LocalDate? = null)

/** What a placement is on [today]: not yet shown, or shown at its size once any enlargement has started. */
data class ShownWidget(val kind: WidgetKind, val size: WidgetSize, val pendingFrom: LocalDate?, val pendingSize: WidgetSize?, val shown: Boolean)

fun shownOn(p: Placement, today: LocalDate): ShownWidget {
    val started = !p.activeFrom.isAfter(today)
    val enlarged = p.nextSize != null && p.nextFrom != null && !p.nextFrom.isAfter(today)
    val pending = p.nextSize != null && p.nextFrom != null && p.nextFrom.isAfter(today)
    return ShownWidget(p.kind, if (enlarged) p.nextSize!! else p.size, if (!started) p.activeFrom else if (pending) p.nextFrom else null, if (pending) p.nextSize else null, started)
}

/** The first four widgets, from the first use: the default home (WG1). */
fun defaultPlacements(today: LocalDate): List<Placement> = WidgetKind.entries.filter { it.byDefault }.mapIndexed { i, k ->
    Placement(k, if (k == WidgetKind.DAY_PLAN) WidgetSize.TWO_BY_TWO else WidgetSize.TWO_BY_ONE, i, today)
}

/** WG4: a new widget starts tomorrow. Asking for one that is already placed changes nothing. */
fun addWidget(list: List<Placement>, kind: WidgetKind, size: WidgetSize, today: LocalDate): List<Placement> =
    if (list.any { it.kind == kind }) list else list + Placement(kind, size, (list.maxOfOrNull { it.position } ?: -1) + 1, today.plusDays(1))

/** WG4: removing takes effect at once. The Buffer cannot be removed (B2). */
fun removeWidget(list: List<Placement>, kind: WidgetKind): List<Placement> = if (!kind.removable) list else list.filter { it.kind != kind }

/** WG4: a bigger size starts tomorrow, a smaller one at once. */
fun resizeWidget(list: List<Placement>, kind: WidgetKind, size: WidgetSize, today: LocalDate): List<Placement> = list.map { p ->
    if (p.kind != kind) p
    else {
        val current = shownOn(p, today).size
        when {
            size.cells > current.cells -> p.copy(size = current, nextSize = size, nextFrom = today.plusDays(1))
            else -> p.copy(size = size, nextSize = null, nextFrom = null)
        }
    }
}

/** WG3: move a widget one place up (-1) or down (+1). */
fun moveWidget(list: List<Placement>, kind: WidgetKind, by: Int): List<Placement> {
    val sorted = list.sortedBy { it.position }
    val i = sorted.indexOfFirst { it.kind == kind }
    val j = i + by
    if (i < 0 || j !in sorted.indices) return list
    val m = sorted.toMutableList().also { it[i] = sorted[j]; it[j] = sorted[i] }
    return m.mapIndexed { n, p -> p.copy(position = n) }
}

// ---- what a widget says ----

/** The facts the widgets are built from. [updatedAt] is when they were last worked out (WG9). */
data class WidgetData(
    val buffer: BufferState, val nowLine: String?, val nextLines: List<String>, val freeToSpendPence: Int?, val moneyNote: String?,
    val shoppingLeft: Int?, val week: List<String>, val updatedAt: ZonedDateTime?,
)

const val WIDGET_STALE_AFTER_MIN = 10L

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** WG9: data older than ten minutes, or none, is called stale and the numbers are not shown. */
fun staleSince(data: WidgetData?, now: ZonedDateTime): String? = when {
    data?.updatedAt == null -> "stale: no data yet"
    Duration.between(data.updatedAt, now) > Duration.ofMinutes(WIDGET_STALE_AFTER_MIN) -> "stale since ${HM.format(data.updatedAt)}"
    else -> null
}

/**
 * The text of a widget at a size (WG2, WG7, WG8, WG9). Text only: there is no count, badge or progress animation, and the held notifications are never mentioned.
 * A stale widget shows its age and no number.
 */
fun widgetLines(kind: WidgetKind, size: WidgetSize, data: WidgetData?, now: ZonedDateTime): List<String> {
    val stale = staleSince(data, now)
    if (kind == WidgetKind.QUICK_ADD) return listOf("Quick-add")                        // needs no data, so it is never stale
    if (stale != null || data == null) return listOf(kind.label, stale ?: "stale: no data yet")
    return when (kind) {
        WidgetKind.BUFFER -> when (val b = data.buffer) {
            is BufferState.Value -> listOf("Buffer ${signed(b.result.hours)}h") + if (size.cells > 1) listOfNotNull(b.result.deadlineLabel?.let { "to $it" }) else emptyList()
            BufferState.NoDeadline -> listOf("Buffer", "No deadline")
            else -> listOf("Buffer", "Not available")
        }
        WidgetKind.NOW_NEXT -> listOfNotNull(data.nowLine ?: "Nothing now") + data.nextLines.take(1)
        WidgetKind.FREE_TO_SPEND -> listOf("Free to spend", data.freeToSpendPence?.let { pounds(it) } ?: "Recording")
        WidgetKind.DAY_PLAN -> listOf("Day plan") + data.nextLines.take(if (size == WidgetSize.TWO_BY_TWO) 4 else 2)
        WidgetKind.SHOPPING -> listOf("Shopping list", data.shoppingLeft?.let { "$it left to buy" } ?: "Open the list")
        WidgetKind.WEEK_GLANCE -> listOf("Week") + data.week.take(if (size == WidgetSize.TWO_BY_TWO) 7 else 3)
        WidgetKind.QUICK_ADD -> listOf("Quick-add")
    }
}

private fun signed(h: Double) = (if (h >= 0) "+" else "-") + fmt1(kotlin.math.abs(h))

/** WG8: where tapping a built-in widget goes. */
enum class WidgetTarget { WORKING, DAY, MONEY, SHOPPING, QUICK_ADD, WEEK }

fun widgetTarget(kind: WidgetKind): WidgetTarget = when (kind) {
    WidgetKind.BUFFER -> WidgetTarget.WORKING
    WidgetKind.NOW_NEXT, WidgetKind.DAY_PLAN -> WidgetTarget.DAY
    WidgetKind.FREE_TO_SPEND -> WidgetTarget.MONEY
    WidgetKind.SHOPPING -> WidgetTarget.SHOPPING
    WidgetKind.QUICK_ADD -> WidgetTarget.QUICK_ADD
    WidgetKind.WEEK_GLANCE -> WidgetTarget.WEEK
}

/** WG2: seven days from [from], one line each: how many lectures and when the first starts. A day off says so. */
fun weekGlance(slots: List<PatternSlot>, from: LocalDate, zone: java.time.ZoneId = LONDON): List<String> = (0L..6L).map { d ->
    val day = from.plusDays(d)
    val blocks = slotsFor(slots, day, zone).filter { it.slot.type == "lecture" || it.slot.type == "tutorial" }
    DateTimeFormatter.ofPattern("EEE d", java.util.Locale.UK).format(day) + when {
        isDayOff(slots, day) -> ": day off"
        blocks.isEmpty() -> ": no lectures"
        else -> ": ${blocks.size} from ${HM.format(blocks.first().start)}"
    }
}
