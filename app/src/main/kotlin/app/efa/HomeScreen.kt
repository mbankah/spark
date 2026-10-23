package app.efa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.BufferState
import efa.pounds

/** What the home screen shows. Every field is real or says it is missing; nothing is sample data. */
data class HomeState(
    val time: String,
    val buffer: BufferState,
    val now: String?,
    val next: String?,
    val freeToSpendPence: Int?,       // null while money is in its 14-day record-only period (G6)
    val nowOpensBlock: Boolean = false,
    val proofDue: String? = null,     // oldest-first not needed: the newest finished work block still waiting for its photo
)

@Composable
fun HomeScreen(s: HomeState, onBuffer: () -> Unit, onQuickAdd: () -> Unit, onNow: () -> Unit = {}, onProofDue: () -> Unit = {}, onSetup: () -> Unit = {}) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(16.dp).windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(s.time, style = TextStyle(c.text, 40.sp, FontWeight.SemiBold))
        BufferCard(s.buffer, onBuffer)
        InfoCard("NOW", s.now ?: "Nothing scheduled", if (s.nowOpensBlock) onNow else null)
        s.proofDue?.let { InfoCard("PROOF DUE", it, onProofDue) }
        InfoCard("NEXT", s.next ?: "Nothing scheduled")
        InfoCard("FREE TO SPEND", s.freeToSpendPence?.let { pounds(it) } ?: "Recording only. Money starts after 14 days.")
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Btn("Quick add", onQuickAdd, primary = true, modifier = Modifier.weight(2f), description = "Quick add a deadline")
            Btn("Setup", onSetup, modifier = Modifier.weight(1f), description = "Setup: home and lecture venues")
        }
    }
}

@Composable
private fun BufferCard(b: BufferState, onClick: () -> Unit) {
    val c = LocalColors.current
    val (big, small) = when (b) {
        BufferState.NoDeadline -> "No deadline" to "Add a deadline to see the Buffer."
        is BufferState.Value -> b.result.label().removePrefix("Buffer ") to "To ${b.result.deadlineLabel}. Tap for working."
    }
    val fill = if (b is BufferState.Value) 360f * b.result.coverage.toFloat() else 0f
    Row(
        Modifier.fillMaxWidth().background(c.card, CardShape).clickable(enabled = b is BufferState.Value, role = Role.Button, onClick = onClick)
            .padding(16.dp).semantics { contentDescription = "Buffer. $big. $small" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Canvas(Modifier.size(88.dp)) {                      // the ring dial: the one allowed gradient-free ring (AP1)
            val w = 10.dp.toPx()
            drawArc(c.track, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
            drawArc(c.accent, -90f, fill, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
        }
        Column {
            Label("BUFFER")
            BasicText(big, style = TextStyle(c.text, 28.sp, FontWeight.SemiBold))
            BasicText(small, style = TextStyle(c.muted, 14.sp))
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String, onClick: (() -> Unit)? = null) {
    val c = LocalColors.current
    val tap = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Column(Modifier.fillMaxWidth().background(c.card, CardShape).then(tap).padding(16.dp).semantics { contentDescription = "$label. $value" + if (onClick != null) ". Tap to open." else "" }) {
        Label(label)
        BasicText(value, style = TextStyle(c.text, 20.sp))
    }
}
