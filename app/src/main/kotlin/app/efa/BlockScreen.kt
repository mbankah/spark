package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    /** R1, R2, R3: what a verified block bought: what it kept out of the Buffer, the open phone it earned, and the reward that is next. */
    suspend fun rewardLines(ds: DatedSlot, creditedMin: Int): List<String> = emptyList()
    /** F2, F4: why free time became this work block, or null when it did not. */
    suspend fun conversionNote(ds: DatedSlot): String? = null
    /** ST2: what happened to a block with no proof. */
    suspend fun runRecord(ds: DatedSlot): RunRecord? = null
    suspend fun runStart(ds: DatedSlot, nowMs: Long) {}
    suspend fun runFinish(ds: DatedSlot, nowMs: Long) {}
    suspend fun runSkip(ds: DatedSlot, reason: String) {}
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * A block (W1): the timer runs out, "done" is held for one second, then one proof photo. Nothing here lets a block end early,
 * and there is no review screen after the photo (G5). Each branch says what happens when it cannot finish (X7).
 */
@Composable
fun BlockScreen(ds: DatedSlot, actions: BlockActions, onTurnBack: (DatedSlot) -> Unit = {}, onStartRoutine: (String) -> Unit = {}, onShift: (Int) -> Unit = {}, onCameUp: () -> Unit = {}, onBack: () -> Unit) {
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
    var run by remember { mutableStateOf<RunRecord?>(null) }
    LaunchedEffect(ds) { run = actions.runRecord(ds) }
    var rewards by remember { mutableStateOf<List<String>>(emptyList()) }
    var convNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ds) { convNote = actions.conversionNote(ds) }
    LaunchedEffect(row?.verified) { val r = row; if (r?.verified == true) rewards = actions.rewardLines(ds, r.creditedMin) }
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
    // ST2: the screen scrolls, so the run card and its answers can always be reached
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label(ds.slot.type.uppercase())
        BasicText(ds.slot.title, style = Ts(c.text, 26.sp, FontWeight.SemiBold))
        BasicText(ds.window(), style = Ts(c.muted, 16.sp, fontFamily = Mono))
        convNote?.let { note ->
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("WHY THIS IS WORK")
                BasicText(note, style = Ts(c.text, 15.sp))
                if (!verified && now.isBefore(ds.end)) Btn("Make it a break: type 75 characters", { onTurnBack(ds) }, modifier = Modifier.fillMaxWidth())
            }
        }

        // ST2: a block with no proof is started with a hold, finished with a shorter one, and says what happened when it did not go to plan
        if (isRunnable(ds.slot)) RunCard(
            ds, run, now,
            onStart = { scope.launch { actions.runStart(ds, System.currentTimeMillis()); run = actions.runRecord(ds) } },
            onFinish = { scope.launch { actions.runFinish(ds, System.currentTimeMillis()); run = actions.runRecord(ds) } },
            onSkip = { reason -> scope.launch { actions.runSkip(ds, reason); run = actions.runRecord(ds); if (reason == "Something came up") onCameUp() } },
            onShift = onShift,
        )
        // ST1: a block that is a routine (the wake, the Bible, getting ready, the wind-down, a study block) can be started from here: the runner opens on that routine, one step at a time
        val routine = remember(ds) { routineForSlot(ds.slot) }
        if (routine != null && !verified) Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("START")
            BasicText("${routine.title}, one step at a time.", style = Ts(c.muted, 15.sp))
            Btn("Start ${routine.title.lowercase()}", { onStartRoutine(routine.id) }, primary = proof == Proof.NONE, modifier = Modifier.fillMaxWidth(), description = "Start ${routine.title}, one step at a time")
        }
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("PROOF")
            BasicText(proofText(proof), style = Ts(c.text, 16.sp))
            when {
                verified -> {
                    val r = row!!
                    BasicText("Verified. ${r.creditedMin} min confirmed." + (if (r.recovered) " Recovered time." else "") + (if (r.weaker) " Weaker proof." else ""),
                        style = Ts(c.text, 20.sp, FontWeight.SemiBold))
                    rewards.forEach { BasicText(it, style = Ts(c.muted, 15.sp)) }
                }
                proof == Proof.NONE -> {}
                proof == Proof.GIT_COMMIT -> when {
                    now.isBefore(ds.end) -> {
                        val left = Duration.between(now, ds.end)
                        BasicText("Timer %d:%02d:%02d left".format(left.toHours(), left.toMinutesPart(), left.toSecondsPart()), style = Ts(c.text, 28.sp, FontWeight.SemiBold, fontFamily = Mono))
                    }
                    row?.holdAtMs == null -> {
                        BasicText("Timer finished.", style = Ts(c.text, 20.sp, FontWeight.SemiBold))
                        HoldButton("Hold to finish") { scope.launch { actions.recordHold(ds, System.currentTimeMillis()); row = actions.load(ds) } }
                    }
                    else -> {
                        BasicText("Done is held. Push your commit, then check.", style = Ts(c.text, 20.sp, FontWeight.SemiBold))
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
                    now.isBefore(ds.start) -> BasicText("Starts at ${HM.format(ds.start)}.", style = Ts(c.text, 18.sp))
                    now.isAfter(ds.end) -> BasicText(Reason.OUTSIDE_WINDOW.text(), style = Ts(c.text, 16.sp, FontWeight.SemiBold))
                    else -> Btn("Take proof photo", { if (actions.hasLocationPermission()) camera = true else askLocation = true }, primary = true, modifier = Modifier.fillMaxWidth())
                }
                proof == Proof.WORK_PHOTO -> when {
                    now.isBefore(ds.end) -> {
                        val left = Duration.between(now, ds.end)
                        BasicText("Timer %d:%02d:%02d left".format(left.toHours(), left.toMinutesPart(), left.toSecondsPart()), style = Ts(c.text, 28.sp, FontWeight.SemiBold, fontFamily = Mono))
                    }
                    row?.holdAtMs == null -> {
                        BasicText("Timer finished.", style = Ts(c.text, 20.sp, FontWeight.SemiBold))
                        HoldButton("Hold to finish") { scope.launch { actions.recordHold(ds, System.currentTimeMillis()); row = actions.load(ds) } }
                    }
                    else -> {
                        BasicText("Done is held.", style = Ts(c.text, 20.sp, FontWeight.SemiBold))
                        Btn("Take proof photo", { camera = true }, primary = true, modifier = Modifier.fillMaxWidth())
                    }
                }
                proof == Proof.WINDOW_PHOTO -> when {
                    now.isBefore(ds.start) -> BasicText("Starts at ${HM.format(ds.start)}.", style = Ts(c.text, 18.sp))
                    now.isAfter(ds.end) -> BasicText(Reason.OUTSIDE_WINDOW.text(), style = Ts(c.text, 16.sp, FontWeight.SemiBold))
                    else -> Btn("Take proof photo", { camera = true }, primary = true, modifier = Modifier.fillMaxWidth())
                }
            }
            if (reading) BasicText("Reading location.", style = Ts(c.text, 16.sp))
            message?.let { BasicText(it, style = Ts(c.text, 16.sp, FontWeight.SemiBold), modifier = Modifier.semantics { contentDescription = "Result. $it" }) }
        }
        Spacer(Modifier.height(12.dp))
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
private fun HoldButton(text: String, requiredMs: Long = 1000, onDone: () -> Unit) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    val tracker = remember(requiredMs) { HoldTracker(requiredMs) }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).background(c.track, CardShape)
            .semantics { contentDescription = "$text. Press and hold for ${if (requiredMs <= 1000L) "one second" else "${(requiredMs + 500) / 1000} seconds"}." }
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
        BasicText(text, style = Ts(c.text, 18.sp, FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 18.dp))
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
        BasicText("Location", style = Ts(c.text, 28.sp, FontWeight.SemiBold))
        BasicText("Location is read once, when you take an attendance photo, to check you are at the lecture. It is not tracked, not stored and not sent anywhere. Only a distance band such as under 150 m is kept.", style = Ts(c.text, 16.sp))
        if (denied) BasicText("Location permission is off, so attendance cannot be verified. Allow precise location in Android settings, then try again.", style = Ts(c.text, 16.sp, FontWeight.SemiBold))
        Btn("Allow location", { ask.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) }, primary = true, modifier = Modifier.fillMaxWidth())
        Btn("Back", onCancel, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * ST2: the run card of a block with no proof. Not started: a hold that fills over [holdMillis] seconds, and a way to say it did not happen. Started: how far through it is, a short hold to
 * finish, and more time when it is over. Late and over are said in minutes and lead to the same Running over preview, so the rest of the day is never silently wrong.
 */
@Composable
private fun RunCard(ds: DatedSlot, rec: RunRecord?, now: ZonedDateTime, onStart: () -> Unit, onFinish: () -> Unit, onSkip: (String) -> Unit, onShift: (Int) -> Unit) {
    val c = LocalColors.current
    val v = runView(rec, ds.start.toInstant().toEpochMilli(), ds.end.toInstant().toEpochMilli(), now.toInstant().toEpochMilli(), now.zone)
    val minutes = Duration.between(ds.start, ds.end).toMinutes().toInt()
    var reasons by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("RUN")
        BasicText(v.line, style = Ts(c.text, 17.sp, FontWeight.SemiBold), modifier = Modifier.semantics { contentDescription = v.line })
        when (v.status) {
            RunStatus.NOT_STARTED -> {
                HoldButton("Hold to start", holdMillis(minutes), onStart)
                if (!reasons) Btn("It did not happen", { reasons = true }, modifier = Modifier.fillMaxWidth())
                else {
                    BasicText("What happened?", style = Ts(c.muted, 14.sp))
                    listOf("Too tired", "Ran out of time", "Something came up", "Other").forEach { r -> Btn(r, { onSkip(r) }, modifier = Modifier.fillMaxWidth()) }
                }
            }
            RunStatus.RUNNING -> {
                Box(Modifier.fillMaxWidth().height(10.dp).background(c.track, CardShape)) { Box(Modifier.fillMaxWidth(v.fraction).height(10.dp).background(c.accent, CardShape)) }
                if (v.lateMin >= RUN_LATE_MIN) Btn("Started late: slide the rest of today by ${shiftMinutes(v.lateMin)} min", { onShift(shiftMinutes(v.lateMin)) }, modifier = Modifier.fillMaxWidth())
                if (v.overMin >= RUN_LATE_MIN) Btn("Over by ${v.overMin} min: slide the rest of today by ${shiftMinutes(v.overMin)} min", { onShift(shiftMinutes(v.overMin)) }, modifier = Modifier.fillMaxWidth())
                else if (!now.isBefore(ds.end.minusMinutes(2))) {
                    BasicText("Need more time?", style = Ts(c.muted, 14.sp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(10, 20).forEach { m -> Btn("+$m min", { onShift(m) }, modifier = Modifier.weight(1f)) } }
                }
                HoldButton("Hold to finish", HOLD_END_MS, onFinish)
            }
            RunStatus.DONE -> if (v.overMin >= RUN_LATE_MIN && now.isBefore(ds.end.plusMinutes(60))) Btn("Went over by ${v.overMin} min: slide the rest of today by ${shiftMinutes(v.overMin)} min", { onShift(shiftMinutes(v.overMin)) }, modifier = Modifier.fillMaxWidth())
            RunStatus.SKIPPED -> {}
        }
    }
}
