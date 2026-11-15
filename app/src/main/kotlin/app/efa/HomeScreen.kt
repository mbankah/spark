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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.launch
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
    val widgets: Set<efa.WidgetKind> = efa.WidgetKind.entries.filter { it.byDefault }.toSet(),   // WG3: the widgets in use; a removed one is not drawn
    val newRoom: String? = null,        // L6: a venue Spark cannot place, usually a room that changed
    val voiceNote: String? = null,      // V2: what voice could not do, said plainly
    val voice: Boolean = false,         // V1, G7: voice is offered, and hidden in lectures and the library when the setting says so
    val week: List<String> = emptyList(),   // the week glance widget
    val shoppingDay: Boolean = false,   // SH1: the shopping list is one tap away on shopping day
    val review: String? = null,          // B5: a finished task waiting to be asked about
    val moneyNote: String? = null,       // G6: when the money starts, while it is only being recorded
    val earnedOn: Boolean = false,       // R2: earned phone access is open right now
    val schedule: List<efa.ScheduleDay> = emptyList(),   // SC1: the second page, the whole calendar day by day
    val nowFraction: Float? = null,      // how far through the running block it is, drawn as a bar
    val progressFraction: Float? = null, // the week's confirmed share of planned work, drawn as a bar
)

/**
 * The home screen follows the drawing (design/ColdDark.dc.html): date and phone state, a big clock, the Buffer dial, now, next, money.
 * A second page, one swipe to the left, is the whole schedule (SC1). The search bar and the add buttons stay on both pages.
 */
@Composable
fun HomeScreen(
    s: HomeState, onBuffer: () -> Unit, onQuickAdd: () -> Unit,
    onNow: () -> Unit = {}, onProofDue: () -> Unit = {}, onSetup: () -> Unit = {}, onHeld: () -> Unit = {}, onEssential: (String) -> Unit = {}, onSearch: () -> Unit = {}, onRestoreLauncher: () -> Unit = {}, onReady: () -> Unit = {}, onMoney: () -> Unit = {}, onReview: () -> Unit = {}, onShopping: () -> Unit = {}, onVoice: () -> Unit = {}, onNewRoom: () -> Unit = {},
    homeTick: Int = 0,
) {
    val c = LocalColors.current
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    BackHandler(enabled = pager.currentPage != 0) { scope.launch { pager.scrollToPage(0) } }       // back from the schedule goes to the first page
    LaunchedEffect(homeTick) { if (homeTick > 0) pager.scrollToPage(0) }                              // the home button always lands on the first page
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), pageSpacing = 20.dp, key = { it }) { page ->
            if (page == 0) HomePage(s, onBuffer, onNow, onProofDue, onSetup, onHeld, onEssential, onRestoreLauncher, onReady, onMoney, onReview, onShopping, onNewRoom)
            else ScheduleScreen(s.schedule)
        }
        PageDots(pager.currentPage) { page -> scope.launch { pager.scrollToPage(page) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // U6, LA5: apps are reached by typing, not by browsing a grid.
            Box(Modifier.weight(1f).heightIn(min = 54.dp).card().clip(CardShape).clickable(role = Role.Button, onClick = onSearch).padding(horizontal = 16.dp).semantics { contentDescription = "Type to open an app" }, contentAlignment = Alignment.CenterStart) {
                BasicText("Type to open", style = Ts(c.muted, 16.sp))
            }
            if (s.voice) Box(
                Modifier.size(54.dp).card().clip(CardShape).clickable(role = Role.Button, onClick = onVoice).semantics { contentDescription = "Voice quick add" },
                contentAlignment = Alignment.Center,
            ) { BasicText("Say", style = Ts(c.text, 14.sp, FontWeight.Bold)) }
            Box(
                Modifier.size(54.dp).background(c.accent, CardShape).clickable(role = Role.Button, onClick = onQuickAdd).semantics { contentDescription = "Quick add" },
                contentAlignment = Alignment.Center,
            ) { BasicText("+", style = Ts(c.onAccent, 30.sp, FontWeight.Bold)) }
        }
    }
}

/** Two dots, the current page filled. Each is a button of its own, so the other page is one tap away as well as one swipe. */
@Composable
private fun PageDots(page: Int, onPage: (Int) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        listOf("Home" to 0, "Schedule" to 1).forEach { (name, i) ->
            Box(
                Modifier.heightIn(min = 32.dp).widthIn(min = 48.dp).clickable(role = Role.Tab) { onPage(i) }.semantics { contentDescription = "$name page" + if (page == i) ", showing" else "" },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(width = if (page == i) 22.dp else 8.dp, height = 8.dp).background(if (page == i) c.accent else c.track, CircleShape)) }
        }
    }
}

@Composable
private fun HomePage(
    s: HomeState, onBuffer: () -> Unit, onNow: () -> Unit, onProofDue: () -> Unit, onSetup: () -> Unit, onHeld: () -> Unit, onEssential: (String) -> Unit,
    onRestoreLauncher: () -> Unit, onReady: () -> Unit, onMoney: () -> Unit, onReview: () -> Unit, onShopping: () -> Unit, onNewRoom: () -> Unit,
) {
    val c = LocalColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        if (s.launcherPaused) InfoCard("LAUNCHER PAUSED", "Put Spark back as a home choice", onRestoreLauncher, "Spark crashed 3 times in 10 minutes. Blocking kept running.")
        s.morning?.let { InfoCard("MORNING", "Ready photo needed", onReady, it) }
        BufferCard(s.buffer, onBuffer)
        if (s.moved.isNotEmpty()) InfoCard("MOVED FOR YOU", s.moved.first(), null, (s.moved.drop(1).take(2) + listOfNotNull(s.moved.size.minus(3).takeIf { it > 0 }?.let { "And $it more." })).joinToString(" ").ifBlank { null })
        if (s.unavailable) InfoCard("SCHEDULE", "Schedule unavailable", null, "The database could not be read.")
        else if (efa.WidgetKind.NOW_NEXT !in s.widgets) {}
        else if (s.nowTitle == null) InfoCard("NOW", "Nothing scheduled", null, null, efa.CardKind.NOW)
        else InfoCard((s.nowLabel ?: "Now").uppercase(), s.nowTitle, if (s.nowOpensBlock) onNow else null, s.nowDetail?.takeIf { it.isNotBlank() }, efa.CardKind.NOW, s.nowFraction)
        s.proofDue?.let { InfoCard("PROOF DUE", it, onProofDue, null) }
        if (s.shoppingDay || efa.WidgetKind.SHOPPING in s.widgets) InfoCard("SHOPPING", "Shopping list", onShopping, null)
        s.newRoom?.let { InfoCard("WHERE IS IT?", "Where is $it?", onNewRoom, "Put it on the map so attendance can be checked.") }
        s.voiceNote?.let { BasicText(it, style = Ts(c.text, 14.sp)) }
        s.review?.let { InfoCard("HELP THE ESTIMATES", it, onReview, null) }
        if (s.heldWaiting) InfoCard("HELD NOTIFICATIONS", "Waiting for you", onHeld, "Open the digest.")
        if (efa.WidgetKind.DAY_PLAN in s.widgets) NextCard(s.next)
        if (efa.WidgetKind.WEEK_GLANCE in s.widgets && s.week.isNotEmpty()) InfoCard("WEEK", s.week.first(), null, s.week.drop(1).joinToString("\n"))
        MoneyCard(s, onMoney)
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
}

/** Money: the amount, and under it a bar of how much of the week's planned work is confirmed. Both come from the same week (R4, R7). */
@Composable
private fun MoneyCard(s: HomeState, onMoney: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxWidth().card(efa.CardKind.SPEND).clip(CardShape).clickable(role = Role.Button, onClick = onMoney).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "Free to spend this week. " + (s.freeToSpendPence?.let { pounds(it) } ?: "Recording") + ". Tap for the working." },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label("Free to spend this week")
            BasicText(s.freeToSpendPence?.let { pounds(it) } ?: "Recording", style = Ts(c.text, 26.sp, FontWeight.Bold, letterSpacing = (-0.5).sp))
        }
        s.progressFraction?.let { Bar(it) }
        s.progress?.let { BasicText(it, style = Ts(c.muted, 12.sp, fontFamily = Mono)) }
        if (s.freeToSpendPence == null) BasicText(s.moneyNote ?: "Recorded for the first 14 days.", style = Ts(c.muted, 12.sp))
    }
}

/** The Buffer as a dial (AP1). Its fill is the share of due work that fits, so it is never decorative (P4, Q-G12). A negative Buffer always says NEGATIVE (AP6). */
@Composable
private fun BufferCard(b: BufferState, onClick: () -> Unit) {
    val c = LocalColors.current
    val big: String; val small: String; val lines: List<Pair<String, String>>
    var fill = 0f
    when (b) {
        BufferState.NoDeadline -> { big = "None"; small = "Buffer"; lines = emptyList() }
        is BufferState.Value -> {
            val r = b.result
            big = r.label().removePrefix("Buffer ").removePrefix("NEGATIVE ")
            small = if (r.negative) "NEGATIVE" else "Buffer"
            lines = listOfNotNull("Available" to "${fmt1(r.availableHours)}h", "Work" to "${fmt1(r.workHours)}h", r.deadlineLabel?.takeIf { it.isNotBlank() }?.let { "To" to it })
            fill = 360f * r.coverage.toFloat()
        }
    }
    val spoken = if (b is BufferState.NoDeadline) "No deadline. Add one to see the Buffer." else lines.joinToString(". ") { "${it.first} ${it.second}" }
    Row(
        Modifier.fillMaxWidth().card(efa.CardKind.BUFFER).clip(CardShape).clickable(enabled = b is BufferState.Value, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "Buffer. $big. $small. $spoken." + if (b is BufferState.Value) " Tap for the working." else "" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Dial(124.dp, fill, big, small)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (b is BufferState.NoDeadline) {
                BasicText("No deadline.", style = Ts(c.text, 15.sp, FontWeight.SemiBold))
                BasicText("Add one to see the Buffer.", style = Ts(c.muted, 13.sp))
            }
            lines.forEach { (k, v) ->
                BasicText(
                    buildAnnotatedString {
                        pushStyle(SpanStyle(color = c.muted)); append("$k "); pop()
                        pushStyle(SpanStyle(color = c.text, fontWeight = FontWeight.Bold)); append(v); pop()
                    },
                    style = Ts(c.text, 15.sp),
                )
            }
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
private fun InfoCard(label: String, value: String, onClick: (() -> Unit)?, detail: String?, kind: efa.CardKind? = null, fraction: Float? = null) {
    val c = LocalColors.current
    val tap = if (onClick != null) Modifier.clip(CardShape).clickable(role = Role.Button, onClick = onClick) else Modifier
    Column(
        Modifier.fillMaxWidth().card(kind).then(tap).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "$label. $value." + (detail?.let { " $it" } ?: "") + if (onClick != null) " Tap to open." else "" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Label(label)
        BasicText(value, style = Ts(c.text, 21.sp, FontWeight.Bold))
        fraction?.let { Bar(it, Modifier.padding(vertical = 2.dp)) }
        detail?.let { BasicText(it, style = Ts(c.muted, 13.sp)) }
    }
}

@Composable
private fun NextCard(rows: List<NextRow>) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card(efa.CardKind.NEXT).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label("Next")
        if (rows.isEmpty()) BasicText("Nothing scheduled", style = Ts(c.text, 15.sp, FontWeight.Medium))
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 4.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                BasicText(r.time, style = Ts(c.accentText, 14.sp, FontWeight.Medium, fontFamily = Mono), modifier = Modifier.width(48.dp))
                Column {
                    BasicText(r.title, style = Ts(c.text, 15.sp, FontWeight.Medium))
                    if (r.detail.isNotBlank()) BasicText(r.detail, style = Ts(c.muted, 12.5.sp))
                }
            }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.track))
        }
    }
}
