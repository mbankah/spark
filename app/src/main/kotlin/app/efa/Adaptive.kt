package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.*
import efa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

/** What the home shows of the adaptive layer (AD): a drift to answer, the step the day is on, and, on the bottom step, the three things that matter. */
data class AdaptView(
    val driftLine: String? = null, val driftDetail: String? = null, val levelLine: String? = null,
    val three: List<String> = emptyList(), val liteAuto: Boolean = false,
)

/**
 * AD1, AD3: held in memory and worked out each minute with the rest of the state, so the home reads it in microseconds. A failure here changes nothing else (X7): nothing is proposed,
 * the day stays a full day, and blocking, alarms and the plan are untouched.
 */
object AdaptState {
    @Volatile var drift: Drift? = null; private set
    @Volatile var proposal: DriftProposal? = null; private set
    @Volatile var level: DayLevel = DayLevel.FULL; private set
    @Volatile var three: List<Matter> = emptyList(); private set
    @Volatile var liteAuto: Boolean = false; private set

    suspend fun refresh(db: EfaDb, pattern: List<PatternSlot>, verified: Set<String>, now: ZonedDateTime) {
        try {
            val since = now.toLocalDate().minusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val started = db.blocks().startedKeys(since).toSet()
            val weekAgo = now.toLocalDate().minusDays(7).atStartOfDay(now.zone).toInstant().toEpochMilli()
            if (!startSignalsSeen(db.blocks().startedKeys(weekAgo).size, verified.size)) {          // AD1: nothing to go on yet, so nothing is said
                drift = null; proposal = null; level = DayLevel.FULL; three = emptyList()
                val cur = if (db.meta().get(META_LITE) == "1") LiteSource.MANUAL else if (db.meta().get(META_LITE_AUTO) == "1") LiteSource.AUTO else LiteSource.NONE
                if (cur == LiteSource.AUTO) db.meta().put(MetaRow(META_LITE_AUTO, "0"))
                liteAuto = false
                return
            }
            val acked = db.meta().get(META_DRIFT_ACK)?.toLongOrNull()?.let { java.time.Instant.ofEpochMilli(it).atZone(now.zone) }
            val d = detectDrift(pattern, now, started, verified, acked)
            drift = d
            proposal = d?.let { adaptDrift(it, pattern, db.tasks().open().map { t -> t.toDue() }, now) }
            level = dayLevel(squeezeOf(pattern, now, d?.behindMin ?: 0), level)
            val manual = db.meta().get(META_LITE) == "1"
            val cur = if (manual) LiteSource.MANUAL else if (db.meta().get(META_LITE_AUTO) == "1") LiteSource.AUTO else LiteSource.NONE
            val dismissed = db.meta().get(META_LITE_DISMISSED) == now.toLocalDate().toString()
            val next = liteAfter(cur, level, dismissed)
            if (cur != LiteSource.MANUAL && next != cur) db.meta().put(MetaRow(META_LITE_AUTO, if (next == LiteSource.AUTO) "1" else "0"))
            liteAuto = next == LiteSource.AUTO
            three = if (level.lite || liteAuto) threeThatMatter(pattern, now, verified) else emptyList()
        } catch (e: Exception) { /* X7 */ }
    }

    fun view(): AdaptView {
        val p = proposal
        val lv = level
        return AdaptView(
            driftLine = p?.let { driftLine(it.drift) },
            driftDetail = p?.let { (it.drift.reasons.take(2) + listOfNotNull(it.preview.compiled.lines.firstOrNull())).joinToString(" ") },
            levelLine = when (lv) {
                DayLevel.FULL -> null
                DayLevel.SHORT -> "Short day: routines run at their short version until you catch up."
                DayLevel.FLOOR -> "Floor day: routines run at their floor. Sleep, the Bible floor and what does not move stay."
                DayLevel.THREE -> "Three things: only what matters shows until you catch up."
            },
            three = three.map { "%02d:%02d  %s. %s.".format(it.start.hour, it.start.minute, it.title, it.why) },
            liteAuto = liteAuto,
        )
    }

    /** AD1: you answered the proposal (took it or sent it away). The blocks behind you are not counted again; new drift is. */
    suspend fun acknowledge(db: EfaDb, nowMs: Long) {
        db.meta().put(MetaRow(META_DRIFT_ACK, nowMs.toString()))
        drift = null; proposal = null
    }

    /** AD3: "back to the full day": the ladder lets go of Lite for today. */
    suspend fun fullDay(db: EfaDb, today: java.time.LocalDate) {
        db.meta().put(MetaRow(META_LITE_DISMISSED, today.toString())); db.meta().put(MetaRow(META_LITE_AUTO, "0"))
        liteAuto = false; three = emptyList()
    }
}

@Composable
private fun CameUpBox(value: String, onChange: (String) -> Unit, hint: String, description: String) {
    val c = LocalColors.current
    BasicTextField(
        value, onChange, textStyle = Ts(c.text, 16.sp), cursorBrush = SolidColor(c.accent), singleLine = false, maxLines = 3,
        modifier = Modifier.fillMaxWidth().card().padding(horizontal = 14.dp, vertical = 14.dp).semantics { contentDescription = description },
        decorationBox = { inner -> if (value.isEmpty()) BasicText(hint, style = Ts(c.muted, 16.sp)); inner() },
    )
}

private fun bufferWords(b: BufferState) = when (b) { is BufferState.Value -> b.result.label() + (b.result.deadlineLabel?.let { " to $it" } ?: ""); BufferState.NoDeadline -> "No deadline"; else -> "Not available" }

/**
 * AD2: Something came up. Say what happened in a few words, how long it takes, and whether it can move. Spark finds a place for it, shows what it costs in numbers, and offers the
 * next best way if it does not fit as asked. Nothing changes here: "Use this" opens the normal preview, where Apply is the same free-or-wall decision as everywhere else.
 */
@Composable
fun CameUpScreen(db: EfaDb, onPlan: (Instance) -> Unit, onBack: () -> Unit) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(30) }
    var flex by remember { mutableStateOf(Flex.FIXED) }
    var result by remember { mutableStateOf<Proposal?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("SOMETHING CAME UP")
        BasicText("Say what happened. Spark finds a place for it and shows what it costs. Nothing changes until you apply it, and work that has to give way comes back as work, so moving it is free.", style = Ts(c.muted, 14.sp))
        CameUpBox(text, { text = it; result = null }, "What happened? For example: a friend turned up", "What happened")
        Label("HOW LONG")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 30, 45, 60).forEach { m -> Chip("$m min", minutes == m, { minutes = m; result = null }, "$m minutes") } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(90, 120, 180, 240).forEach { m -> Chip("$m min", minutes == m, { minutes = m; result = null }, "$m minutes") } }
        Label("CAN IT MOVE?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Flex.entries.forEach { f -> Chip(f.label, flex == f, { flex = f; result = null }, f.label) } }
        Btn(if (busy) "Looking..." else "Find a place", {
            if (!busy) { busy = true; scope.launch(Dispatchers.IO) {
                val now = BlockingState.now()
                val r = try {
                    val pattern = PlanRepo(db).slots(); val work = db.tasks().open().map { it.toDue() }
                    val active = db.modifiers().all().filter { it.state == "active" }.mapNotNull { decodeInstance(it.instance) }
                    proposeFor(Situation(text, minutes, flex), pattern, work, now, now.zone, active.mapNotNull { nightAnchorsOf(it) }.associateBy { it.night })
                } catch (e: Exception) { null }
                withContext(Dispatchers.Main) { result = r; busy = false }
            } }
        }, primary = true, modifier = Modifier.fillMaxWidth(), description = "Find a place")
        result?.let { pr ->
            val offer = pr.offer
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("WHAT SPARK FOUND")
                if (offer == null) {
                    BasicText("Nothing can be placed for that.", style = Ts(c.text, 16.sp, FontWeight.Bold))
                    pr.reasons.forEach { BasicText(it, style = Ts(c.text, 14.sp)) }
                    BasicText("Try a shorter time, or say it can move.", style = Ts(c.muted, 13.sp))
                } else {
                    val (plan, pv) = offer
                    plan.notes.forEach { BasicText(it, style = Ts(c.text, 14.sp)) }
                    pv.compiled.lines.take(5).forEach { BasicText(it, style = Ts(c.text, 14.sp)) }
                    BasicText("Buffer now: ${bufferWords(pv.before)}", style = Ts(c.muted, 13.sp)); BasicText("Buffer with this: ${bufferWords(pv.after)}", style = Ts(c.muted, 13.sp, FontWeight.Bold))
                    if (pv.wallNeeded) { BasicText("This gives something up, so it needs the typed wall:", style = Ts(c.text, 14.sp, FontWeight.Bold)); pv.compiled.loosening.take(3).forEach { BasicText(it, style = Ts(c.text, 13.sp)) } }
                    else BasicText("Nothing is given up. It is free.", style = Ts(c.text, 14.sp, FontWeight.Bold))
                    Btn(if (pr.preview.canApply) "Use this" else "Use this instead", { onPlan(plan.instance) }, primary = true, modifier = Modifier.fillMaxWidth(), description = "Use this plan")
                    pr.freeOffer?.takeIf { it.first.instance != plan.instance }?.let { (alt, apv) ->
                        BasicText("Or without giving anything up: ${alt.placedAt}. Buffer with that: ${bufferWords(apv.after)}", style = Ts(c.text, 14.sp))
                        Btn("Use the one that gives nothing up", { onPlan(alt.instance) }, modifier = Modifier.fillMaxWidth(), description = "Use the one that gives nothing up")
                    }
                }
            }
        }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}
