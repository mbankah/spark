package app.efa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.StripBlock
import efa.StripState

/** What the strip's window is: the hours around now that are worth drawing, so a block is wide enough to read. */
internal fun stripWindow(blocks: List<StripBlock>, nowMin: Int): Pair<Int, Int> {
    val from = (nowMin - 180).coerceAtLeast(blocks.minOfOrNull { it.plannedStart } ?: 0).coerceAtLeast(0)
    val to = (nowMin + 300).coerceAtMost(blocks.maxOfOrNull { it.plannedEnd } ?: 1440).coerceAtMost(1800)
    return if (to - from < 240) from to (from + 240) else from to to
}

/** One line a screen reader can say for the whole strip: how many blocks, and how many are late, over, skipped or not started. */
fun stripSummary(blocks: List<StripBlock>): String {
    val run = blocks.filter { it.runnable }
    if (run.isEmpty()) return "Nothing to start today."
    fun n(s: StripState) = run.count { it.state == s }
    val parts = listOfNotNull(
        n(StripState.ON_TIME).takeIf { it > 0 }?.let { "$it on time" }, n(StripState.SHIFTED).takeIf { it > 0 }?.let { "$it shifted" },
        n(StripState.RUNNING).takeIf { it > 0 }?.let { "$it running" }, n(StripState.NOT_STARTED).takeIf { it > 0 }?.let { "$it not started" },
        n(StripState.SKIPPED).takeIf { it > 0 }?.let { "$it did not happen" },
    )
    return if (parts.isEmpty()) "${run.size} to start later." else parts.joinToString(", ") + "."
}

/**
 * ST3: the day as a strip. The top row is the plan, the bottom row is what happened. A block started late sits to the right of its plan, one that ran over has a longer tail,
 * one not started is a dashed outline, one that did not happen is a thin line, and a line marks now.
 */
@Composable
fun DayStrip(blocks: List<StripBlock>, nowMin: Int) {
    val c = LocalColors.current
    if (blocks.none { it.runnable }) return
    val (from, to) = stripWindow(blocks, nowMin)
    val span = (to - from).toFloat().coerceAtLeast(1f)
    val summary = stripSummary(blocks)
    Column(Modifier.fillMaxWidth().card().padding(14.dp).semantics { contentDescription = "Today so far. $summary" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("TODAY, PLANNED AND DONE")
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val w = size.width; val rowH = 18.dp.toPx(); val gap = 6.dp.toPx(); val round = CornerRadius(3.dp.toPx())
            fun x(m: Int) = ((m - from) / span).coerceIn(0f, 1f) * w
            val planTop = 2.dp.toPx(); val doneTop = planTop + rowH + gap + 6.dp.toPx()
            blocks.forEach { b ->
                val px = x(b.plannedStart); val pw = (x(b.plannedEnd) - px).coerceAtLeast(2.dp.toPx())
                drawRoundRect(c.track, Offset(px, planTop), Size(pw, rowH), round)
                if (!b.runnable) return@forEach
                when (b.state) {
                    StripState.ON_TIME, StripState.SHIFTED -> {
                        val ax = x(b.actualStart ?: b.plannedStart); val ae = x(b.actualEnd ?: b.plannedEnd)
                        drawRoundRect(c.accent.copy(alpha = if (b.state == StripState.ON_TIME) 1f else 0.55f), Offset(ax, doneTop), Size((ae - ax).coerceAtLeast(2.dp.toPx()), rowH), round)
                    }
                    StripState.RUNNING -> {
                        val ax = x(b.actualStart ?: b.plannedStart); val ae = maxOf(x(nowMin), ax + 2.dp.toPx())
                        drawRoundRect(c.accent.copy(alpha = 0.7f), Offset(ax, doneTop), Size(ae - ax, rowH), round)
                    }
                    StripState.NOT_STARTED -> drawRoundRect(c.muted, Offset(px, doneTop), Size(pw, rowH), round, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                    StripState.SKIPPED -> drawLine(c.muted, Offset(px, doneTop + rowH / 2), Offset(px + pw, doneTop + rowH / 2), 2.dp.toPx())
                    StripState.PLANNED -> {}
                }
            }
            val nx = x(nowMin); drawLine(c.text, Offset(nx, 0f), Offset(nx, size.height), 1.5.dp.toPx())
        }
        BasicText(summary, style = Ts(c.muted, 13.sp))
    }
}
