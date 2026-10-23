package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
fun Modifier.card(): Modifier = composed {
    val c = LocalColors.current
    this
        .drawBehind { drawRoundRect(c.edge, topLeft = Offset(0f, 2.dp.toPx()), size = size, cornerRadius = CornerRadius(14.dp.toPx())) }
        .background(c.card, CardShape)
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
