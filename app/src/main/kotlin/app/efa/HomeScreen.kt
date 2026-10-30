package app.efa

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.BufferState
import efa.fmt1
import efa.pounds

data class NextRow(val time: String, val title: String, val detail: String)

/** What the home screen shows. Every field is real or says it is missing; nothing is sample data. */
data class HomeState(
    val date: String,
    val phone: String?,                 // "Phone open", "Phone closed" or null when blocking is not set up
    val phoneOpen: Boolean,
    val time: String,
    val buffer: BufferState,
    val nowLabel: String?,              // e.g. "Now, until 12:15"
    val nowTitle: String?,
    val nowDetail: String?,
    val nowOpensBlock: Boolean = false,
    val next: List<NextRow> = emptyList(),
    val freeToSpendPence: Int?,         // null while money is in its 14-day record-only period (G6)
    val proofDue: String? = null,       // the newest finished work block still waiting for its photo
    val heldWaiting: Boolean = false,   // held notifications exist: shown without a count (WG7)
    val unavailable: Boolean = false,   // the database could not be read (X7)
    val launcherPaused: Boolean = false, // Spark handed the home screen back after 3 crashes in 10 minutes (LA3)
    val period: String? = null,          // "Assignment week, until Fri 6 Nov" (PW3)
    val moved: List<String> = emptyList(), // what Spark moved for you in the last day and a half, with the reason (RS8)
    val morning: String? = null,         // M6, M8: social is blocked until the ready photo
    val progress: String? = null,        // R4: "14.0 of 60.0 hours done"
    val review: String? = null,          // B5: a finished task waiting to be asked about
    val moneyNote: String? = null,       // G6: when the money starts, while it is only being recorded
    val earnedOn: Boolean = false,       // R2: earned phone access is open right now
)

/** The home screen follows the drawing (design/ColdDark.dc.html): date and phone state, a big clock, the Buffer dial, now, next, money. */
@Composable
fun HomeScreen(
    s: HomeState, onBuffer: () -> Unit, onQuickAdd: () -> Unit,
    onNow: () -> Unit = {}, onProofDue: () -> Unit = {}, onSetup: () -> Unit = {}, onHeld: () -> Unit = {}, onEssential: (String) -> Unit = {}, onSearch: () -> Unit = {}, onRestoreLauncher: () -> Unit = {}, onReady: () -> Unit = {}, onMoney: () -> Unit = {}, onReview: () -> Unit = {},
) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Label(s.date)
                if (s.phone != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(9.dp).background(if (s.phoneOpen) c.accent else c.muted, CircleShape))
                    Label(s.phone + if (s.earnedOn) ", earned" else "")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(s.time, style = Ts(c.text, 88.sp, FontWeight.Bold, letterSpacing = (-3.5).sp, lineHeight = 88.sp))
                SparkLogo(44.dp)
            }
            s.period?.let { Label(it) }
            if (s.launcherPaused) InfoCard("LAUNCHER PAUSED", "Put Spark back as a home choice", onRestoreLauncher, "Spark crashed 3 times in 10 minutes, so the phone's own home screen took over. Blocking kept running.")
            s.morning?.let { InfoCard("MORNING", "Ready photo needed", onReady, it) }
            BufferCard(s.buffer, onBuffer)
            if (s.moved.isNotEmpty()) InfoCard("MOVED FOR YOU", s.moved.first(), null, (s.moved.drop(1).take(2) + listOfNotNull(s.moved.size.minus(3).takeIf { it > 0 }?.let { "And $it more." })).joinToString(" ").ifBlank { null })
            if (s.unavailable) InfoCard("SCHEDULE", "Schedule unavailable", null, "The database could not be read. Nothing is shown rather than something wrong.")
            else if (s.nowTitle == null) InfoCard("NOW", "Nothing scheduled", null, null)
            else InfoCard((s.nowLabel ?: "Now").uppercase(), s.nowTitle, if (s.nowOpensBlock) onNow else null, s.nowDetail?.takeIf { it.isNotBlank() })
            s.proofDue?.let { InfoCard("PROOF DUE", it, onProofDue, null) }
            s.review?.let { InfoCard("HELP THE ESTIMATES", it, onReview, "Takes one tap.") }
            if (s.heldWaiting) InfoCard("HELD NOTIFICATIONS", "Waiting for you", onHeld, "Held while your apps were blocked. Open the digest.")
            NextCard(s.next)
            Row(Modifier.fillMaxWidth().card().clip(CardShape).clickable(role = Role.Button, onClick = onMoney).padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { contentDescription = "Free to spend this week. " + (s.freeToSpendPence?.let { pounds(it) } ?: "Recording") + ". Tap for the working." },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Label("Free to spend this week")
                BasicText(s.freeToSpendPence?.let { pounds(it) } ?: "Recording", style = Ts(c.text, 24.sp, FontWeight.Bold, letterSpacing = (-0.5).sp))
            }
            s.progress?.let { BasicText(it, style = Ts(c.muted, 13.sp, fontFamily = Mono)) }
            if (s.freeToSpendPence == null) BasicText(s.moneyNote ?: "Money starts after the first 14 days. Until then it is only recorded.", style = Ts(c.muted, 12.sp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Phone" to "phone", "Msgs" to "messages", "Maps" to "maps").forEach { (label, key) ->
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp).card().clip(CardShape).clickable(role = Role.Button) { onEssential(key) }.semantics { contentDescription = "$label. Opens your $label app." },
                        contentAlignment = Alignment.Center,
                    ) { BasicText(label, style = Ts(c.text, 14.sp, FontWeight.Medium)) }
                }
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).card().clip(CardShape).clickable(role = Role.Button, onClick = onSetup).semantics { contentDescription = "Setup: home, venues, blocking and GitHub" },
                    contentAlignment = Alignment.Center,
                ) { BasicText("Setup", style = Ts(c.muted, 14.sp, FontWeight.Medium)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // U6, LA5: apps are reached by typing, not by browsing a grid.
            Box(Modifier.weight(1f).heightIn(min = 54.dp).card().clip(CardShape).clickable(role = Role.Button, onClick = onSearch).padding(horizontal = 16.dp).semantics { contentDescription = "Type to open an app" }, contentAlignment = Alignment.CenterStart) {
                BasicText("Type to open", style = Ts(c.muted, 16.sp))
            }
            Box(
                Modifier.size(54.dp).background(c.accent, CardShape).clickable(role = Role.Button, onClick = onQuickAdd).semantics { contentDescription = "Quick add" },
                contentAlignment = Alignment.Center,
            ) { BasicText("+", style = Ts(c.onAccent, 30.sp, FontWeight.Bold)) }
        }
    }
}

/** The Buffer as a dial (AP1). Its fill is the share of due work that fits, so it is never decorative (P4, Q-G12). A negative Buffer always says NEGATIVE (AP6). */
@Composable
private fun BufferCard(b: BufferState, onClick: () -> Unit) {
    val c = LocalColors.current
    val big: String; val small: String; val lines: List<String>
    var fill = 0f
    when (b) {
        BufferState.NoDeadline -> { big = "None"; small = "Buffer"; lines = listOf("No deadline.", "Add one to see the Buffer.") }
        is BufferState.Value -> {
            val r = b.result
            big = r.label().removePrefix("Buffer ").removePrefix("NEGATIVE ")
            small = if (r.negative) "NEGATIVE" else "Buffer"
            lines = listOf("Available ${fmt1(r.availableHours)}h", "Work ${fmt1(r.workHours)}h", "To ${r.deadlineLabel}")
            fill = 360f * r.coverage.toFloat()
        }
    }
    Row(
        Modifier.fillMaxWidth().card().clip(CardShape).clickable(enabled = b is BufferState.Value, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "Buffer. $big. $small. ${lines.joinToString(". ")}." + if (b is BufferState.Value) " Tap for the working." else "" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Dial(124.dp, fill, big, small)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            lines.forEach { BasicText(it, style = Ts(c.text, 13.sp, fontFamily = Mono, lineHeight = 22.sp)) }
            if (b is BufferState.Value) BasicText("Tap dial for the sum", style = Ts(c.accentText, 13.sp, fontFamily = Mono, lineHeight = 22.sp))
        }
    }
}

@Composable
private fun Dial(size: Dp, fillDegrees: Float, big: String, small: String) {
    val c = LocalColors.current
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 16.dp.toPx(); val tl = Offset(w / 2, w / 2); val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(c.track, 0f, 360f, false, tl, sz, style = Stroke(w))
            if (fillDegrees > 0f) drawArc(c.accent, -90f, fillDegrees, false, tl, sz, style = Stroke(w, cap = StrokeCap.Butt))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(big, style = Ts(c.text, 26.sp, FontWeight.Bold, letterSpacing = (-0.8).sp))
            Label(small)
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String, onClick: (() -> Unit)?, detail: String?) {
    val c = LocalColors.current
    val tap = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Column(
        Modifier.fillMaxWidth().card().then(tap).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "$label. $value." + (detail?.let { " $it" } ?: "") + if (onClick != null) " Tap to open." else "" },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Label(label)
        BasicText(value, style = Ts(c.text, 21.sp, FontWeight.Bold))
        detail?.let { BasicText(it, style = Ts(c.muted, 13.sp)) }
    }
}

@Composable
private fun NextCard(rows: List<NextRow>) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label("Next")
        if (rows.isEmpty()) BasicText("Nothing scheduled", style = Ts(c.text, 15.sp, FontWeight.Medium))
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 4.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                BasicText(r.time, style = Ts(c.accentText, 13.sp, fontFamily = Mono), modifier = Modifier.width(44.dp))
                Column {
                    BasicText(r.title, style = Ts(c.text, 15.sp, FontWeight.Medium))
                    if (r.detail.isNotBlank()) BasicText(r.detail, style = Ts(c.muted, 12.5.sp))
                }
            }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.track))
        }
    }
}
