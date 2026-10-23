package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.BlockRow
import app.efa.data.CaptureFailed
import efa.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** What the block screen needs from storage. Implemented over Room in MainActivity. */
interface BlockActions {
    suspend fun load(ds: DatedSlot): BlockRow?
    suspend fun recordHold(ds: DatedSlot, atMs: Long)
    suspend fun submitPhoto(ds: DatedSlot, jpeg: ByteArray, now: ZonedDateTime, fix: Fix?): Verdict
    /** A code block's proof: asks GitHub for the user's own commits made in the block (G1). */
    suspend fun checkCommit(ds: DatedSlot, now: ZonedDateTime): Verdict
    /** One location read, taken right after the photo (A8). null when there is no permission or no precise fix. */
    suspend fun currentFix(): Fix?
    fun hasLocationPermission(): Boolean
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * A block (W1): the timer runs out, "done" is held for one second, then one proof photo. Nothing here lets a block end early,
 * and there is no review screen after the photo (G5). Each branch says what happens when it cannot finish (X7).
 */
@Composable
fun BlockScreen(ds: DatedSlot, actions: BlockActions, onBack: () -> Unit) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val proof = remember(ds) { proofFor(ds.slot) }
    var now by remember { mutableStateOf(ZonedDateTime.now(ds.start.zone)) }
    var row by remember { mutableStateOf<BlockRow?>(null) }
    var camera by remember { mutableStateOf(false) }
    var askLocation by remember { mutableStateOf(false) }
    var locationDenied by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ds) { row = actions.load(ds) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(ds.start.zone); delay(1000) } }

    if (camera) {
        CameraCapture(
            onJpeg = { jpeg ->
                camera = false
                scope.launch {
                    // Attendance: read the location once, right after the photo (A8).
                    val fix = if (proof == Proof.ATTENDANCE) { reading = true; actions.currentFix().also { reading = false } } else null
                    message = try { actions.submitPhoto(ds, jpeg, ZonedDateTime.now(ds.start.zone), fix).let { v ->
                        if (v.verified) "Verified. ${v.creditedMinutes} min confirmed." + (if (v.recovered) " Recovered time." else "") + (if (v.weaker) " Weaker proof: away from the building but not at home." else "") else v.reason.text()
                    } } catch (e: CaptureFailed) { e.message } catch (e: Exception) { "Saving failed. Nothing was counted. Take the photo again." }
                    row = actions.load(ds)
                }
            },
            onCancel = { camera = false },
        )
        return
    }

    if (askLocation) {
        LocationAsk(denied = locationDenied, onGranted = { askLocation = false; camera = true }, onDenied = { locationDenied = true }, onCancel = { askLocation = false })
        return
    }
    val verified = row?.verified == true
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label(ds.slot.type.uppercase())
        BasicText(ds.slot.title, style = TextStyle(c.text, 26.sp, FontWeight.SemiBold))
        BasicText(ds.window(), style = TextStyle(c.muted, 16.sp, fontFamily = FontFamily.Monospace))

        Column(Modifier.fillMaxWidth().background(c.card, CardShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("PROOF")
            BasicText(proofText(proof), style = TextStyle(c.text, 16.sp))
            when {
                verified -> {
                    val r = row!!
                    BasicText("Verified. ${r.creditedMin} min confirmed." + (if (r.recovered) " Recovered time." else "") + (if (r.weaker) " Weaker proof." else ""),
                        style = TextStyle(c.text, 20.sp, FontWeight.SemiBold))
                }
                proof == Proof.NONE -> {}
                proof == Proof.GIT_COMMIT -> when {
                    now.isBefore(ds.end) -> {
                        val left = Duration.between(now, ds.end)
                        BasicText("Timer %d:%02d:%02d left".format(left.toHours(), left.toMinutesPart(), left.toSecondsPart()), style = TextStyle(c.text, 28.sp, FontWeight.SemiBold, fontFamily = FontFamily.Monospace))
                    }
                    row?.holdAtMs == null -> {
                        BasicText("Timer finished.", style = TextStyle(c.text, 20.sp, FontWeight.SemiBold))
                        HoldButton("Hold to finish") { scope.launch { actions.recordHold(ds, System.currentTimeMillis()); row = actions.load(ds) } }
                    }
                    else -> {
                        BasicText("Done is held. Push your commit, then check.", style = TextStyle(c.text, 20.sp, FontWeight.SemiBold))
                        Btn(if (checking) "Checking GitHub" else "Check commit", {
                            if (!checking) {
                                checking = true
                                scope.launch {
                                    message = try { actions.checkCommit(ds, ZonedDateTime.now(ds.start.zone)).let { v ->
                                        if (v.verified) "Verified. ${v.creditedMinutes} min confirmed." + (if (v.recovered) " Recovered time." else "") else v.reason.text()
                                    } } catch (e: Exception) { "The check failed. Nothing was counted. Try again." }
                                    row = actions.load(ds); checking = false
                                }
                            }
                        }, primary = true, modifier = Modifier.fillMaxWidth())
                    }
                }
                proof == Proof.ATTENDANCE -> when {
                    now.isBefore(ds.start) -> BasicText("Starts at ${HM.format(ds.start)}.", style = TextStyle(c.text, 18.sp))
                    now.isAfter(ds.end) -> BasicText(Reason.OUTSIDE_WINDOW.text(), style = TextStyle(c.text, 16.sp, FontWeight.SemiBold))
                    else -> Btn("Take proof photo", { if (actions.hasLocationPermission()) camera = true else askLocation = true }, primary = true, modifier = Modifier.fillMaxWidth())
                }
                proof == Proof.WORK_PHOTO -> when {
                    now.isBefore(ds.end) -> {
                        val left = Duration.between(now, ds.end)
                        BasicText("Timer %d:%02d:%02d left".format(left.toHours(), left.toMinutesPart(), left.toSecondsPart()), style = TextStyle(c.text, 28.sp, FontWeight.SemiBold, fontFamily = FontFamily.Monospace))
                    }
                    row?.holdAtMs == null -> {
                        BasicText("Timer finished.", style = TextStyle(c.text, 20.sp, FontWeight.SemiBold))
                        HoldButton("Hold to finish") { scope.launch { actions.recordHold(ds, System.currentTimeMillis()); row = actions.load(ds) } }
                    }
                    else -> {
                        BasicText("Done is held.", style = TextStyle(c.text, 20.sp, FontWeight.SemiBold))
                        Btn("Take proof photo", { camera = true }, primary = true, modifier = Modifier.fillMaxWidth())
                    }
                }
                proof == Proof.WINDOW_PHOTO -> when {
                    now.isBefore(ds.start) -> BasicText("Starts at ${HM.format(ds.start)}.", style = TextStyle(c.text, 18.sp))
                    now.isAfter(ds.end) -> BasicText(Reason.OUTSIDE_WINDOW.text(), style = TextStyle(c.text, 16.sp, FontWeight.SemiBold))
                    else -> Btn("Take proof photo", { camera = true }, primary = true, modifier = Modifier.fillMaxWidth())
                }
            }
            if (reading) BasicText("Reading location.", style = TextStyle(c.text, 16.sp))
            message?.let { BasicText(it, style = TextStyle(c.text, 16.sp, FontWeight.SemiBold), modifier = Modifier.semantics { contentDescription = "Result. $it" }) }
        }
        Spacer(Modifier.weight(1f))
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

private fun proofText(p: Proof) = when (p) {
    Proof.NONE -> "This block needs no photo."
    Proof.WORK_PHOTO -> "When the timer ends: hold done for one second, then take one photo of your notes or worked solutions."
    Proof.WINDOW_PHOTO -> "One photo taken inside this block's time window."
    Proof.ATTENDANCE -> "One photo inside the lecture window. Your location is read once, when you take it: within about 150 m of the building passes, or at least 250 m from home counts as weaker proof."
    Proof.GIT_COMMIT -> "When the timer ends: hold done for one second, then check. One of your own commits made during this block, pushed to a public repo set in Setup, proves it. One commit proves one block."
}

/** Hold for one second without letting go (W1). Completes the moment the second is reached; letting go early restarts it. */
@Composable
private fun HoldButton(text: String, onDone: () -> Unit) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    val tracker = remember { HoldTracker() }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).background(c.track, CardShape)
            .semantics { contentDescription = "$text. Press and hold for one second." }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    // Frame time is monotonic on a phone and controllable in tests. The hold completes the instant one second is reached.
                    val ticker = scope.launch {
                        var started = false
                        while (!tracker.completed) {
                            withFrameMillis { ms ->
                                if (!started) { tracker.down(ms); started = true }
                                progress = tracker.progress(ms).toFloat()
                                if (progress >= 1f) { tracker.up(ms); onDone() }
                            }
                        }
                    }
                    tryAwaitRelease()
                    if (!tracker.completed) { tracker.cancel(); ticker.cancel(); progress = 0f }
                })
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).heightIn(min = 64.dp).background(c.accent, CardShape))
        BasicText(text, style = TextStyle(c.text, 18.sp, FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 18.dp))
    }
}

/** A9: location permission, asked in the app with its reason, only for attendance. */
@Composable
private fun LocationAsk(denied: Boolean, onGranted: () -> Unit, onDenied: () -> Unit, onCancel: () -> Unit) {
    val c = LocalColors.current
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) onGranted() else onDenied()
    }
    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BasicText("Location", style = TextStyle(c.text, 28.sp, FontWeight.SemiBold))
        BasicText("Location is read once, when you take an attendance photo, to check you are at the lecture. It is not tracked, not stored and not sent anywhere. Only a distance band such as under 150 m is kept.", style = TextStyle(c.text, 16.sp))
        if (denied) BasicText("Location permission is off, so attendance cannot be verified. Allow precise location in Android settings, then try again.", style = TextStyle(c.text, 16.sp, FontWeight.SemiBold))
        Btn("Allow location", { ask.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) }, primary = true, modifier = Modifier.fillMaxWidth())
        Btn("Back", onCancel, modifier = Modifier.fillMaxWidth())
    }
}
