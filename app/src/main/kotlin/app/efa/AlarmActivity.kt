package app.efa

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import app.efa.data.EfaDb
import app.efa.data.MorningRepo
import app.efa.data.MorningRow
import app.efa.data.RefKind
import app.efa.data.ReferenceRepo
import efa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The alarm screen over the lock screen (M5). It cannot be left with Back. It stops the alarm only through the photo, the steps or the wait
 * (M2, M10), and every stop is recorded with how it happened so a weaker one shows as weaker. Failure behaviour (X7): if the database cannot be
 * read the alarm keeps ringing and the screen shows what it can; the person is never locked out of calls or messages.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val db = EfaDb.get(this)
        setContent {
            EfaTheme {
                val scope = rememberCoroutineScope()
                var row by remember { mutableStateOf<MorningRow?>(null) }
                var camera by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf<String?>(null) }
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var checkedReference by remember { mutableStateOf(false) }
                val repo = remember { MorningRepo(db) }
                BackHandler(true) { /* the alarm is left only by stopping it */ }

                LaunchedEffect(Unit) {
                    while (true) {
                        now = System.currentTimeMillis()
                        val r = withContext(Dispatchers.IO) { try { repo.latest() } catch (e: Exception) { null } }
                        if (r != null) row = r
                        if (r == null || r.ringStartedMs == null || r.stoppedMs != null) { finish(); return@LaunchedEffect }
                        if (!checkedReference) {
                            checkedReference = true
                            val refs = withContext(Dispatchers.IO) { ReferenceRepo(applicationContext, db).valid(listOf(RefKind.NIGHT, RefKind.STANDING), now) }
                            if (refs.isEmpty()) { withContext(Dispatchers.IO) { repo.skipToSteps(LocalDate.parse(r.date)) }; message = "No reference photo is set for today, so the photo cannot be checked. Steps instead." }
                        }
                        delay(1000)
                    }
                }

                val r = row
                if (r != null) {
                    val date = LocalDate.parse(r.date)
                    val stepsOk = AlarmSession.stepsAvailable(applicationContext)
                    val steps = maxOf(r.steps, AlarmSession.steps)
                    val offer = nextOffer(r.failedPhotos, false, steps, stepsOk, r.waitStartedMs, now)
                    LaunchedEffect(offer is Offer.Wait) { if (offer is Offer.Wait && r.waitStartedMs == null) withContext(Dispatchers.IO) { repo.startWait(date, System.currentTimeMillis()) } }
                    if (camera) CameraCapture(
                        onJpeg = { jpeg ->
                            camera = false
                            scope.launch {
                                val m = withContext(Dispatchers.IO) { ReferenceRepo(applicationContext, db).match(listOf(RefKind.NIGHT, RefKind.STANDING), jpeg, System.currentTimeMillis()) }
                                if (m.result.passed) stopAlarm(repo, date, StopMethod.PHOTO, m.result.score)
                                else {
                                    withContext(Dispatchers.IO) { repo.failedPhoto(date, m.result.score) }
                                    message = if (m.result.tooDark) "That photo is too dark to check. Turn a light on." else "That does not match your setup (score ${m.result.score}, it needs ${MATCH_PASS_SCORE})."
                                }
                            }
                        },
                        onCancel = { camera = false },
                    ) else AlarmScreen(
                        AlarmUi(DateTimeFormatter.ofPattern("HH:mm").format(java.time.Instant.ofEpochMilli(now).atZone(LONDON)), offer, message,
                            (offer as? Offer.Wait)?.let { ((it.untilMs - now) / 1000).toInt().coerceAtLeast(0) }),
                        onTakePhoto = { camera = true },
                        onCameraBroken = { scope.launch { withContext(Dispatchers.IO) { repo.skipToSteps(date) }; message = "The camera is not working, so the photo is skipped." } },
                        onStop = { method -> scope.launch { stopAlarm(repo, date, method, r.bestScore) } },
                    )
                }
            }
        }
    }

    /** M2: the alarm stops and the next one is set. The social block (M6) starts here, ending only with the ready photo (M8). */
    private suspend fun stopAlarm(repo: MorningRepo, date: LocalDate, method: StopMethod, score: Int) {
        withContext(Dispatchers.IO) {
            repo.stop(date, method, score, System.currentTimeMillis())
            AlarmService.stop(applicationContext)
            BlockingState.refresh(applicationContext)
            AlarmScheduler.refresh(applicationContext)
        }
        finish()
    }
}
