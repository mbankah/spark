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
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) { BlockingState.refresh(applicationContext) }
                    val d = BlockingState.decide(pkg)
                    decision = d
                    val s = BlockingState.snapshot
                    if (d is Decision.Block && d.night && s != null) {
                        val w = nightWindow(s.pattern, BlockingState.now())
                        if (w != null) uses = withContext(Dispatchers.IO) { BlockingRepo(db).nightUsesSince(w.cutoff) }
                    }
                    if (d is Decision.Allow) { launchApp(pkg); finish() }       // an allowance began in the meantime
                }
                val d = decision
                if (d is Decision.Block) BlockedCard(label, d, uses, onHome = ::goHome, onWallPassed = { len ->
                    scope.launch(Dispatchers.IO) {
                        val now = BlockingState.now()
                        val s = BlockingState.snapshot
                        val repo = BlockingRepo(db)
                        if (d.night) repo.nightOverride(now, overrideMinutes(fullyVerifiedDay = false), len)
                        else repo.openApp(pkg, now, len, nowAndNext(s?.pattern.orEmpty(), now).now, BlockRepo(applicationContext, db, s?.pattern.orEmpty()))
                        BlockingState.refresh(applicationContext)
                        withContext(Dispatchers.Main) { launchApp(pkg); finishAndRemoveTask() }
                    }
                }, bufferCost = BlockingState.bufferCost(d))
            }
        }
    }

    private fun launchApp(pkg: String) { packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun BlockedCard(
    appLabel: String, d: Decision.Block, nightUsesTonight: Int, onHome: () -> Unit, onWallPassed: (Int) -> Unit,
    bufferCost: Pair<BufferState, BufferState>?,
) {
    val c = LocalColors.current
    var wallOpen by remember { mutableStateOf(false) }
    val snap = BlockingState.snapshot
    val now = BlockingState.now()
    val wake = snap?.pattern?.let { nextWake(it, now) }
    val offer = if (d.night && wake != null) nightOffer(now, wake, nightUsesTonight, fullyVerifiedDay = false) else null
    val len = if (d.night) offer?.wallLength ?: WallLength.nightOverride(nightUsesTonight + 1) else WallLength.OPEN_BLOCKED_APP
    val canOpen = !d.night || offer?.allowed == true
    val target = remember(wallOpen, len) { newWallString(len) }
    val wallRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(wallOpen) { if (wallOpen) { kotlinx.coroutines.delay(150); wallRequester.bringIntoView() } }

    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label("BLOCKED")
        BasicText("$appLabel is blocked", style = TextStyle(c.text, 28.sp, FontWeight.SemiBold))
        Column(Modifier.fillMaxWidth().background(c.card, CardShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("WHY")
            BasicText(
                when (d.reason) {
                    BlockReason.WORK_BLOCK -> "Work block: ${d.label}, until ${HM.format(d.until)}."
                    BlockReason.GETTING_READY -> "Getting ready: ${d.label}, until ${HM.format(d.until)}."
                    BlockReason.TRANSITION -> "Transition: ${d.label}, until ${HM.format(d.until)}."
                    BlockReason.PLANNING -> "Planning block: ${d.label}, until ${HM.format(d.until)}."
                    BlockReason.NIGHT -> "Past the night cutoff. It lifts at your wake time, ${HM.format(d.until)}."
                },
                style = TextStyle(c.text, 18.sp),
            )
            if (d.night && wake != null) BasicText(eightHourSum(wake), style = TextStyle(c.muted, 14.sp))     // N6
        }
        Column(Modifier.fillMaxWidth().background(c.card, CardShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("WHAT OPENING IT COSTS")
            if (d.unprovenMinutes > 0) {
                BasicText("Opening it ends this block as broken. Its ${d.unprovenMinutes} unproven minutes are lost.", style = TextStyle(c.text, 16.sp))
                val (before, after) = bufferCost ?: (null to null)
                if (before is BufferState.Value && after is BufferState.Value)
                    BasicText("Buffer now ${before.result.label().removePrefix("Buffer ")}. With this block lost: ${after.result.label().removePrefix("Buffer ")}.", style = TextStyle(c.text, 16.sp, FontWeight.SemiBold))
            } else if (d.night) {
                BasicText(
                    if (canOpen) "An override opens all your blocked apps for ${offer?.minutes ?: 30} minutes. It uses the hard-to-type string below, and the next one tonight is longer."
                    else "No override is possible now: it is within 6 hours of your wake time.",
                    style = TextStyle(c.text, 16.sp),
                )
            } else BasicText("Opening it costs no block. It opens for 30 minutes, then blocks again.", style = TextStyle(c.text, 16.sp))
        }
        Btn("Go home", onHome, primary = true, modifier = Modifier.fillMaxWidth())
        if (canOpen) {
            if (!wallOpen) Btn("Open anyway: type $len characters", { wallOpen = true }, modifier = Modifier.fillMaxWidth())
            else Column(Modifier.fillMaxWidth().bringIntoViewRequester(wallRequester).background(c.card, CardShape).padding(16.dp)) { WallField(target) { onWallPassed(len) } }
        }
    }
}
