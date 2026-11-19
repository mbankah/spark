package app.efa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.*
import efa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime

private val RUNNABLE: List<Routine> get() = listOf(BIBLE, WAKE, GETTING_READY, STUDY_BLOCK, PASTA_AND_SHOWER, WIND_DOWN, LEAVE_HOME, ARRIVE_HOME)

/**
 * The last of the third page: the routine runner (RT, CO), NFC tags as an option (SL2), break glass (CE7), and today's wins (CO9). The runner takes a routine at the tier the time and
 * the night allow, shows its steps one by one, accepts a tap or a registered tag, and says plainly which steps are photo steps done on the home screen.
 */
@Composable
fun WorkItOutExtra(db: EfaDb, onWall: (String, String, Int, () -> Unit) -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf<Routine?>(null) }
    var tier by remember { mutableStateOf(Tier.FULL) }
    var facts by remember { mutableStateOf<List<Fact>>(emptyList()) }
    var taps by remember { mutableStateOf<Set<NfcPurpose>>(emptySet()) }
    var nfcOn by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf<Map<String, NfcPurpose>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }
    var glassOk by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<String?>(null) }
    var identity by remember { mutableStateOf<List<String>>(emptyList()) }
    val nfcAvailable = remember { Nfc.available(ctx) }

    LaunchedEffect(tick) {
        withContext(Dispatchers.IO) {
            val repo = FactRepo(db)
            facts = repo.today(); taps = repo.recentTaps(); tags = repo.tags(); nfcOn = db.meta().get(META_NFC_ON) == "1"
            glassOk = breakGlassAvailable(DoorRepo(db).uses(), Instant.now())
            identity = db.meta().get(META_IDENTITY).orEmpty().lines().filter { it.isNotBlank() }
            Nfc.message?.let { message = it; Nfc.message = null }
        }
    }
    LaunchedEffect(running, nfcOn) { while (running != null || (nfcOn && Nfc.registering != null)) { delay(1500); tick++ } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // RT, CO: the runner
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("RUN A ROUTINE")
            val r = running
            if (r == null) {
                BasicText("One step at a time, at the tier today allows. Photo steps are taken on the home screen.", style = Ts(c.muted, 13.sp))
                RUNNABLE.forEach { rt ->
                    Btn(rt.title, {
                        scope.launch(Dispatchers.IO) {
                            val now = ZonedDateTime.now(LONDON)
                            val nextStart = nowAndNext(PlanRepo(db).slots(), now).next?.start
                            val avail = nextStart?.let { java.time.Duration.between(now, it).toMinutes().toInt() } ?: 60
                            val sleep = db.nights().latest()?.sleepMin?.div(60.0)
                            tier = pickTier(rt, avail.coerceAtLeast(0), sleep, null); reward = null; running = rt
                        }
                    }, modifier = Modifier.fillMaxWidth(), description = "Run ${rt.title}")
                }
            } else {
                BasicText("${r.title}: ${tier.name.lowercase()} tier, ${r.minutes(tier)} minutes", style = Ts(c.text, 17.sp, FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tier.entries.forEach { t -> Chip(t.name.lowercase().replaceFirstChar { it.uppercase() }, tier == t, { tier = t }, "Tier ${t.name.lowercase()}") } }
                val steps = runSteps(r, tier, facts, taps, nfcOn)
                steps.forEach { rs ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText((if (rs.done) "Done: " else "") + rs.step.say + (if (rs.step.minutes > 0) " (${rs.step.minutes} min)" else ""), style = Ts(if (rs.done) c.muted else c.text, 15.sp, FontWeight.Medium))
                        when {
                            rs.done -> {}
                            rs.needsPhoto -> BasicText("Photo step: take it on the home screen.", style = Ts(c.muted, 12.sp))
                            else -> {
                                rs.tagPurpose?.let { tp -> BasicText("Or tap the ${tp.label} tag.", style = Ts(c.muted, 12.sp)) }
                                if (rs.step.proof.rank >= ProofTier.SENSOR.rank) BasicText("Confirmed by tap: Spark cannot sense this one yet.", style = Ts(c.muted, 12.sp))
                                Btn("Done", { scope.launch(Dispatchers.IO) { FactRepo(db).record(Fact(FactType.TAP, Instant.now(), "tap", 1.0, "step:${rs.step.id}")); Coach.haptic(ctx, Haptic.START); tick++ } }, modifier = Modifier.fillMaxWidth(), description = "Done: ${rs.step.say}")
                            }
                        }
                    }
                }
                if (routineDone(steps)) {
                    val doneId = "routine:${r.id}"
                    LaunchedEffect(r.id, facts.size) {
                        withContext(Dispatchers.IO) {
                            if (facts.none { it.ref == doneId }) {
                                FactRepo(db).record(Fact(FactType.TAP, Instant.now(), "routine", 1.0, doneId)); Coach.haptic(ctx, Haptic.REWARD)
                                reward = rewardPick(db.meta().get(META_REWARDS).orEmpty().lines().filter { it.isNotBlank() }, System.currentTimeMillis())
                                tick++
                            }
                        }
                    }
                    BasicText("${r.title} is done.", style = Ts(c.text, 18.sp, FontWeight.Bold))
                    reward?.let { BasicText("Your reward: $it", style = Ts(c.text, 15.sp)) }
                }
                Btn("Close", { running = null }, modifier = Modifier.fillMaxWidth(), description = "Close the routine")
            }
        }

        // CO9, CO10: today's wins, three true facts
        val done = RUNNABLE.filter { rt -> facts.any { it.ref == "routine:${rt.id}" } }
        val steps = facts.count { it.ref.startsWith("step:") }
        if (done.isNotEmpty() || steps > 0) Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("TODAY'S WINS")
            val wins = todaysWins(steps, steps, 0, bibleDone = done.any { it.id == "bible" }, upOnTime = false).filter { !it.contains("steps done") } +
                done.filter { it.id != "bible" }.map { "${it.title} done." } + (if (steps > 0) listOf("$steps steps done.") else emptyList())
            wins.take(3).forEach { BasicText(it, style = Ts(c.text, 15.sp)) }
            identityLine(identity, done.size, System.currentTimeMillis() / 86_400_000L)?.let { BasicText(it, style = Ts(c.muted, 13.sp)) }
        }

        // SL2: NFC, an option
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("NFC TAGS (OPTIONAL)")
            BasicText("Photos stay the proof. If you buy cheap NFC stickers, switch this on and register each one. A tap then proves a step in a second. Read only while Spark is open.", style = Ts(c.muted, 13.sp))
            if (!nfcAvailable) BasicText("This phone has no NFC, so this stays off.", style = Ts(c.text, 14.sp, FontWeight.Bold))
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("NFC on", nfcOn, { scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(META_NFC_ON, "1")); tick++ } }, "NFC tags on"); Chip("NFC off", !nfcOn, { scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(META_NFC_ON, "0")); Nfc.registering = null; tick++ } }, "NFC tags off")
                }
                if (nfcOn) {
                    BasicText("Choose a place, then tap its sticker once.", style = Ts(c.text, 14.sp))
                    NfcPurpose.entries.forEach { p ->
                        val has = tags.values.contains(p)
                        Btn((if (has) "Registered: " else "Register: ") + p.label, { Nfc.registering = p; message = "Tap the ${p.label} sticker now."; tick++ }, modifier = Modifier.fillMaxWidth(), description = "Register the ${p.label} tag")
                    }
                    if (tags.isNotEmpty()) Btn("Forget all tags", { scope.launch(Dispatchers.IO) { FactRepo(db).saveTags(emptyMap()); tick++ } }, modifier = Modifier.fillMaxWidth(), description = "Forget all tags")
                }
            }
            message?.let { BasicText(it, style = Ts(c.text, 14.sp, FontWeight.Bold)) }
        }

        // CE7: break glass
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("BREAK GLASS")
            BasicText("Once in 30 days. It lifts one lock: this week's loosening lockdown, an import cooldown, and the WhatsApp cooldown. It never touches sleep. It needs the longest string, $BREAK_GLASS_WALL characters.", style = Ts(c.muted, 13.sp))
            if (glassOk) Btn("Break glass", {
                onWall("Break glass", "This lifts one lock. It can be used once in 30 days. Type the characters below by hand.", BREAK_GLASS_WALL) {
                    scope.launch(Dispatchers.IO) {
                        val now = Instant.now(); val changes = ChangeRepo(db)
                        DoorRepo(db).record(DoorUse(Door.BREAK_GLASS, now, 0, 0))
                        changes.replace(breakGlassHistory(changes.history(), now))
                        WHATSAPP_PACKAGES.forEach { db.meta().put(MetaRow(META_CAP_START + it, "0")) }
                        BlockingState.refresh(ctx); tick++
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), description = "Break glass")
            else BasicText("Used in the last 30 days.", style = Ts(c.text, 14.sp))
        }
    }
}
