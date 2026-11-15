package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CardShape = RoundedCornerShape(14.dp)   // AP1

/**
 * The industrial card (AP1): rounded, with a thin light edge along the top inside and a solid darker edge 2 dp below, like a
 * key on a device. Flat colours only, no blur and no gradient.
 */
fun Modifier.card(kind: efa.CardKind? = null): Modifier = composed {
    val c = LocalColors.current
    this
        .drawBehind { drawRoundRect(c.edge, topLeft = Offset(0f, 2.dp.toPx()), size = size, cornerRadius = CornerRadius(14.dp.toPx())) }
        .background(c.tint(kind), CardShape)
        .then(if (c.colourful) Modifier.border(2.dp, Color.Black, CardShape) else Modifier)
        .drawWithContent {
            drawContent()
            clipRect(bottom = 14.dp.toPx()) { drawRoundRect(c.hi, cornerRadius = CornerRadius(14.dp.toPx()), size = Size(size.width, size.height), style = Stroke(1.dp.toPx())) }
        }
}

/** A small mono label in capitals, the way the drawings label every card. */
@Composable
fun Label(text: String) {
    BasicText(text.uppercase(), style = Ts(LocalColors.current.muted, 11.sp, fontFamily = Mono, letterSpacing = 0.9.sp))
}

/** AN1: the motion the app uses. Short, one-shot, never looping; none at all when Android's animations are off. */
object Motion { const val QUICK = 160; const val STANDARD = 240; const val FILL = 400 }

@Composable
fun <T> motion(ms: Int): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    if (LocalMotion.current) androidx.compose.animation.core.tween(ms, easing = androidx.compose.animation.core.FastOutSlowInEasing) else androidx.compose.animation.core.snap()

/** A number that eases to [target] and starts from 0 when first drawn, so a bar or dial fills rather than appearing. The number itself is exact once it has settled. */
@Composable
fun animatedFraction(target: Float, ms: Int = Motion.FILL): Float {
    var goal by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(target) { goal = target }
    return androidx.compose.animation.core.animateFloatAsState(goal, motion(ms), label = "fraction").value
}

/** A flat progress bar: a track with a filled share. Always drawn from a real number (P4), never decorative. It fills when it appears and eases when the number changes (AN1). */
@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 6.dp, color: Color = LocalColors.current.accent) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth().height(height).background(c.track, shape)) {
        val f = animatedFraction(fraction.coerceIn(0f, 1f))
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color, shape))
    }
}

/** One share of a whole, as a fraction of the full circle. */
data class Slice(val fraction: Float, val color: Color)

/**
 * A donut: parts of a whole, or one part of it (a ring). It is the chart for anything that is a share (how much of the work is done, how
 * much of a block has gone, how a day divides), where a bar is for comparing amounts side by side. Slices start at the top and run clockwise
 * and sweep out when it appears (AN1). What is not covered shows as the track. Something that says what it is goes in [center].
 */
@Composable
fun Donut(
    slices: List<Slice>, size: androidx.compose.ui.unit.Dp, thickness: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier,
    track: Color = LocalColors.current.track, gapDegrees: Float = 0f, center: @Composable () -> Unit = {},
) {
    val t = animatedFraction(1f, 700)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val w = thickness.toPx(); val tl = Offset(w / 2, w / 2); val sz = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(w))
            val shown = slices.count { it.fraction > 0f }
            var start = -90f
            for (s in slices) {
                val f = s.fraction.coerceIn(0f, 1f)
                if (f <= 0f) continue
                val gap = if (shown > 1) gapDegrees else 0f
                val sweep = (360f * f - gap).coerceAtLeast(0.5f) * t
                drawArc(s.color, start + gap / 2, sweep, false, tl, sz, style = Stroke(w))
                start += 360f * f
            }
        }
        center()
    }
}

/** A small chevron that turns over when its section opens (AN1). */
@Composable
fun Chevron(open: Boolean, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val turn = androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, motion(Motion.STANDARD), label = "chevron").value
    androidx.compose.foundation.Canvas(modifier.size(18.dp).graphicsLayer { rotationZ = turn }) {
        val w = size.width; val h = size.height; val sw = 2.dp.toPx()
        drawLine(c.muted, Offset(w * 0.2f, h * 0.38f), Offset(w * 0.5f, h * 0.65f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(c.muted, Offset(w * 0.5f, h * 0.65f), Offset(w * 0.8f, h * 0.38f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/** A small rounded tag, for a day's state or a block's kind. */
@Composable
fun Pill(text: String, filled: Boolean = false) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(50)
    Box(Modifier.then(if (filled) Modifier.background(c.accent, shape) else Modifier.border(1.dp, c.track, shape)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        BasicText(text.uppercase(), style = Ts(if (filled) c.onAccent else c.muted, 10.sp, fontFamily = Mono, letterSpacing = 0.8.sp))
    }
}

/** Plain button. Primary uses the accent as a fill (never as text colour, see AP8 test). */
@Composable
fun Btn(text: String, onClick: () -> Unit, primary: Boolean = false, modifier: Modifier = Modifier, description: String = text) {
    val c = LocalColors.current
    val fg = if (primary) c.onAccent else c.text
    val base = if (primary) modifier.heightIn(min = 52.dp).background(c.accent, CardShape) else modifier.heightIn(min = 52.dp).card()
    Box(
        base.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = Ts(fg, 16.sp, FontWeight.Bold)) }
}

/** A selectable chip for choices such as a day, a time or a heaviness step. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, description: String = text) {
    val c = LocalColors.current
    val base = if (selected) Modifier.background(c.accent, CardShape) else Modifier.card()
    Box(
        Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).then(base)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { this.selected = selected; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = Ts(if (selected) c.onAccent else c.text, 15.sp, FontWeight.Medium)) }
}
