package app.efa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.*
import kotlinx.coroutines.delay

/** A VRNPM cycle that is running: its study block and the cycle as it stands. */
data class VrActive(val ds: DatedSlot, val run: VrRun)

/** What the cycle card asks for. */
enum class VrAction { PAUSE, RESUME, NEXT, PAGE, OPEN }

/** The cycle symbol: a circular arrow, drawn so it needs no font. */
@Composable
fun CycleSymbol(size: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension * 0.11f; val r = this.size.minDimension / 2 - w * 1.6f; val ctr = Offset(this.size.width / 2, this.size.height / 2)
        drawArc(color, startAngle = -50f, sweepAngle = 285f, useCenter = false, topLeft = Offset(ctr.x - r, ctr.y - r), size = Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
        val a = Math.toRadians(235.0); val tip = Offset(ctr.x + r * Math.cos(a).toFloat(), ctr.y + r * Math.sin(a).toFloat())
        val tx = -Math.sin(a).toFloat(); val ty = Math.cos(a).toFloat()            // the way the arc is travelling there
        val nx = Math.cos(a).toFloat(); val ny = Math.sin(a).toFloat()
        val head = w * 2.6f
        val path = Path().apply {
            moveTo(tip.x + tx * head, tip.y + ty * head)
            lineTo(tip.x - tx * head * 0.2f + nx * head * 0.9f, tip.y - ty * head * 0.2f + ny * head * 0.9f)
            lineTo(tip.x - tx * head * 0.2f - nx * head * 0.9f, tip.y - ty * head * 0.2f - ny * head * 0.9f)
            close()
        }
        drawPath(path, color, style = Fill)
    }
}

/** VR18: the five steps as chips, each with its icon and letter and the state it is in. The same on the home card and the block screen. */
@Composable
fun VrChips(v: VrView) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        v.chips.forEach { ch ->
            val step = VrStep.entries.first { it.letter == ch.letter }
            val (fill, ink) = when (ch.state) {
                VrChipState.UPCOMING -> c.track to c.muted
                VrChipState.CURRENT -> c.accent to c.bg
                VrChipState.OVER_NOW -> c.text to c.bg
                VrChipState.DONE, VrChipState.DONE_OVER -> c.accent.copy(alpha = 0.45f) to c.text
                VrChipState.OVERRIDDEN -> c.track to c.text
            }
            val mark = when (ch.state) { VrChipState.OVER_NOW, VrChipState.DONE_OVER -> "+"; VrChipState.OVERRIDDEN -> "*"; else -> "" }
            Row(
                Modifier.weight(1f).heightIn(min = 40.dp).background(fill, CardShape).semantics { contentDescription = "${step.label}, ${ch.state.name.lowercase().replace('_', ' ')}" },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                VrIcon(step, 16.dp, ink)
                Spacer(Modifier.width(4.dp))
                BasicText(ch.letter.toString() + mark, style = Ts(ink, 16.sp, FontWeight.Bold))
            }
        }
    }
}

/**
 * VR18: a running cycle, drawn once and used on the home card and on the block screen. The ring is the whole cycle, each phase as wide as its time; the icon of the step in hand and the time are in the
 * middle; under it what to do now, the five chips, Pause or Resume, a page done while on notes, and the hold that ends the step. A step past its time shows OVER and its arc is in the strongest contrast.
 * [onRing] is what a tap on the ring does (the home card opens the study block). [pagesNote] is the line about timed pages while on notes.
 */
@Composable
fun VrCycleBody(run: VrRun, nowMs: Long, onAction: (VrAction) -> Unit, onRing: (() -> Unit)? = null, pagesNote: String? = null) {
    val c = LocalColors.current
    val v = vrView(run, nowMs); val ring = vrRing(run, nowMs)
    val time = if (v.over) "+" + clockMs(v.overMs) else clockMs(v.remainingMs)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f).then(if (onRing != null) Modifier.clip(CardShape).clickable(role = Role.Button, onClick = onRing) else Modifier)
                .semantics { contentDescription = "VRNPM. ${v.title}. " + (if (v.paused) "Paused. " else "") + (if (v.over) "Over by ${clockMs(v.overMs)}." else "${clockMs(v.remainingMs)} left.") + if (onRing != null) " Tap to open the study block." else "" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                val gap = 3f
                val stepW = 20.dp.toPx(); val breakW = 9.dp.toPx(); val inset = stepW / 2
                val tl = Offset(inset, inset); val sz = Size(this.size.width - stepW, this.size.height - stepW)
                ring.forEach { a ->
                    val w = if (a.isBreak) breakW else stepW
                    val start = -90f + a.start * 360f + gap / 2; val sweep = (a.sweep * 360f - gap).coerceAtLeast(1f)
                    drawArc(c.track, start, sweep, false, tl, sz, style = Stroke(w))
                    if (a.fill > 0f) drawArc(if (a.over) c.text else if (a.isBreak) c.muted else c.accent, start, sweep * a.fill, false, tl, sz, style = Stroke(w))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                VrIcon(vrStepOf(v.isBreak, v.title), 44.dp, if (v.over) c.text else c.accent)
                BasicText(time, style = Ts(c.text, 46.sp, FontWeight.Bold, fontFamily = Mono, letterSpacing = (-1).sp, lineHeight = 50.sp))
                BasicText(v.title, style = Ts(c.text, 18.sp, FontWeight.SemiBold))
                if (v.paused) BasicText("PAUSED", style = Ts(c.muted, 13.sp, FontWeight.Bold))
                else if (v.over) BasicText("OVER", style = Ts(c.text, 13.sp, FontWeight.Bold))
            }
        }
        BasicText(v.todo, style = Ts(c.muted, 15.sp), modifier = Modifier.fillMaxWidth())            // VR13: what to do now
        pagesNote?.let { BasicText(it, style = Ts(c.muted, 14.sp), modifier = Modifier.fillMaxWidth()) }
        if (v.pausedMin > 0 || v.paused) BasicText(if (v.paused) "The clock is stopped. Paused ${v.pausedMin} min so far." else "Paused ${v.pausedMin} min so far.", style = Ts(c.muted, 14.sp), modifier = Modifier.fillMaxWidth())
        VrChips(v)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(if (v.paused) "Resume" else "Pause", { onAction(if (v.paused) VrAction.RESUME else VrAction.PAUSE) }, modifier = Modifier.weight(1f),
                description = if (v.paused) "Resume the timer" else "Pause the timer. The time paused is not counted in the step")
            if (v.title.startsWith("N ")) Btn("Page done", { onAction(VrAction.PAGE) }, modifier = Modifier.weight(1f))
        }
        val last = v.title.startsWith("M ")
        HoldButton(if (v.isBreak) "Hold to start the next step" else if (last) "Hold to finish the cycle" else "Hold to finish ${v.title.first()}", HOLD_END_MS) { onAction(VrAction.NEXT) }
    }
}

/**
 * VR11, VR16: a running cycle, big, at the top of the home screen, and linked to its study block: the block's name and hours are at the top with where the cycle stands against it (the block ends in
 * 40 min, or ended at 12:30 and the cycle runs on). When the block is the one that is on now this card is the Now card for it, with the two changes that can still happen mid-cycle under it.
 * It ticks every second by itself.
 */
@Composable
fun VrHero(active: VrActive, onReality: (String) -> Unit = {}, onAction: (VrAction) -> Unit) {
    val c = LocalColors.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (true) { nowMs = System.currentTimeMillis(); delay(1000) } }
    val ds = active.ds
    val link = vrBlockLink(ds.start.toInstant().toEpochMilli(), ds.end.toInstant().toEpochMilli(), nowMs, ds.start.zone)
    val blockName = ds.slot.title.substringBefore(" - ")
    Column(Modifier.fillMaxWidth().card(CardKind.NOW).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(CardShape).clickable(role = Role.Button) { onAction(VrAction.OPEN) }
                .semantics { contentDescription = "Study block. $blockName. ${link.window}. ${link.status}. Tap to open." },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Label("VRNPM")
                BasicText(link.window, style = Ts(c.muted, 13.sp, fontFamily = Mono))
            }
            BasicText(blockName, style = Ts(c.text, 20.sp, FontWeight.Bold), maxLines = 1)
            BasicText(link.status, style = Ts(if (link.ended) c.text else c.muted, 13.sp, if (link.ended) FontWeight.Bold else FontWeight.Normal))
        }
        VrCycleBody(active.run, nowMs, onAction, onRing = { onAction(VrAction.OPEN) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Something came up", false, { onReality("cameup") }, "Something came up: say what happened and Spark finds a place for it")
            Chip("Change the plan", false, { onReality("door") }, "Change the plan")
        }
    }
}
