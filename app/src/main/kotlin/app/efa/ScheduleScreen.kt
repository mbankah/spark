package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.RowKind
import efa.RowState
import efa.ScheduleDay
import efa.ScheduleRow
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

/** A day with this much work fills its column in the week chart and its bar. */
private const val FULL_DAY_MIN = 8 * 60

/**
 * The second home page (SC1): the whole calendar. A chart of the next two weeks' work sits on top and each column jumps to its day. Each day is
 * a timeline: a rail down the side with a dot for every block, filled for work, dark for fixed commitments, small for the things around the day,
 * and a ring where there is free time. A finished block is muted and the one running has a NOW tag. It only reads: a block is changed where it
 * already is, never here.
 */
@Composable
fun ScheduleScreen(days: List<ScheduleDay>, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    if (days.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText("Schedule unavailable", style = Ts(c.muted, 16.sp)) }
        return
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(modifier.fillMaxSize().semantics { contentDescription = "Full schedule" }, state = list, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("Schedule", style = Ts(c.text, 30.sp, FontWeight.Bold, letterSpacing = (-0.8).sp))
                WeekChart(days.take(14)) { i -> scope.launch { list.scrollToItem(i + 1) } }
            }
        }
        items(days, key = { it.date.toEpochDay() }) { DayBlock(it) }
    }
}

/** One column per day, as tall as that day's work, the day's initial and date under it. A day off is a dot instead of a column. Tap one to go to that day. */
@Composable
private fun WeekChart(days: List<ScheduleDay>, onDay: (Int) -> Unit) {
    val c = LocalColors.current
    val top = maxOf(FULL_DAY_MIN, days.maxOf { it.workMin })
    Column(Modifier.fillMaxWidth().card().padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label("Work per day")
            Key("Work", c.accent)
        }
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, d ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { onDay(i) }
                        .semantics { contentDescription = "${d.tag ?: d.heading}. ${if (d.dayOff) "Day off" else d.summary().ifBlank { "Nothing planned" }}. Go to this day." },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                        when {
                            d.dayOff -> Box(Modifier.size(6.dp).border(1.5.dp, c.muted, CircleShape))
                            d.workMin > 0 -> Box(Modifier.fillMaxWidth().fillMaxHeight(d.workMin.toFloat() / top).background(if (d.today) c.accent else c.accent.copy(alpha = 0.55f), RoundedCornerShape(4.dp)))
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

@Composable
private fun Key(label: String, colour: Color) {
    val c = LocalColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(colour, CircleShape))
        BasicText(label, style = Ts(c.muted, 11.sp, fontFamily = Mono))
    }
}

@Composable
private fun DayBlock(d: ScheduleDay) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "${d.tag ?: d.heading}. ${if (d.dayOff) "Day off. " else ""}${d.summary().ifBlank { "Nothing planned" }}" },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(d.heading, style = Ts(c.text, 19.sp, FontWeight.Bold, letterSpacing = (-0.3).sp))
                d.tag?.let { Pill(it, filled = d.today) }
                if (d.dayOff) Pill("Day off", filled = true)
            }
            val s = d.summary()
            if (s.isNotBlank()) BasicText(s, style = Ts(c.muted, 12.sp, fontFamily = Mono))
        }
        if (d.workMin > 0) Bar(d.workMin.toFloat() / FULL_DAY_MIN, Modifier.padding(top = 8.dp), height = 4.dp)
        if (d.due.isNotEmpty()) Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            d.due.forEach { due ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("Due ${due.time}", filled = true)
                    BasicText(due.title, style = Ts(c.text, 15.sp, FontWeight.SemiBold))
                }
            }
        }
        if (d.rows.isEmpty()) BasicText(if (d.dayOff) "Nothing counts as lost." else "Nothing planned", style = Ts(c.muted, 14.sp), modifier = Modifier.padding(top = 10.dp))
        Column(Modifier.padding(top = 8.dp)) { d.rows.forEachIndexed { i, r -> TimelineRow(r, first = i == 0, last = i == d.rows.lastIndex) } }
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
            .semantics { contentDescription = "${r.start} to ${r.end}. ${r.title}. ${if (now) "Now. " else if (past) "Finished. " else ""}${r.detail}" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(r.start, style = Ts(if (past || !big) c.muted else c.text, 13.sp, if (big) FontWeight.Medium else null, fontFamily = Mono), modifier = Modifier.width(42.dp).padding(top = if (big) 7.dp else 4.dp))
        Box(Modifier.width(12.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().padding(top = if (first) 12.dp else 0.dp, bottom = if (last) 0.dp else 0.dp).background(c.track))
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
