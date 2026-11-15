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
import androidx.compose.ui.graphics.Color
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
 * available minus work equals the Buffer, and every task is a row of the same numbers the old lines gave. The two rings are shares of a whole:
 * how much of the available time the work takes, and how the work divides between tasks. They are drawn from the figures beside them and carry
 * nothing those do not say.
 */
@Composable
fun WorkingScreen(r: BufferResult, onBack: () -> Unit) {
    val c = LocalColors.current
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
            // The available time as a whole: the work takes its share and what is left over is the Buffer.
            val takes = if (r.availableHours > 0) (r.workHours / r.availableHours).toFloat().coerceIn(0f, 1f) else 1f
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Donut(listOf(Slice(takes, c.text), Slice(1f - takes, c.accent)), 96.dp, 14.dp, track = c.track) {
                    BasicText("${Math.round(takes * 100)}%", style = Ts(c.text, 18.sp, FontWeight.Bold))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Legend(c.text, if (r.negative) "Work takes all of it" else "Work takes ${Math.round(takes * 100)}%")
                    if (!r.negative) Legend(c.accent, "${fmt1(r.hours)}h left over")
                    else BasicText("${fmt1(-r.hours)}h more than there is", style = Ts(c.muted, 13.sp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("Work due"); Label("Hours") }
            if (r.lines.isEmpty()) BasicText("No work due.", style = Ts(c.muted, 14.sp))
            else {
                val total = r.workHours.coerceAtLeast(0.01)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Donut(r.lines.mapIndexed { i, l -> Slice((l.hours / total).toFloat(), sliceColour(i, c.accent)) }, 96.dp, 14.dp, gapDegrees = 3f) {
                        BasicText("${fmt1(r.workHours)}h", style = Ts(c.text, 17.sp, FontWeight.Bold))
                    }
                    BasicText("How the work divides between the tasks.", style = Ts(c.muted, 13.sp))
                }
            }
            r.lines.forEachIndexed { i, l -> TaskRow(l, sliceColour(i, c.accent)) }
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

/** The accent in steps from full to faint, so each task has its own slice of the ring and the same colour on its row. */
internal fun sliceColour(i: Int, accent: Color): Color = accent.copy(alpha = listOf(1f, 0.62f, 0.4f, 0.26f, 0.16f)[i.coerceAtMost(4)])

@Composable
private fun Legend(colour: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(colour, CircleShape))
        BasicText(text, style = Ts(LocalColors.current.text, 14.sp, FontWeight.Medium))
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

/** One task: a swatch in its slice colour, its name, a heaviness dot with the number, and what it costs. Read aloud as the whole sentence. */
@Composable
private fun TaskRow(l: WorkingLine, swatch: Color) {
    val c = LocalColors.current
    val said = "${l.title} (heaviness ${l.heaviness}): ${fmt1(l.rawHours)}h x ${l.factor} = ${fmt1(l.hours)}h"
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = said }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(12.dp).background(swatch, CircleShape))
        Column(Modifier.weight(1f)) {
            BasicText(l.title, style = Ts(c.text, 15.sp, FontWeight.SemiBold))
            BasicText("Heaviness ${l.heaviness}, " + fmt1(l.rawHours) + "h" + (if (l.factor != 1.0) " x ${l.factor}" else ""), style = Ts(c.muted, 12.sp, fontFamily = Mono))
        }
        BasicText("${fmt1(l.hours)}h", style = Ts(c.text, 16.sp, FontWeight.Bold, fontFamily = Mono))
    }
}
