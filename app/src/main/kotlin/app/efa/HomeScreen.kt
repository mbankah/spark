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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import efa.blockKey
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
    val nowKey: String? = null,         // VR16: the key of the block that is on now, so a cycle running in it is the Now card
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
    val progressFraction: Float? = null, // the week's confirmed share of planned work, drawn as a ring
    val photo: efa.PhotoDue? = null,     // PH1: the photo to take now, or the next one, shown first
    val photoLeft: Int? = null,          // minutes left to take it in full
    val nowLeftMin: Int? = null,         // minutes left in the running block, shown inside its ring
    val meal: String? = null,            // MC1: the meal being asked about, with the two answers on the home screen
    val mealKey: String = "",
    val evening: Boolean = false,        // HM1: from 21:00 the evening offers "Bed later tonight"
    val lite: Boolean = false,          // LT1: the home shows now, next and the phone, nothing else
    val mode: efa.HomeMode = efa.HomeMode.FREE,   // HS1, HS2: the home follows the moment; a running block, the wind-down and the night show only what matters
    val vr: VrActive? = null,            // VR11: a VRNPM cycle that is running, drawn big at the top
    val adapt: AdaptView = AdaptView(),           // AD: a drift to answer, the step the day is on, the three things that matter
    val confirms: List<efa.Confirm> = emptyList(),   // CF1: questions waiting for an answer, from whatever raised them
)

/**
 * The home screen follows the drawing (design/ColdDark.dc.html): date and phone state, a big clock, the Buffer dial, now, next, money.
 * A second page, one swipe to the left, is the whole schedule (SC1). The search bar and the add buttons stay on both pages.
 */
@Composable
fun HomeScreen(
    s: HomeState, onBuffer: () -> Unit, onQuickAdd: () -> Unit,
    onNow: () -> Unit = {}, onProofDue: () -> Unit = {}, onSetup: () -> Unit = {}, onHeld: () -> Unit = {}, onEssential: (String) -> Unit = {}, onSearch: () -> Unit = {}, onRestoreLauncher: () -> Unit = {}, onReady: () -> Unit = {}, onMoney: () -> Unit = {}, onReview: () -> Unit = {}, onShopping: () -> Unit = {}, onVoice: () -> Unit = {}, onNewRoom: () -> Unit = {},
    homeTick: Int = 0, onPhoto: () -> Unit = {}, onMeal: (String, Boolean) -> Unit = { _, _ -> }, onReality: (String) -> Unit = {},
    third: (@Composable () -> Unit)? = null,         // HS4: the third page, Work it out
    onEmergency: (efa.EmergencyAction) -> Unit = {}, onAdapt: (String) -> Unit = {}, onVr: (VrAction) -> Unit = {}, onConfirm: (String, String) -> Unit = { _, _ -> },
) {
    val c = LocalColors.current
    val pager = rememberPagerState(pageCount = { if (third != null) 3 else 2 })
    val scope = rememberCoroutineScope()
    BackHandler(enabled = pager.currentPage != 0) { scope.launch { pager.animateScrollToPage(0) } }       // back from the schedule goes to the first page
    LaunchedEffect(homeTick) { if (homeTick > 0) pager.animateScrollToPage(0) }
    val waiting = RoutineLaunch.pending
    LaunchedEffect(waiting) { if (waiting != null) { if (third != null) pager.scrollToPage(2) else RoutineLaunch.pending = null } }          // ST1: a Start button on a block lands on the runner                              // the home button always lands on the first page
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), pageSpacing = 20.dp, key = { it }) { page ->
            if (page == 0) HomePage(s, onBuffer, onNow, onProofDue, onSetup, onHeld, onEssential, onRestoreLauncher, onReady, onMoney, onReview, onShopping, onNewRoom, onPhoto, onMeal, onReality, onEmergency, onAdapt, onVr, onConfirm)
            else if (page == 1) ScheduleScreen(s.schedule)
            else third?.invoke()
        }
        PageDots(pager.currentPage, if (third != null) 3 else 2) { page -> scope.launch { pager.animateScrollToPage(page) } }
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

/** HS6: the dock's own small icons, drawn from simple shapes so they follow the theme. */
@Composable
private fun DockIcon(kind: String, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width; val sw = 1.8.dp.toPx(); val st = Stroke(sw, cap = StrokeCap.Round)
        when (kind) {
            "phone" -> { drawRoundRect(color, Offset(w * 0.28f, w * 0.06f), Size(w * 0.44f, w * 0.88f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f), style = st); drawLine(color, Offset(w * 0.42f, w * 0.8f), Offset(w * 0.58f, w * 0.8f), sw, StrokeCap.Round) }
            "messages" -> { drawRoundRect(color, Offset(w * 0.08f, w * 0.14f), Size(w * 0.84f, w * 0.56f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f), style = st); drawLine(color, Offset(w * 0.3f, w * 0.7f), Offset(w * 0.26f, w * 0.92f), sw, StrokeCap.Round); drawLine(color, Offset(w * 0.26f, w * 0.92f), Offset(w * 0.5f, w * 0.7f), sw, StrokeCap.Round) }
            "maps" -> { drawCircle(color, w * 0.22f, Offset(w * 0.5f, w * 0.36f), style = st); drawLine(color, Offset(w * 0.34f, w * 0.52f), Offset(w * 0.5f, w * 0.92f), sw, StrokeCap.Round); drawLine(color, Offset(w * 0.66f, w * 0.52f), Offset(w * 0.5f, w * 0.92f), sw, StrokeCap.Round) }
            else -> { drawCircle(color, w * 0.2f, Offset(w * 0.5f, w * 0.5f), style = st); for (i in 0 until 8) { val a = Math.PI / 4 * i; drawLine(color, Offset(w * (0.5f + 0.3f * Math.cos(a).toFloat()), w * (0.5f + 0.3f * Math.sin(a).toFloat())), Offset(w * (0.5f + 0.44f * Math.cos(a).toFloat()), w * (0.5f + 0.44f * Math.sin(a).toFloat())), sw, StrokeCap.Round) } }
        }
    }
}

/** Two dots, the current page filled. Each is a button of its own, so the other page is one tap away as well as one swipe. */
@Composable
private fun PageDots(page: Int, count: Int, onPage: (Int) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        listOf("Home" to 0, "Schedule" to 1, "Right now" to 2).take(count).forEach { (name, i) ->
            Box(
                Modifier.heightIn(min = 32.dp).widthIn(min = 48.dp).clickable(role = Role.Tab) { onPage(i) }.semantics { contentDescription = "$name page" + if (page == i) ", showing" else "" },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(width = androidx.compose.animation.core.animateDpAsState(if (page == i) 22.dp else 8.dp, motion(Motion.STANDARD), label = "dot").value, height = 8.dp).background(if (page == i) c.accent else c.track, CircleShape)) }
        }
    }
}

@Composable
private fun HomePage(
    s: HomeState, onBuffer: () -> Unit, onNow: () -> Unit, onProofDue: () -> Unit, onSetup: () -> Unit, onHeld: () -> Unit, onEssential: (String) -> Unit,
    onRestoreLauncher: () -> Unit, onReady: () -> Unit, onMoney: () -> Unit, onReview: () -> Unit, onShopping: () -> Unit, onNewRoom: () -> Unit, onPhoto: () -> Unit, onMeal: (String, Boolean) -> Unit, onReality: (String) -> Unit, onEmergency: (efa.EmergencyAction) -> Unit, onAdapt: (String) -> Unit = {}, onVr: (VrAction) -> Unit = {}, onConfirm: (String, String) -> Unit = { _, _ -> },
) {
    val c = LocalColors.current
    var allNeeds by remember { mutableStateOf(false) }
    // HS2, AD: the home is laid out in one order, always. The clock, then NOW (the hero, with the three things you report from it), then NEXT, then what is waiting for an answer
    // (the first two, the rest one tap away), then the Buffer, then the quieter cards. Nothing waiting for you is allowed to push Now off the first screen.
    val needs = buildList<@Composable () -> Unit> {
        s.confirms.forEach { q -> add { ConfirmCard(q, onConfirm) } }                                  // CF1: a question to answer comes first
        s.photo?.let { add { PhotoCard(it, s.photoLeft, onPhoto) } }
        s.morning?.let { add { InfoCard("MORNING", "Ready photo needed", onReady, it) } }
        s.meal?.let { m -> add { MealCard(m, s.mealKey, onMeal) } }
        s.adapt.driftLine?.let { line -> add { DriftCard(line, s.adapt.driftDetail, onAdapt) } }
        s.proofDue?.let { add { InfoCard("PROOF DUE", it, onProofDue, null) } }
        if (s.launcherPaused) add { InfoCard("LAUNCHER PAUSED", "Put Spark back as a home choice", onRestoreLauncher, "Spark crashed 3 times in 10 minutes. Blocking kept running.") }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Label(s.date); s.period?.let { Label(it) } }
            if (s.phone != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(9.dp).background(if (s.phoneOpen) c.accent else c.muted, CircleShape))
                Label(s.phone + if (s.earnedOn) ", earned" else "")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(s.time, style = Ts(c.text, 72.sp, FontWeight.Bold, letterSpacing = (-3).sp, lineHeight = 72.sp))
            SparkLogo(38.dp)
        }
        s.vr?.let { if (!s.unavailable) VrHero(it, onReality) { a -> onVr(a) } }                      // VR11: a running VRNPM cycle comes first, big
        val vrIsNow = s.vr != null && s.nowKey != null && blockKey(s.vr.ds) == s.nowKey                     // VR16: it is the Now card for its block, so the block is not drawn twice
        if (s.unavailable) InfoCard("SCHEDULE", "Schedule unavailable", null, "The database could not be read.")
        else if (efa.WidgetKind.NOW_NEXT in s.widgets && !vrIsNow) NowHero(s, onNow, onReality)
        if (!s.unavailable && (efa.WidgetKind.DAY_PLAN in s.widgets || s.lite)) NextCard(if (s.lite) s.next.take(2) else s.next)
        needs.take(if (allNeeds) needs.size else 2).forEach { it() }
        if (needs.size > 2 && !allNeeds) Chip("${needs.size - 2} more waiting", false, { allNeeds = true }, "Show ${needs.size - 2} more things waiting for you")
        s.adapt.levelLine?.let { BasicText(it, style = Ts(c.muted, 13.sp)) }
        if (s.adapt.three.isNotEmpty()) Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("THREE THINGS THAT MATTER")
            s.adapt.three.forEach { BasicText(it, style = Ts(c.text, 15.sp)) }
            if (s.adapt.liteAuto) Chip("Back to the full day", false, { onAdapt("full") }, "Back to the full day")
        }
        if (!s.lite) BufferCard(s.buffer, onBuffer)
        if (s.mode.showsSecondary && (s.shoppingDay || efa.WidgetKind.SHOPPING in s.widgets)) InfoCard("SHOPPING", "Shopping list", onShopping, null)
        s.newRoom?.takeIf { s.mode.showsSecondary }?.let { InfoCard("WHERE IS IT?", "Where is $it?", onNewRoom, "Put it on the map so attendance can be checked.") }
        s.voiceNote?.let { BasicText(it, style = Ts(c.text, 14.sp)) }
        s.review?.takeIf { s.mode.showsSecondary }?.let { InfoCard("HELP THE ESTIMATES", it, onReview, null) }
        if (s.heldWaiting) InfoCard("HELD NOTIFICATIONS", "Waiting for you", onHeld, "Open the digest.")
        if (s.mode.showsSecondary && efa.WidgetKind.WEEK_GLANCE in s.widgets && s.week.isNotEmpty()) InfoCard("WEEK", s.week.first(), null, s.week.drop(1).joinToString("\n"))
        if (s.mode.showsSecondary && !s.lite) MoneyCard(s, onMoney)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Phone" to "phone", "Msgs" to "messages", "Maps" to "maps").forEach { (label, key) ->
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).card().clip(CardShape).clickable(role = Role.Button) { onEssential(key) }.semantics { contentDescription = "$label. Opens your $label app." },
                    contentAlignment = Alignment.Center,
                ) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) { DockIcon(key, c.text); BasicText(label, style = Ts(c.text, 13.sp, FontWeight.Medium)) } }
            }
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).card().clip(CardShape).clickable(role = Role.Button, onClick = onSetup).semantics { contentDescription = "Setup: home, venues, blocking and GitHub" },
                contentAlignment = Alignment.Center,
            ) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) { DockIcon("setup", c.muted); BasicText("Setup", style = Ts(c.muted, 13.sp, FontWeight.Medium)) } }
        }
        // EM1: the Emergency door is one tap from the home, never gated
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(efa.EmergencyAction.CALL_EMERGENCY, efa.EmergencyAction.HEALTH, efa.EmergencyAction.SHARE_LOCATION).forEach { a -> Chip(a.label, false, { onEmergency(a) }, "Emergency: ${a.label}") }
        }
    }
}

/**
 * NOW, the hero. What is on, how long is left as a ring, and under it the three things you report from here (HM1, MX4): it is running over, you started late, or something came up.
 * They are free and one tap each, and they sit in the card so the card reads as one thing: what is happening, and what to do if it is not going to plan.
 */
@Composable
private fun NowHero(s: HomeState, onNow: () -> Unit, onReality: (String) -> Unit) {
    val c = LocalColors.current
    val label = (s.nowLabel ?: "Now").uppercase(); val value = s.nowTitle ?: "Nothing scheduled"
    val detail = s.nowDetail?.takeIf { it.isNotBlank() }
    val opens = s.nowTitle != null && s.nowOpensBlock
    val acts = buildList {
        if (s.nowTitle != null) { add("Running over" to ("over" to "Running over: this is taking longer")); add("Started late" to ("late" to "Started late: start the day from now")) }
        add("Something came up" to ("cameup" to "Something came up: say what happened and Spark finds a place for it"))
        add("Change the plan" to ("door" to "Change the plan: a day off, the library, a file, the wake time and more"))          // CD1: every other change is one tap away
    }
    Column(Modifier.fillMaxWidth().card(efa.CardKind.NOW)) {
        Row(
            Modifier.fillMaxWidth().then(if (opens) Modifier.clip(CardShape).clickable(role = Role.Button, onClick = onNow) else Modifier).padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics { contentDescription = "$label. $value." + (detail?.let { " $it" } ?: "") + if (opens) " Tap to open." else "" },
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Label(label)
                BasicText(value, style = Ts(c.text, 23.sp, FontWeight.Bold, lineHeight = 28.sp))
                detail?.let { BasicText(it, style = Ts(c.muted, 13.sp)) }
            }
            s.nowFraction?.let { f ->
                Donut(listOf(Slice(f, c.accent)), 68.dp, 9.dp) {
                    val left = s.nowLeftMin
                    if (left != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(if (left >= 100) "${left / 60}h" else "$left", style = Ts(c.text, 17.sp, FontWeight.Bold))
                        BasicText(if (left >= 100) "left" else "min", style = Ts(c.muted, 10.sp, fontFamily = Mono))
                    }
                }
            }
        }
        if (!s.unavailable) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.track))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))) {
                acts.forEachIndexed { i, (text, kd) ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.track))
                    Box(
                        Modifier.weight(if (kd.first == "cameup") 1.45f else 1f).heightIn(min = 50.dp).clickable(role = Role.Button) { onReality(kd.first) }.padding(horizontal = 6.dp).semantics { contentDescription = kd.second },
                        contentAlignment = Alignment.Center,
                    ) { BasicText(text, style = Ts(c.text, 13.sp, FontWeight.Medium).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 2) }
                }
            }
            if (s.evening) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.track))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)).clickable(role = Role.Button) { onReality("night") }.semantics { contentDescription = "Going to bed later tonight" },
                    contentAlignment = Alignment.Center,
                ) { BasicText("Bed later tonight", style = Ts(c.text, 13.5.sp, FontWeight.Medium)) }
            }
        }
    }
}

/** AD1: the day and the plan have come apart. One proposal, free to take, easy to send away. */
@Composable
private fun DriftCard(line: String, detail: String?, onAdapt: (String) -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("YOU ARE BEHIND")
        BasicText(line, style = Ts(c.text, 17.sp, FontWeight.Bold, lineHeight = 22.sp))
        detail?.takeIf { it.isNotBlank() }?.let { BasicText(it, style = Ts(c.muted, 13.sp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Replan from now", { onAdapt("drift") }, primary = true, description = "Replan the rest of today from now")
            Btn("Not now", { onAdapt("dismiss") }, description = "Not now")
        }
    }
}

@Composable
private fun MealCard(m: String, key: String, onMeal: (String, Boolean) -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("DID YOU EAT?")
        BasicText(m, style = Ts(c.text, 18.sp, FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Btn("Ate", { onMeal(key, true) }, primary = true, description = "Ate $m"); Btn("Not yet", { onMeal(key, false) }, description = "Not yet, $m") }
    }
}

/**
 * PH1: the photo to take. When one is open it is the loudest thing on the home screen: a filled card that says to take it now, what it is for, by when,
 * and shows how much of the time to take it in full is left. Otherwise it is a quiet card for the next one, with when it opens. Tap it to go and take it.
 */
@Composable
private fun PhotoCard(p: efa.PhotoDue, left: Int?, onClick: () -> Unit) {
    val c = LocalColors.current
    val open = p.open
    val fg = if (open) c.onAccent else c.text
    val sub = if (open) c.onAccent else c.muted
    val window = java.time.Duration.between(p.opens, p.dueBy).toMinutes().coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().then(if (open) Modifier.background(c.accent, CardShape) else Modifier.card()).clip(CardShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = (if (open) "Photo due. " else "Next photo. ") + p.headline() + ". " + p.detail() + " Tap to open." },
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText((if (open) (if (p.kind == efa.PhotoKind.COMMIT) "COMMIT DUE" else "PHOTO DUE") else (if (p.kind == efa.PhotoKind.COMMIT) "NEXT COMMIT" else "NEXT PHOTO")), style = Ts(sub, 11.sp, fontFamily = Mono, letterSpacing = 0.9.sp))
            BasicText(p.headline(), style = Ts(fg, 24.sp, FontWeight.Bold, letterSpacing = (-0.5).sp))
            BasicText(p.detail(), style = Ts(sub, 13.sp))
        }
        if (open && left != null) Donut(listOf(Slice(left.toFloat() / window, c.onAccent)), 64.dp, 8.dp, track = c.onAccent.copy(alpha = 0.25f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText("$left", style = Ts(c.onAccent, 18.sp, FontWeight.Bold))
                BasicText("min", style = Ts(c.onAccent, 10.sp, fontFamily = Mono))
            }
        }
    }
}

/** Money: the amount, and under it a bar of how much of the week's planned work is confirmed. Both come from the same week (R4, R7). */
@Composable
private fun MoneyCard(s: HomeState, onMoney: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().card(efa.CardKind.SPEND).clip(CardShape).clickable(role = Role.Button, onClick = onMoney).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "Free to spend this week. " + (s.freeToSpendPence?.let { pounds(it) } ?: "Recording") + ". Tap for the working." },
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        // The share of the week's planned work that is confirmed is a part of a whole, so it is a ring, not a bar.
        s.progressFraction?.let { f -> Donut(listOf(Slice(f, c.accent)), 72.dp, 10.dp) { BasicText("${Math.round(f * 100)}%", style = Ts(c.text, 16.sp, FontWeight.Bold)) } }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("Free to spend this week")
            BasicText(s.freeToSpendPence?.let { pounds(it) } ?: "Recording", style = Ts(c.text, 28.sp, FontWeight.Bold, letterSpacing = (-0.5).sp))
            s.progress?.let { BasicText(it, style = Ts(c.muted, 12.sp, fontFamily = Mono)) }
            if (s.freeToSpendPence == null) BasicText(s.moneyNote ?: "Recorded for the first 14 days.", style = Ts(c.muted, 12.sp))
        }
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
        Modifier.fillMaxWidth().card(efa.CardKind.BUFFER).clip(CardShape).clickable(enabled = b is BufferState.Value, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "Buffer. $big. $small. $spoken." + if (b is BufferState.Value) " Tap for the working." else "" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Dial(100.dp, fill, big, small)
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
    val sweep = animatedFraction(fillDegrees / 360f, 700) * 360f           // the dial sweeps round to its value when it appears (AN1)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 16.dp.toPx(); val tl = Offset(w / 2, w / 2); val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(c.track, 0f, 360f, false, tl, sz, style = Stroke(w))
            if (sweep > 0f) drawArc(c.accent, -90f, sweep, false, tl, sz, style = Stroke(w, cap = StrokeCap.Butt))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(big, style = Ts(c.text, if (size < 110.dp) 21.sp else 26.sp, FontWeight.Bold, letterSpacing = (-0.8).sp))
            Label(small)
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String, onClick: (() -> Unit)?, detail: String?, kind: efa.CardKind? = null, fraction: Float? = null, leftMin: Int? = null) {
    val c = LocalColors.current
    val tap = if (onClick != null) Modifier.clip(CardShape).clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        Modifier.fillMaxWidth().card(kind).then(tap).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "$label. $value." + (detail?.let { " $it" } ?: "") + if (onClick != null) " Tap to open." else "" },
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(label)
            BasicText(value, style = Ts(c.text, 21.sp, FontWeight.Bold))
            detail?.let { BasicText(it, style = Ts(c.muted, 13.sp)) }
        }
        // How much of the running block has gone is a part of a whole: a ring with the minutes left in it.
        fraction?.let { f ->
            Donut(listOf(Slice(f, c.accent)), 60.dp, 8.dp) {
                if (leftMin != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(if (leftMin >= 100) "${leftMin / 60}h" else "$leftMin", style = Ts(c.text, 16.sp, FontWeight.Bold))
                    BasicText(if (leftMin >= 100) "left" else "min", style = Ts(c.muted, 10.sp, fontFamily = Mono))
                }
            }
        }
    }
}

@Composable
private fun NextCard(rows: List<NextRow>) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card(efa.CardKind.NEXT).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label("Next")
        if (rows.isEmpty()) BasicText("Nothing scheduled", style = Ts(c.text, 15.sp, FontWeight.Medium), modifier = Modifier.padding(top = 4.dp))
        // a timeline: a dot per thing, joined by a line, the first one filled and larger because it is the one that is coming
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(14.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = if (i == 0) 9.dp else 7.dp).size(if (i == 0) 12.dp else 9.dp).background(if (i == 0) c.accent else c.track, CircleShape))
                    if (i < rows.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(c.track))
                }
                BasicText(r.time, style = Ts(c.accentText, 14.sp, FontWeight.Medium, fontFamily = Mono), modifier = Modifier.width(48.dp).padding(top = if (i == 0) 8.dp else 6.dp))
                Column(Modifier.weight(1f).padding(top = if (i == 0) 5.dp else 4.dp, bottom = if (i < rows.lastIndex) 12.dp else 2.dp)) {
                    BasicText(r.title, style = Ts(c.text, if (i == 0) 17.sp else 15.sp, if (i == 0) FontWeight.Bold else FontWeight.Medium))
                    if (r.detail.isNotBlank()) BasicText(r.detail, style = Ts(c.muted, 12.5.sp), maxLines = 1)
                }
            }
        }
    }
}
