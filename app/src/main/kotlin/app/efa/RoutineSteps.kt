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
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * BS1 to BS3: a block that is a routine carries its steps. They are on the block's own screen, one list at the tier the time left in the block and last night's sleep allow (RT5), each step done
 * by a tap, a registered tag or its proof. When the last step is done the routine is done: a fact is kept once, a reward is offered, and [onAllDone] tells the block (so a block that was started is
 * finished by it, not by a second hold). This is the runner that used to be a page of its own.
 */
@Composable
fun RoutineStepsCard(db: EfaDb, routine: Routine, ds: DatedSlot, onAllDone: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var tier by remember(routine.id, ds) { mutableStateOf<Tier?>(null) }
    var facts by remember { mutableStateOf<List<Fact>>(emptyList()) }
    var taps by remember { mutableStateOf<Set<NfcPurpose>>(emptySet()) }
    var nfcOn by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<String?>(null) }

    // the tier is picked once from the time left in the block and last night's sleep (RT5); a tap on a chip changes it
    LaunchedEffect(routine.id, ds) {
        withContext(Dispatchers.IO) {
            val now = ZonedDateTime.now(ds.start.zone)
            val avail = Duration.between(if (now.isAfter(ds.start)) now else ds.start, ds.end).toMinutes().toInt().coerceAtLeast(0)
            val sleep = db.nights().latest()?.sleepMin?.div(60.0)
            tier = capTier(pickTier(routine, avail, sleep, null), AdaptState.level)
        }
    }
    LaunchedEffect(tick) {
        withContext(Dispatchers.IO) {
            val repo = FactRepo(db)
            facts = repo.today(); taps = repo.recentTaps(); nfcOn = db.meta().get(META_NFC_ON) == "1"
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }                       // a tag tapped, or a step done elsewhere, shows within a moment

    val t = tier ?: return
    val steps = runSteps(routine, t, facts, taps, nfcOn)
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("STEPS")
        BasicText("${routine.title}: ${t.name.lowercase()} tier, ${routine.minutes(t)} minutes", style = Ts(c.text, 17.sp, FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tier.entries.forEach { x -> Chip(x.name.lowercase().replaceFirstChar { it.uppercase() }, t == x, { tier = x }, "Tier ${x.name.lowercase()}") } }
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
            val doneId = "routine:${routine.id}"
            LaunchedEffect(routine.id, facts.size) {
                withContext(Dispatchers.IO) {
                    // once for this block: a routine done earlier today is not done again by looking at its block
                    if (facts.none { it.ref == doneId && !it.at.isBefore(ds.start.toInstant()) }) {
                        FactRepo(db).record(Fact(FactType.TAP, Instant.now(), "routine", 1.0, doneId)); Coach.haptic(ctx, Haptic.REWARD)
                        reward = rewardPick(db.meta().get(META_REWARDS).orEmpty().lines().filter { it.isNotBlank() }, System.currentTimeMillis())
                        withContext(Dispatchers.Main) { onAllDone() }
                        tick++
                    }
                }
            }
            BasicText("${routine.title} is done.", style = Ts(c.text, 18.sp, FontWeight.Bold))
            reward?.let { BasicText("Your reward: $it", style = Ts(c.text, 15.sp)) }
        }
    }
}
