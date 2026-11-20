package app.efa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import efa.VrStep

/**
 * VR17: what each step of the cycle is, drawn so it can be told at a glance and needs no font: V a screen with a play triangle, R an open book, N a pencil over a line, P a sheet with a question
 * mark, M a tick and a cross, and a break a cup with steam. A null [step] is a break.
 */
@Composable
fun VrIcon(step: VrStep?, size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension; val w = s * 0.085f
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        val line = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (step) {
            VrStep.V -> {
                drawRoundRect(color, o(0.07f, 0.17f), Size(0.86f * s, 0.6f * s), CornerRadius(0.08f * s), style = line)
                drawPath(Path().apply { moveTo(o(0.42f, 0.32f).x, o(0.42f, 0.32f).y); lineTo(o(0.42f, 0.62f).x, o(0.42f, 0.62f).y); lineTo(o(0.66f, 0.47f).x, o(0.66f, 0.47f).y); close() }, color, style = Fill)
                drawLine(color, o(0.3f, 0.9f), o(0.7f, 0.9f), w, StrokeCap.Round)
            }
            VrStep.R -> {
                val left = Path().apply { moveTo(o(0.5f, 0.28f).x, o(0.5f, 0.28f).y); lineTo(o(0.1f, 0.2f).x, o(0.1f, 0.2f).y); lineTo(o(0.1f, 0.78f).x, o(0.1f, 0.78f).y); lineTo(o(0.5f, 0.86f).x, o(0.5f, 0.86f).y); close() }
                val right = Path().apply { moveTo(o(0.5f, 0.28f).x, o(0.5f, 0.28f).y); lineTo(o(0.9f, 0.2f).x, o(0.9f, 0.2f).y); lineTo(o(0.9f, 0.78f).x, o(0.9f, 0.78f).y); lineTo(o(0.5f, 0.86f).x, o(0.5f, 0.86f).y); close() }
                drawPath(left, color, style = line); drawPath(right, color, style = line)
                drawLine(color, o(0.2f, 0.4f), o(0.4f, 0.44f), w * 0.7f, StrokeCap.Round); drawLine(color, o(0.2f, 0.56f), o(0.4f, 0.6f), w * 0.7f, StrokeCap.Round)
                drawLine(color, o(0.6f, 0.44f), o(0.8f, 0.4f), w * 0.7f, StrokeCap.Round); drawLine(color, o(0.6f, 0.6f), o(0.8f, 0.56f), w * 0.7f, StrokeCap.Round)
            }
            VrStep.N -> {
                drawLine(color, o(0.3f, 0.7f), o(0.78f, 0.22f), w * 2.4f, StrokeCap.Butt)
                drawPath(Path().apply { moveTo(o(0.16f, 0.86f).x, o(0.16f, 0.86f).y); lineTo(o(0.2f, 0.66f).x, o(0.2f, 0.66f).y); lineTo(o(0.36f, 0.82f).x, o(0.36f, 0.82f).y); close() }, color, style = Fill)
                drawLine(color, o(0.46f, 0.9f), o(0.9f, 0.9f), w, StrokeCap.Round)
            }
            VrStep.P -> {
                drawRoundRect(color, o(0.2f, 0.08f), Size(0.6f * s, 0.84f * s), CornerRadius(0.07f * s), style = line)
                drawArc(color, -200f, 250f, false, o(0.38f, 0.22f), Size(0.24f * s, 0.24f * s), style = line)
                drawLine(color, o(0.5f, 0.46f), o(0.5f, 0.56f), w, StrokeCap.Round)
                drawCircle(color, w * 0.62f, o(0.5f, 0.72f))
            }
            VrStep.M -> {
                drawPath(Path().apply { moveTo(o(0.1f, 0.3f).x, o(0.1f, 0.3f).y); lineTo(o(0.22f, 0.42f).x, o(0.22f, 0.42f).y); lineTo(o(0.42f, 0.16f).x, o(0.42f, 0.16f).y) }, color, style = line)
                drawLine(color, o(0.54f, 0.3f), o(0.92f, 0.3f), w, StrokeCap.Round)
                drawLine(color, o(0.12f, 0.6f), o(0.4f, 0.88f), w, StrokeCap.Round); drawLine(color, o(0.4f, 0.6f), o(0.12f, 0.88f), w, StrokeCap.Round)
                drawLine(color, o(0.54f, 0.74f), o(0.92f, 0.74f), w, StrokeCap.Round)
            }
            null -> {
                drawPath(Path().apply { moveTo(o(0.16f, 0.4f).x, o(0.16f, 0.4f).y); lineTo(o(0.24f, 0.84f).x, o(0.24f, 0.84f).y); lineTo(o(0.6f, 0.84f).x, o(0.6f, 0.84f).y); lineTo(o(0.68f, 0.4f).x, o(0.68f, 0.4f).y); close() }, color, style = line)
                drawArc(color, -90f, 180f, false, o(0.6f, 0.46f), Size(0.24f * s, 0.26f * s), style = line)
                drawLine(color, o(0.3f, 0.1f), o(0.3f, 0.26f), w, StrokeCap.Round); drawLine(color, o(0.42f, 0.08f), o(0.42f, 0.24f), w, StrokeCap.Round); drawLine(color, o(0.54f, 0.1f), o(0.54f, 0.26f), w, StrokeCap.Round)
            }
        }
    }
}

/** The step a phase of the cycle is, or null for a break. */
fun vrStepOf(isBreak: Boolean, title: String): VrStep? = if (isBreak) null else VrStep.entries.firstOrNull { it.letter == title.firstOrNull() }
