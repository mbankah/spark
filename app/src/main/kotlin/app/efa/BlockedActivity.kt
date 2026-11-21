package app.efa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.BlockRepo
import app.efa.data.BlockingRepo
import app.efa.data.EfaDb
import efa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/**
 * The one plain card every route to a blocked app ends at (U7, LA8): search, a notification, recents, a link, the share sheet,
 * a widget and split screen all land here, because the service reads whatever app is in front.
 * Back, home and recents return to the launcher with no loop (LA10): back sends the user home and this task is not in recents.
 */
class BlockedActivity : ComponentActivity() {
    companion object { const val EXTRA_PKG = "pkg" }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = goHome() })
        val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
        val db = EfaDb.get(this)
        setContent {
            EfaTheme {
                var decision by remember { mutableStateOf<Decision?>(null) }
                var uses by remember { mutableIntStateOf(0) }
                var doorLock by remember { mutableStateOf<String?>(null) }        // DP2, DP4: the weekly pool is used up
                var doorWall by remember { mutableStateOf<Int?>(null) }
                var glassOk by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) { BlockingState.refresh(applicationContext) }
                    val d = BlockingState.decide(pkg)
                    decision = d
                    val s = BlockingState.snapshot
                    if (d is Decision.Block && d.night && s != null) {
                        val w = nightWindow(s.pattern, BlockingState.now(), lateness = s.lateness)
                        if (w != null) uses = withContext(Dispatchers.IO) { BlockingRepo(db).nightUsesSince(w.cutoff) }
                    }
                    if (d is Decision.Block && d.night) {
                        // DP2, DP4: a night override draws from the same weekly pool as every other door
                        val mins = overrideMinutes(BlockingState.fullyVerifiedTonight())
                        when (val r = withContext(Dispatchers.IO) { app.efa.data.DoorRepo(db).decide(gateDoor(Door.OVERRIDE, java.time.Instant.now(), mins)) }) {
                            is DoorDecision.Locked -> doorLock = r.reason
                            is DoorDecision.Open -> doorWall = r.wallLength
                        }
                    }
                    if (doorLock != null) glassOk = withContext(Dispatchers.IO) { breakGlassAvailable(app.efa.data.DoorRepo(db).uses(), java.time.Instant.now()) }
                    if (d is Decision.Block && d.reason == BlockReason.APP_CAP) {
                        val left = java.time.Duration.between(BlockingState.now(), d.until).toMinutes().toInt().coerceAtLeast(1)
                        when (val r = withContext(Dispatchers.IO) { app.efa.data.DoorRepo(db).decide(DoorUse(Door.WALL_ENDS_COOLDOWN, java.time.Instant.now(), 0, left)) }) {
                            is DoorDecision.Locked -> doorLock = r.reason
                            is DoorDecision.Open -> doorWall = maxOf(r.wallLength, WallLength.OPEN_BLOCKED_APP)
                        }
                    }
                    if (d is Decision.Allow) { launchApp(pkg); finish() }       // an allowance began in the meantime
                }
                val d = decision
                if (d is Decision.Block) BlockedCard(label, d, uses, onHome = ::goHome, onWallPassed = { len ->
                    scope.launch(Dispatchers.IO) {
                        val now = BlockingState.now()
                        val s = BlockingState.snapshot
                        val repo = BlockingRepo(db)
                        if (d.reason == BlockReason.APP_CAP) app.efa.data.DoorRepo(db).record(DoorUse(Door.WALL_ENDS_COOLDOWN, java.time.Instant.now(), 0, java.time.Duration.between(now, d.until).toMinutes().toInt().coerceAtLeast(1)))
                        if (d.night) app.efa.data.DoorRepo(db).record(gateDoor(Door.OVERRIDE, java.time.Instant.now(), overrideMinutes(BlockingState.fullyVerifiedTonight())))          // DP2: a night override counts in the same pool
                        if (d.night) repo.nightOverride(now, overrideMinutes(BlockingState.fullyVerifiedTonight()), len)
                        else repo.openApp(pkg, now, len, nowAndNext(s?.pattern.orEmpty(), now).now, BlockRepo(applicationContext, db, s?.pattern.orEmpty()))
                        BlockingState.refresh(applicationContext)
                        withContext(Dispatchers.Main) { launchApp(pkg); finishAndRemoveTask() }
                    }
                }, bufferCost = BlockingState.bufferCost(d), doorLock = doorLock, doorWall = doorWall, glassOk = glassOk, onBreakGlass = { len ->
                    scope.launch(Dispatchers.IO) {
                        val now = BlockingState.now(); val s = BlockingState.snapshot; val repo = BlockingRepo(db)
                        app.efa.data.DoorRepo(db).record(DoorUse(Door.BREAK_GLASS, java.time.Instant.now(), 0, 0))          // CE7: once in 30 days, never touches sleep
                        if (d.night) repo.nightOverride(now, overrideMinutes(BlockingState.fullyVerifiedTonight()), len)
                        else repo.openApp(pkg, now, len, nowAndNext(s?.pattern.orEmpty(), now).now, BlockRepo(applicationContext, db, s?.pattern.orEmpty()))
                        BlockingState.refresh(applicationContext)
                        withContext(Dispatchers.Main) { launchApp(pkg); finishAndRemoveTask() }
                    }
                }, onEmergency = { a -> scope.launch(Dispatchers.IO) { Emergency.run(applicationContext, a) } }, onReady = {
                    startActivity(Intent(this@BlockedActivity, MainActivity::class.java).putExtra(BufferNotifier.EXTRA_ACTION, BufferNotifier.ACTION_READY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                })
            }
        }
    }

    private fun launchApp(pkg: String) { packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun BlockedCard(
    appLabel: String, d: Decision.Block, nightUsesTonight: Int, onHome: () -> Unit, onWallPassed: (Int) -> Unit,
    bufferCost: Pair<BufferState, BufferState>?, onReady: () -> Unit = {}, doorLock: String? = null, doorWall: Int? = null, onEmergency: (EmergencyAction) -> Unit = {}, glassOk: Boolean = false, onBreakGlass: (Int) -> Unit = {},
) {
    var glassOpen by remember { mutableStateOf(false) }
    val c = LocalColors.current
    var wallOpen by remember { mutableStateOf(false) }
    val snap = BlockingState.snapshot
    val now = BlockingState.now()
    val wake = snap?.pattern?.let { driftedWake(it, now, snap.lateness) }      // Z3: a late morning moves the target later
    val offer = if (d.night && wake != null) nightOffer(now, wake, nightUsesTonight, BlockingState.fullyVerifiedTonight()) else null
    val len = if (d.night) maxOf(offer?.wallLength ?: WallLength.nightOverride(nightUsesTonight + 1), doorWall ?: 0) else doorWall ?: WallLength.OPEN_BLOCKED_APP
    val canOpen = (!d.night || offer?.allowed == true) && !d.reason.isMorning && doorLock == null
    val target = remember(wallOpen, len) { newWallString(len) }
    val wallRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(wallOpen) { if (wallOpen) { kotlinx.coroutines.delay(150); wallRequester.bringIntoView() } }

    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label("BLOCKED")
        BasicText("$appLabel is blocked", style = Ts(c.text, 28.sp, FontWeight.SemiBold))
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("WHY")
            BasicText(
                refusalLine(d.reason, d.label, HM.format(d.until)),
                style = Ts(c.text, 18.sp),
            )
            if (d.night && wake != null) BasicText(eightHourSum(wake), style = Ts(c.muted, 14.sp))     // N6
        }
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("WHAT OPENING IT COSTS")
            if (d.unprovenMinutes > 0) {
                BasicText("Opening it ends this block as broken. Its ${d.unprovenMinutes} unproven minutes are lost.", style = Ts(c.text, 16.sp))
                val (before, after) = bufferCost ?: (null to null)
                if (before is BufferState.Value && after is BufferState.Value)
                    BasicText("Buffer now ${before.result.label().removePrefix("Buffer ")}. With this block lost: ${after.result.label().removePrefix("Buffer ")}.", style = Ts(c.text, 16.sp, FontWeight.SemiBold))
            } else if (doorLock != null) {
                BasicText(doorLock, style = Ts(c.text, 16.sp))
            } else if (d.night) {
                BasicText(
                    if (canOpen) "An override opens all your blocked apps for ${offer?.minutes ?: 30} minutes. It uses the hard-to-type string below, and the next one tonight is longer."
                    else wake?.let { noOverrideLine(now, it) } ?: "No override is possible now.",
                    style = Ts(c.text, 16.sp),
                )
            } else if (d.reason.isMorning) {
                BasicText("It cannot be opened with a typed string. Calls, messages, maps, music and the Buffer still work.", style = Ts(c.text, 16.sp))
            } else BasicText("Opening it costs no block. It opens for 30 minutes, then blocks again.", style = Ts(c.text, 16.sp))
        }
        if (d.reason == BlockReason.MORNING_READY) Btn("Take the ready photo", onReady, primary = true, modifier = Modifier.fillMaxWidth())
        Btn("Go home", onHome, primary = d.reason != BlockReason.MORNING_READY, modifier = Modifier.fillMaxWidth())
        if (doorLock != null && glassOk && !d.reason.isMorning && (!d.night || offer?.allowed == true)) {
            // CE7: break glass, once in 30 days, the longest string; it never touches sleep
            val glassTarget = remember(glassOpen) { newWallString(BREAK_GLASS_WALL) }
            if (!glassOpen) Btn("Break glass: once in 30 days, type $BREAK_GLASS_WALL characters", { glassOpen = true }, modifier = Modifier.fillMaxWidth())
            else Column(Modifier.fillMaxWidth().card().padding(16.dp)) { WallField(glassTarget) { onBreakGlass(BREAK_GLASS_WALL) } }
        }
        // EM1: the Emergency door is on the blocked card too, never gated
        Label("EMERGENCY")
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(EmergencyAction.CALL_EMERGENCY, EmergencyAction.CALL_ANYONE, EmergencyAction.MAPS).forEach { a -> Chip(a.label, false, { onEmergency(a) }, "Emergency: ${a.label}") }
        }
        if (canOpen) {
            if (!wallOpen) Btn("Open anyway: type $len characters", { wallOpen = true }, modifier = Modifier.fillMaxWidth())
            else Column(Modifier.fillMaxWidth().bringIntoViewRequester(wallRequester).card().padding(16.dp)) { WallField(target) { onWallPassed(len) } }
        }
    }
}
