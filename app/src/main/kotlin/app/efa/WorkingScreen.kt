package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.BufferResult
import efa.WorkingLine
import efa.fmt1

/**
 * B3: tapping the Buffer shows every task, its heaviness, its hours and the sum. Nothing is hidden (P4). The sum is laid out as the sum it is,
 * available minus work equals the Buffer, and every task is a row of the same numbers the old lines gave. The bars are scaled from the figures
 * beside them and carry nothing those do not say.
 */
@Composable
fun WorkingScreen(r: BufferResult, onBack: () -> Unit) {
    val c = LocalColors.current
    val scale = maxOf(r.availableHours, r.workHours, 0.1)
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText("Buffer working", style = Ts(c.text, 28.sp, FontWeight.SemiBold))
        Column(Modifier.fillMaxWidth().card(efa.CardKind.BUFFER).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Label(r.deadlineLabel?.let { "To $it" } ?: "The tightest deadline sets the number")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                Tile("Available", "${fmt1(r.availableHours)}h", Modifier.weight(1f))
                Operator("−")
                Tile("Work", "${fmt1(r.workHours)}h", Modifier.weight(1f))
                Operator("=")
                Tile(if (r.negative) "NEGATIVE" else "Buffer", r.label().removePrefix("Buffer ").removePrefix("NEGATIVE "), Modifier.weight(1.25f), result = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bar((r.availableHours / scale).toFloat(), color = c.accent, height = 10.dp)
                Bar((r.workHours / scale).toFloat(), color = c.text, height = 10.dp)
            }
        }
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("Work due"); Label("Hours") }
            if (r.lines.isEmpty()) BasicText("No work due.", style = Ts(c.muted, 14.sp))
            val top = r.lines.maxOfOrNull { it.hours }?.coerceAtLeast(0.1) ?: 0.1
            r.lines.forEach { TaskRow(it, top) }
            if (r.lines.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.track))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BasicText("Work total", style = Ts(c.text, 15.sp, FontWeight.SemiBold))
                    BasicText("${fmt1(r.workHours)}h", style = Ts(c.text, 18.sp, FontWeight.Bold, fontFamily = Mono))
                }
            }
        }
        if (r.shortSleep) BasicText("Short night: hard work x1.5, easy work x1.15.", style = Ts(c.muted, 13.sp))
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

/** A figure with its name above it. The result tile is the filled one. */
@Composable
private fun Tile(label: String, value: String, modifier: Modifier, result: Boolean = false) {
    val c = LocalColors.current
    Column(
        modifier.then(if (result) Modifier.background(c.accent, RoundedCornerShape(12.dp)) else Modifier.background(c.bg, RoundedCornerShape(12.dp))).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(label.uppercase(), style = Ts(if (result) c.onAccent else c.muted, 10.sp, fontFamily = Mono, letterSpacing = 0.6.sp), maxLines = 1)
        BasicText(value, style = Ts(if (result) c.onAccent else c.text, 22.sp, FontWeight.Bold, letterSpacing = (-0.5).sp), maxLines = 1)
    }
}

@Composable
private fun Operator(sign: String) {
    BasicText(sign, style = Ts(LocalColors.current.muted, 20.sp, FontWeight.Medium), modifier = Modifier.padding(bottom = 12.dp))
}

/** One task: its name, a heaviness dot with the number, what it costs, and a bar scaled to the biggest task. Read aloud as the whole sentence. */
@Composable
private fun TaskRow(l: WorkingLine, top: Double) {
    val c = LocalColors.current
    val said = "${l.title} (heaviness ${l.heaviness}): ${fmt1(l.rawHours)}h x ${l.factor} = ${fmt1(l.hours)}h"
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = said }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(26.dp).background(c.bg, CircleShape), contentAlignment = Alignment.Center) { BasicText("${l.heaviness}", style = Ts(c.text, 13.sp, FontWeight.Bold, fontFamily = Mono)) }
            Column(Modifier.weight(1f)) {
                BasicText(l.title, style = Ts(c.text, 15.sp, FontWeight.SemiBold))
                BasicText(fmt1(l.rawHours) + "h" + (if (l.factor != 1.0) " x ${l.factor}" else ""), style = Ts(c.muted, 12.sp, fontFamily = Mono))
            }
            BasicText("${fmt1(l.hours)}h", style = Ts(c.text, 16.sp, FontWeight.Bold, fontFamily = Mono))
        }
        Bar((l.hours / top).toFloat(), color = c.text, height = 4.dp)
    }
}
