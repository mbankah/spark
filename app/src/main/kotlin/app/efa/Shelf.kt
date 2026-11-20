package app.efa

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** SH1: whether a shelf starts open. Off in the app, so a page shows a few headings and not everything at once. The tests switch it on so every card is reachable. */
object Shelves { @Volatile var startOpen = false }

/**
 * SH1: a shelf is a group of cards under one heading and a line that says what is inside. It starts folded; a tap opens it and a tap folds it. It is how a page with many things on it
 * (Setup, Right now) stays short: what matters now is shown, and the rest is one tap away under a name.
 */
@Composable
fun Shelf(title: String, summary: String, startOpen: Boolean = Shelves.startOpen, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    var open by remember { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().card().clip(CardShape).clickable(role = Role.Button) { open = !open }.padding(16.dp)
                .semantics { contentDescription = "$title. $summary. ${if (open) "Open. Tap to fold." else "Folded. Tap to open."}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(title, style = Ts(c.text, 18.sp, FontWeight.Bold))
                BasicText(summary, style = Ts(c.muted, 13.sp))
            }
            BasicText(if (open) "Fold" else "Open", style = Ts(c.muted, 14.sp, FontWeight.Bold))
        }
        if (open) content()
    }
}
