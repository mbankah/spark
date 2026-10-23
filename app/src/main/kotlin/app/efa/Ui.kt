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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CardShape = RoundedCornerShape(14.dp)   // AP1

@Composable
fun Label(text: String) {
    BasicText(text, style = TextStyle(LocalColors.current.muted, 12.sp, fontFamily = FontFamily.Monospace))
}

/** Plain button. Primary uses the accent as a fill (never as text colour, see AP8 test). */
@Composable
fun Btn(text: String, onClick: () -> Unit, primary: Boolean = false, modifier: Modifier = Modifier, description: String = text) {
    val c = LocalColors.current
    val bg = if (primary) c.accent else c.card
    val fg = if (primary) c.onAccent else c.text
    Box(
        modifier.heightIn(min = 48.dp).background(bg, CardShape).border(1.dp, c.track, CardShape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = TextStyle(fg, 16.sp, FontWeight.SemiBold)) }
}

/** A selectable chip for choices such as a day, a time or a heaviness step. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, description: String = text) {
    val c = LocalColors.current
    Box(
        Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            .background(if (selected) c.accent else c.card, CardShape).border(1.dp, c.track, CardShape)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { this.selected = selected; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = TextStyle(if (selected) c.onAccent else c.text, 15.sp, FontWeight.Medium)) }
}
