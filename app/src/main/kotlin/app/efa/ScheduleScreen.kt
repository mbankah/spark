package app.efa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.RowKind
import efa.RowState
import efa.ScheduleDay
import efa.ScheduleRow
import efa.ScheduleWeek
import efa.scheduleWeeks
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A day with this much work fills its column in the week chart and its bar. */
private const val FULL_DAY_MIN = 8 * 60

/**
 * The second home page (SC1). A carousel of weeks sits on top: swipe for the next one, each with a chart of its work per day. Under it are that
 * week's days, each folded to a header (its date, its hours and a bar) and opened with a tap; today starts open. Tapping a column of the chart opens
 * that day and takes you to it. An open day is a timeline: a rail down the side with a dot for every block, filled for work, dark for fixed
 * commitments, small for the things around the day, and a ring where there is free time. A finished block is muted and the one running has a NOW
 * tag. It only reads: a block is changed where it already is, never here. Things move only briefly and only when you cause it (AN1).
 */
@Composable
fun ScheduleScreen(days: List<ScheduleDay>, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    if (days.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText("Schedule unavailable", style = Ts(c.muted, 16.sp)) }
        return
    }
    val weeks = remember(days) { scheduleWeeks(days) }
    val top = remember(days) { maxOf(FULL_DAY_MIN, days.maxOf { it.workMin }) }
    val pager = rememberPagerState(initialPage = weeks.indexOfFirst { w -> w.days.any { it.today } }.coerceAtLeast(0)) { weeks.size }
    val open = remember { mutableStateMapOf<LocalDate, Boolean>().apply { days.firstOrNull { it.today }?.let { put(it.date, true) } } }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val week = weeks[pager.currentPage.coerceIn(0, weeks.lastIndex)]
    LazyColumn(modifier.fillMaxSize().semantics { contentDescription = "Full schedule" }, state = list, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item(key = "title") { BasicText("Schedule", style = Ts(c.text, 30.sp, FontWeight.Bold, letterSpacing = (-0.8).sp)) }
        item(key = "weeks") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 12.dp) { i ->
                    WeekPage(weeks[i], top) { d ->
                        open[d.date] = true
                        val at = week.days.indexOfFirst { it.date == d.date }
                        if (at >= 0) scope.launch { list.animateScrollToItem(2 + at) }
                    }
                }
                WeekDots(weeks.size, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
            }
        }
        items(week.days, key = { it.date.toEpochDay() }) { d ->
            DayBlock(d, open[d.date] == true, { open[d.date] = open[d.date] != true }, Modifier.animateItem())
        }
    }
}

/** One week: its name and dates, what it holds, and a column per day as tall as that day's work. A day off is a ring. Tap a column to open that day. */
@Composable
private fun WeekPage(w: ScheduleWeek, top: Int, onDay: (ScheduleDay) -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                BasicText(w.label, style = Ts(c.text, 19.sp, FontWeight.Bold, letterSpacing = (-0.3).sp))
                BasicText(w.range, style = Ts(c.muted, 12.sp, fontFamily = Mono))
            }
            BasicText(w.summary(), style = Ts(c.muted, 12.sp, fontFamily = Mono), modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp))
        }
        Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            w.days.forEach { d ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { onDay(d) }
                        .semantics { contentDescription = "${d.tag ?: d.heading}. ${if (d.dayOff) "Day off" else d.summary().ifBlank { "Nothing planned" }}. Open this day." },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                        val f = animatedFraction(d.workMin.toFloat() / top)
                        when {
                            d.dayOff -> Box(Modifier.size(6.dp).border(1.5.dp, c.muted, CircleShape))
                            d.workMin > 0 -> Box(Modifier.fillMaxWidth().fillMaxHeight(f).background(if (d.today) c.accent else c.accent.copy(alpha = 0.55f), RoundedCornerShape(4.dp)))
                            else -> Box(Modifier.fillMaxWidth().height(2.dp).background(c.track))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    BasicText(d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.UK), style = Ts(if (d.today) c.text else c.muted, 11.sp, if (d.today) FontWeight.Bold else FontWeight.Medium, fontFamily = Mono))
                    BasicText("${d.date.dayOfMonth}", style = Ts(if (d.today) c.accentText else c.muted, 11.sp, if (d.today) FontWeight.Bold else null, fontFamily = Mono))
                }
            }
        }
    }
}

/** One dot per week; the current one is a longer pill that grows into place. */
@Composable
private fun WeekDots(count: Int, page: Int, onPage: (Int) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w = animateDpAsState(if (i == page) 20.dp else 7.dp, motion(Motion.STANDARD), label = "weekdot").value
            Box(Modifier.heightIn(min = 28.dp).widthIn(min = 28.dp).clickable(role = Role.Tab) { onPage(i) }.semantics { contentDescription = "Week ${i + 1} of $count" + if (i == page) ", showing" else "" }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = w, height = 7.dp).background(if (i == page) c.accent else c.track, CircleShape))
            }
        }
    }
}

/** A day folded to its header, opened by a tap. The header says the date, what the day holds and, as a bar, how much work. */
@Composable
private fun DayBlock(d: ScheduleDay, open: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    val enter = if (LocalMotion.current) expandVertically(motion(Motion.STANDARD)) + fadeIn(motion(Motion.QUICK)) else EnterTransition.None
    val exit = if (LocalMotion.current) shrinkVertically(motion(Motion.STANDARD)) + fadeOut(motion(Motion.QUICK)) else ExitTransition.None
    Column(modifier.fillMaxWidth().card()) {
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (open) "Close this day" else "Open this day", onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics { contentDescription = "${d.tag ?: d.heading}. ${if (d.dayOff) "Day off. " else ""}${d.summary().ifBlank { "Nothing planned" }}"; stateDescription = if (open) "Open" else "Closed" },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(d.heading, style = Ts(c.text, 19.sp, FontWeight.Bold, letterSpacing = (-0.3).sp))
                        d.tag?.let { Pill(it, filled = d.today) }
                        if (d.dayOff) Pill("Day off", filled = true)
                        if (d.due.isNotEmpty()) Pill("${d.due.size} due")
                    }
                    val s = d.summary()
                    if (s.isNotBlank()) BasicText(s, style = Ts(c.muted, 12.sp, fontFamily = Mono))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // How the day's planned time divides: work, fixed commitments, and what is left as free time or the small things.
                    val total = (d.workMin + d.fixedMin + d.freeMin).coerceAtLeast(1)
                    if (d.workMin + d.fixedMin > 0) Donut(listOf(Slice(d.workMin.toFloat() / total, c.accent), Slice(d.fixedMin.toFloat() / total, c.text)), 40.dp, 7.dp, gapDegrees = 4f)
                    Chevron(open)
                }
            }
        }
        AnimatedVisibility(open, enter = enter, exit = exit) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                if (d.due.isNotEmpty()) Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.due.forEach { due ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pill("Due ${due.time}", filled = true)
                            BasicText(due.title, style = Ts(c.text, 15.sp, FontWeight.SemiBold))
                        }
                    }
                }
                if (d.rows.isEmpty()) BasicText(if (d.dayOff) "Nothing counts as lost." else "Nothing planned", style = Ts(c.muted, 14.sp))
                d.rows.forEachIndexed { i, r -> TimelineRow(r, first = i == 0, last = i == d.rows.lastIndex) }
            }
        }
    }
}

/** One block on the rail. Work and fixed blocks carry their title in full type; free time and the small things are one quiet line. */
@Composable
private fun TimelineRow(r: ScheduleRow, first: Boolean, last: Boolean) {
    val c = LocalColors.current
    val past = r.state == RowState.PAST
    val now = r.state == RowState.NOW
    val big = r.kind == RowKind.WORK || r.kind == RowKind.FIXED
    val dot = when {
        past -> c.track
        r.kind == RowKind.WORK -> c.accent
        r.kind == RowKind.FIXED -> c.text
        else -> c.track
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .semantics { contentDescription = "${r.start} to ${r.end}. ${r.title}. ${if (now) "Now. " else if (past) "Finished. " else ""}${r.detail}${r.photo?.let { " Photo: ${it.tag()}." } ?: ""}" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(r.start, style = Ts(if (past || !big) c.muted else c.text, 13.sp, if (big) FontWeight.Medium else null, fontFamily = Mono), modifier = Modifier.width(42.dp).padding(top = if (big) 7.dp else 4.dp))
        Box(Modifier.width(12.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().padding(top = if (first) 12.dp else 0.dp).background(c.track))
            val topPad = if (big) 10.dp else 8.dp
            when {
                r.kind == RowKind.FREE -> Box(Modifier.padding(top = topPad - 2.dp).size(10.dp).background(c.card, CircleShape).border(2.dp, c.track, CircleShape))
                big -> Box(Modifier.padding(top = topPad - 2.dp).size(if (now) 14.dp else 10.dp).background(dot, CircleShape).border(2.dp, c.card, CircleShape))
                else -> Box(Modifier.padding(top = topPad).size(6.dp).background(c.track, CircleShape))
            }
        }
        if (big) Column(
            Modifier.weight(1f).padding(vertical = 5.dp).then(if (now) Modifier.background(c.bg, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp) else Modifier),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(r.title, style = Ts(if (past) c.muted else c.text, 15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f, fill = false))
                if (now) Pill("Now", filled = true)
            }
            if (r.detail.isNotBlank()) BasicText(r.detail, style = Ts(c.muted, 12.5.sp))
            BasicText(r.start + " to " + r.end + (if (r.nextDay) " +1" else "") + "  " + duration(r.minutes), style = Ts(c.muted, 11.5.sp, fontFamily = Mono))
            // PH1: what to photograph, and when, on the block itself.
            r.photo?.let { p -> Box(Modifier.padding(top = 3.dp)) { Pill(p.tag(), filled = p.open) } }
        } else Row(Modifier.weight(1f).padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(if (r.kind == RowKind.FREE) "Free" else r.title, style = Ts(c.muted, 13.sp, if (r.kind == RowKind.FREE) FontWeight.Medium else null))
            BasicText(duration(r.minutes), style = Ts(c.muted, 11.sp, fontFamily = Mono))
        }
    }
}

/** "45m", "1h", "1h 30m". */
internal fun duration(min: Int): String = when {
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h ${min % 60}m"
}
