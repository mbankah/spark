package app.efa

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import app.efa.data.BlockRepo
import app.efa.data.BlockRow
import app.efa.data.BlockingRepo
import app.efa.data.EfaDb
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.efa.data.GitConfigRepo
import app.efa.data.HeldRepo
import app.efa.data.MetaRow
import app.efa.data.TamperRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.efa.data.LocationFix
import app.efa.data.PlaceRepo
import app.efa.data.VenueStatus
import app.efa.data.TaskRow
import app.efa.data.ImportLogRow
import app.efa.data.PlanRepo
import app.efa.data.SleepRepo
import app.efa.data.MorningRepo
import app.efa.data.META_FIRST_USE
import app.efa.data.RewardsRepo
import app.efa.data.uses
import app.efa.data.CalibrationRepo
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import efa.LauncherLook
import efa.TookReport
import efa.WallLength
import app.efa.data.libraryHours
import app.efa.data.saveLibraryHours
import app.efa.data.addLibraryPeriod
import app.efa.data.removeLibraryPeriod
import app.efa.data.addDaysOff
import java.time.LocalDate
import java.time.LocalTime
import app.efa.data.MoneyView
import app.efa.data.turnBack
import app.efa.data.MorningRow
import app.efa.data.toState
import app.efa.data.ReferenceRepo
import app.efa.data.RefKind
import app.efa.data.RefResult
import app.efa.data.NIGHT_REFERENCE_VALID
import app.efa.data.PlannedPeriodRow
import app.efa.data.activePeriod
import app.efa.data.toOpen
import app.efa.data.seedIfEmpty
import efa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val CANVAS_PACKAGE = "com.instructure.candroid"
private enum class Screen { HOME, CHANGE, WORKING, QUICK_ADD, BLOCK, SETUP, MAP_HOME, MAP_VENUE, LIST_GATE, BLOCKLIST, DIGEST, SEARCH, IMPORT, IMPORT_WALL, MORNING, MORNING_CAMERA, READY, READY_CAMERA, MONEY, GATE, DAYOFF, LIBRARY, REVIEW, SHOPPING, WIDGETS, PC, MODIFY, MOD_FORM, ADMIN, ESSENTIALS, CAME_UP, WALKTHROUGH }

private val CAMPUS_CENTRE = Pair(52.4508, -1.9305)   // Edgbaston campus: where map pickers open before anything is set

/** Everything the screens need, loaded from the database. null pattern means the database failed (X7). */
private class Loaded(val pattern: List<PatternSlot>?, val tasks: List<TaskRow>, val verified: Set<String> = emptySet(), val periods: List<PlannedPeriodRow> = emptyList(), val moved: List<String> = emptyList(), val sleep: SleepStatus = SleepStatus(),
    val shortSleep: Boolean = false, val money: MoneyView? = null, val earnings: List<Earning> = emptyList(), val runs: Map<String, RunRecord> = emptyMap(), val vrActive: VrActive? = null)

class MainActivity : ComponentActivity() {
    /** An action asked for by the Buffer notification (LA11), consumed by the screen state once. */
    private val pendingAction = mutableStateOf<String?>(null)

    /** V1: the text the phone's speech recogniser heard, read once by the screen state. */
    private val voiceHeard = mutableStateOf<String?>(null)
    private val voiceLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        voiceHeard.value = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
    }
    private val voiceMessage = mutableStateOf<String?>(null)

    /** S6: the person allows Spark to read sleep from Health Connect. Nothing is asked unless they press the button. */
    private val healthChanged = mutableStateOf(0)
    private val healthPermission = registerForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { healthChanged.value++ }

    /** V1, V2: opens the phone's own speech recogniser, on the device where it can. If there is none, say so: typed quick-add is always there (G7). */
    private fun startVoice(vocabulary: List<String>) {
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
            .putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say it, like: assignment due Friday, heavy")
        if (android.os.Build.VERSION.SDK_INT >= 33) intent.putStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(vocabulary.take(100)))
        try { voiceLauncher.launch(intent) } catch (e: Exception) { voiceMessage.value = "This phone has no speech recogniser. Use the plus button to type it." }
    }

    /** IM10: a timetable file that arrived by Open with or Share, read once and then only previewed (IM5). */
    private val incomingIntent = mutableStateOf<Intent?>(null)

    /** The home button was pressed: the home screen returns to its first page. */
    private val homeTick = mutableIntStateOf(0)

    override fun onResume() { super.onResume(); Nfc.start(this) }          // SL2: only while Spark is open and the option is on
    override fun onPause() { Nfc.stop(this); super.onPause() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTick.intValue++
        pendingAction.value = intent.getStringExtra(BufferNotifier.EXTRA_ACTION)
        if (ImportSource.isImportIntent(intent)) incomingIntent.value = intent
    }

    /** U6: the essentials are one tap from home. Each opens whatever app the phone uses for it; if there is none, nothing happens. */
    private fun launchEssential(key: String) {
        val intent = when (key) {
            "phone" -> Intent(Intent.ACTION_DIAL)
            "messages" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
            "maps" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)
            else -> return
        }
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { /* no such app: stay on home */ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()   // status bar icons follow the system light/dark setting; revisit when mode can differ from the system
        pendingAction.value = intent?.getStringExtra(BufferNotifier.EXTRA_ACTION)
        if (savedInstanceState == null && ImportSource.isImportIntent(intent)) incomingIntent.value = intent
        val db = EfaDb.get(this)
        setContent {
            // AP2 to AP5, AP7: the mode applies at once and moves nothing. Auto is Night from the cutoff to the stopped alarm, otherwise Light by day and Dark in the evening.
            var appearance by remember { mutableStateOf(AppearanceMode.AUTO) }
            var inNight by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                while (true) {
                    withContext(Dispatchers.IO) {
                        appearance = runCatching { AppearanceMode.valueOf(db.meta().get(app.efa.data.META_APPEARANCE).orEmpty()) }.getOrDefault(AppearanceMode.AUTO)
                        val s = BlockingState.snapshot; val now = BlockingState.now()
                        inNight = if (s == null) false else inNightMode(now, nightWindow(s.pattern, now, lateness = s.lateness),
                            app.efa.data.ReferenceRepo(applicationContext, db).status()[app.efa.data.RefKind.NIGHT]?.takenAtMs, s.morning?.stoppedMs)
                    }
                    kotlinx.coroutines.delay(30_000)
                }
            }
            val effective = if (appearance == AppearanceMode.AUTO) autoMode(ZonedDateTime.now(LONDON), inNight) else appearance
            LaunchedEffect(effective) {                                                      // AP3: Night is dim
                val lp = window.attributes; lp.screenBrightness = if (effective == AppearanceMode.NIGHT) NIGHT_BRIGHTNESS_CAP else android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; window.attributes = lp
            }
            EfaTheme(when (effective) { AppearanceMode.LIGHT -> Mode.LIGHT; AppearanceMode.DARK -> Mode.DARK; AppearanceMode.NIGHT -> Mode.NIGHT; AppearanceMode.COLOURFUL -> Mode.COLOURFUL; AppearanceMode.AUTO -> Mode.AUTO }) {
                var screen by remember { mutableStateOf(Screen.HOME) }
                var reload by remember { mutableIntStateOf(0) }
                var loaded by remember { mutableStateOf<Loaded?>(null) }
                var places by remember { mutableStateOf(PlacesState(null, emptyList())) }
                var blockList by remember { mutableStateOf<Set<String>?>(null) }
                var serviceOn by remember { mutableStateOf(isBlockerEnabled(applicationContext)) }
                var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
                var mapVenue by remember { mutableStateOf<VenueStatus?>(null) }
                var newRoom by remember { mutableStateOf<VenueStatus?>(null) }
                var venueBack by remember { mutableStateOf(Screen.SETUP) }
                LaunchedEffect(screen, reload) {                                                   // L6: asks for the location of a room that changed
                    newRoom = withContext(Dispatchers.IO) { runCatching { PlaceRepo(db).unplaced(PlanRepo(db).slots(), ZonedDateTime.now(LONDON).toLocalDate()) }.getOrNull() }
                }
                var selected by remember { mutableStateOf<DatedSlot?>(null) }
                var clock by remember { mutableStateOf(ZonedDateTime.now(LONDON)) }
                var heldWaiting by remember { mutableStateOf(false) }
                var digestState by remember { mutableStateOf(Digest(emptyList(), null)) }
                var listenerOn by remember { mutableStateOf(isNotificationAccessOn(applicationContext)) }
                var noticeOn by remember { mutableStateOf(BufferNotifier.allowed(applicationContext)) }
                var doneSteps by remember { mutableStateOf(SETUP_ORDER.toSet()) }
                var setupLoaded by remember { mutableStateOf(false) }
                var everDone by remember { mutableStateOf(false) }
                var launcherPaused by remember { mutableStateOf(CrashGuard.handedOverAt(applicationContext) != null) }
                val scope = rememberCoroutineScope()
                var importUi by remember { mutableStateOf<ImportUi>(ImportUi.Idle) }
                var imports by remember { mutableStateOf<List<ImportLogRow>>(emptyList()) }
                var wall by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
                var wallBack by remember { mutableStateOf(Screen.HOME) }
                var wallLength by remember { mutableIntStateOf(efa.WallLength.IMPORT_LOOSEN) }
                var confirms by remember { mutableStateOf<List<efa.Confirm>>(emptyList()) }                // CF1: the questions waiting for an answer
                var vrLive by remember { mutableStateOf<VrActive?>(null) }                                  // VR14: the running cycle, read each second and set at once after a control
                var vrCfg by remember { mutableStateOf(efa.VrConfig()) }                                  // VR7: the study cycle's settings, read when Setup opens
                var wallDetails by remember { mutableStateOf<List<String>>(emptyList()) }          // IM13: the long list behind a wall folds on the wall screen; the words above the field stay short
                var dayOffUi by remember { mutableStateOf<DayOffUi?>(null) }
                var libraryUi by remember { mutableStateOf<LibraryUi?>(null) }
                var review by remember { mutableStateOf<ReviewTask?>(null) }
                var look by remember { mutableStateOf(LauncherLook()) }
                LaunchedEffect(screen, reload) { look = LauncherLook.decode(withContext(Dispatchers.IO) { db.meta().get(app.efa.data.META_LOOK) }) }
                // Modify Schedule: the list, a preset's form, the Admin library and the essentials.
                var wakeUi by remember { mutableStateOf(WakeUi()) }                                   // WT1
                var wakeMessage by remember { mutableStateOf<String?>(null) }
                var modRows by remember { mutableStateOf<List<app.efa.data.ModifierRow>>(emptyList()) }
                var modDropped by remember { mutableStateOf(0) }
                var modDraft by remember { mutableStateOf<Instance?>(null) }
                var modPreview by remember { mutableStateOf<ModPreview?>(null) }
                var modRec by remember { mutableStateOf<Map<String, Rec>>(emptyMap()) }
                var modMessage by remember { mutableStateOf<String?>(null) }
                var adminState by remember { mutableStateOf(AdminState()) }
                var adminProblems by remember { mutableStateOf<List<String>>(emptyList()) }
                var essUi by remember { mutableStateOf(EssentialsUi(emptyList(), 0, 0, emptyList())) }
                LaunchedEffect(screen, reload) {
                    if (screen == Screen.CHANGE) withContext(Dispatchers.IO) {                                  // WT1: what the wake time card says
                        val now = BlockingState.now()
                        val plan = try { AlarmScheduler.planNow(applicationContext, now) } catch (e: Exception) { null }
                        val chosen = try { AlarmScheduler.chosenWake(applicationContext, now) } catch (e: Exception) { null }
                        val why = when (plan?.reason) { AlarmReason.CHOSEN -> "the time you chose"; AlarmReason.ONSET -> "about 8 hours after you fell asleep"; AlarmReason.HARD -> "30 minutes before a hard commitment"; AlarmReason.BACKSTOP -> "the 12:00 latest alarm"; null -> "" }
                        val start = (plan?.at ?: now.toLocalDate().atTime(10, 30).atZone(now.zone)).let { (it.hour * 60 + it.minute).let { m -> (m + 7) / 15 * 15 } }
                        wakeUi = WakeUi(if (plan == null) "No alarm: that day is a planned day off." else "Next alarm %02d:%02d, %s.".format(plan.at.hour, plan.at.minute, why), chosen != null, (start / 60) % 24, start % 60)
                    }
                    if (screen == Screen.MODIFY) { modRows = withContext(Dispatchers.IO) { app.efa.data.ModifierRepo(db).all() }; modDropped = withContext(Dispatchers.IO) { PlanRepo(db).droppedByChanges() } }
                    if (screen == Screen.ADMIN) withContext(Dispatchers.IO) {
                        adminState = AdminState.decode(db.meta().get(app.efa.data.META_ADMIN))
                        val plan = PlanRepo(db).slots()
                        adminProblems = placeAdmin(plan.filter { !it.notes.startsWith("Admin:") }, adminState, ZonedDateTime.now(LONDON).toLocalDate(), 14).problems
                    }
                    if (screen == Screen.ESSENTIALS) withContext(Dispatchers.IO) {
                        val er = EssentialsRepo(applicationContext, db); val now = System.currentTimeMillis(); val w = er.window(); val all = installedApps(applicationContext)
                        val ess = er.resolve()
                        essUi = EssentialsUi(all.filter { it.pkg in er.extra() }, w.remainingMs(now), w.added, all.filter { it.pkg !in ess }.take(60), essUi.message)
                    }
                }
                LaunchedEffect(screen, reload, essUi.windowMs > 0) {                                // the add window counts down once a second
                    if (screen == Screen.ESSENTIALS && essUi.windowMs > 0) { kotlinx.coroutines.delay(1000); reload++ }
                }
                val saveAdmin: (AdminState) -> Unit = { st -> adminState = st; scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_ADMIN, st.encode())); BlockingState.refresh(applicationContext); reload++ } }
                var healthText by remember { mutableStateOf("") }
                LaunchedEffect(screen, healthChanged.value) {
                    if (screen == Screen.SETUP) healthText = withContext(Dispatchers.IO) {
                        when (HealthLink.status(applicationContext)) {
                            HealthLink.Status.UNAVAILABLE, HealthLink.Status.NEEDS_INSTALL -> "Health Connect is not available on this phone. Spark works the same without it."
                            else -> if (!HealthLink.granted(applicationContext)) "Not allowed yet. Spark reads sleep sessions only, and only if you press the button."
                            else {
                                val night = app.efa.data.SleepRepo(db).latestNight()
                                val watch = night?.let { n -> efa.sessionForNight(HealthLink.sessions(applicationContext, Instant.ofEpochMilli(n.startMs).minusSeconds(6 * 3600), Instant.ofEpochMilli(n.endMs).plusSeconds(6 * 3600)), Instant.ofEpochMilli(n.startMs), Instant.ofEpochMilli(n.endMs)) }
                                (night?.let { efa.healthLine(it.sleepMin, watch) }) ?: "Allowed. No sleep from a watch for the last night."
                            }
                        }
                    }
                }
                var myUoBNote by remember { mutableStateOf<String?>(null) }
                var pcAddress by remember { mutableStateOf("") }
                var pcCode by remember { mutableStateOf("") }
                var pcStatus by remember { mutableStateOf("Not sent yet.") }
                LaunchedEffect(screen, reload) {
                    if (screen == Screen.SETUP || screen == Screen.PC) withContext(Dispatchers.IO) {
                        pcAddress = db.meta().get(app.efa.data.META_PC_ADDRESS).orEmpty(); pcCode = db.meta().get(app.efa.data.META_PC_CODE).orEmpty()
                        val last = db.meta().get(app.efa.data.META_PC_LAST)?.split("|", limit = 2)
                        pcStatus = pcStatusText(last?.getOrNull(0)?.toLongOrNull()?.takeIf { last.getOrNull(1).isNullOrEmpty() }, last?.getOrNull(1)?.takeIf { it.isNotEmpty() }, System.currentTimeMillis())
                    }
                }
                var browsers by remember { mutableStateOf<List<BrowserCoverage>>(emptyList()) }
                LaunchedEffect(screen) {
                    if (screen == Screen.SETUP) browsers = withContext(Dispatchers.IO) {
                        val pm = packageManager
                        val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
                        browserCoverage(pm.queryIntentActivities(web, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.distinctBy { it.first }.filter { it.first != packageName })
                    }
                }
                var voiceDraft by remember { mutableStateOf<Pair<NewTask, String>?>(null) }
                var voiceQuietOff by remember { mutableStateOf(true) }
                var codeReminders by remember { mutableStateOf(true) }
                var photoReminders by remember { mutableStateOf(true) }
                var mealChecks by remember { mutableStateOf(true) }
                var mealLogText by remember { mutableStateOf("") }
                var modifyFromHome by remember { mutableStateOf(false) }
                var quietMode by remember { mutableStateOf(false) }
                var nightPhotoMs by remember { mutableStateOf<Long?>(null) }
                var widgetRows by remember { mutableStateOf<List<WidgetRowUi>>(emptyList()) }
                var widgetMessage by remember { mutableStateOf<String?>(null) }
                var shownWidgets by remember { mutableStateOf<Set<WidgetKind>>(WidgetKind.entries.filter { it.byDefault }.toSet()) }
                LaunchedEffect(screen, reload) {
                    withContext(Dispatchers.IO) {
                        val repo = WidgetRepo(db); val today = ZonedDateTime.now(LONDON).toLocalDate()
                        widgetRows = repo.rows(today)
                        shownWidgets = repo.placements(today).filter { shownOn(it, today).shown }.map { it.kind }.toSet()
                    }
                }
                var shopping by remember { mutableStateOf<List<app.efa.data.ShoppingRow>>(emptyList()) }
                LaunchedEffect(screen, reload) { if (screen == Screen.SHOPPING) shopping = withContext(Dispatchers.IO) { app.efa.data.ShoppingRepo(db).items() } }
                LaunchedEffect(screen, reload) { voiceQuietOff = withContext(Dispatchers.IO) { db.meta().get(app.efa.data.META_VOICE_QUIET_OFF) != "0" }; codeReminders = withContext(Dispatchers.IO) { LectureCodeAlarm.enabled(db) }; photoReminders = withContext(Dispatchers.IO) { PhotoAlarm.enabled(db) }; mealChecks = withContext(Dispatchers.IO) { MealCheckAlarm.enabled(db) }; mealLogText = withContext(Dispatchers.IO) { db.meta().get(app.efa.data.META_MEAL_LOG).orEmpty() }; quietMode = withContext(Dispatchers.IO) { db.meta().get(app.efa.data.META_QUIET_MODE) == "1" }; nightPhotoMs = withContext(Dispatchers.IO) { app.efa.data.SleepRepo(db).shutdownPhotoMs() } }
                /** V1, V3, V4: what was heard becomes a card to check, or opens a screen. A loosening only opens its request screen, where the typed wall is. */
                var reviewRow by remember { mutableStateOf<app.efa.data.TaskRow?>(null) }
                /** B5, E9: the estimates are corrected from finished tasks, and the newest finished task not yet asked about is offered on home. */
                LaunchedEffect(screen, reload) {
                    withContext(Dispatchers.IO) {
                        val repo = CalibrationRepo(db)
                        CalibrationState.cal = repo.calibration()
                        val c = repo.nextToReview(System.currentTimeMillis() - 7L * 86_400_000)
                        reviewRow = c?.task
                        review = c?.let { ReviewTask(it.task.id, it.task.title, it.task.hoursEstimate, it.note) }
                    }
                }
                /** C1, C2, O11: reads the calendar, the hours and the periods, and works out what a library period would do before it is planned. */
                val refreshLibrary: (LocalDate?, Int, String?, Boolean, Int, Int) -> Unit = { start, days, message, editing, dOpen, dClose ->
                    scope.launch(Dispatchers.IO) {
                        val plan = PlanRepo(db); val now = ZonedDateTime.now(LONDON); val hours = plan.libraryHours(db)
                        val preview = if (hours != null && start != null) libraryPreview(plan.slots(), db.tasks().open().map { t -> t.toDue() }, now, start, start.plusDays(days - 1L), hours) else null
                        val planned = db.periods().all().filter { it.kind == LIBRARY_KIND }.map { r -> r.id to "${DateTimeFormatter.ofPattern("EEE d MMM").format(LocalDate.parse(r.startDate))} to ${DateTimeFormatter.ofPattern("EEE d MMM").format(LocalDate.parse(r.endDate))}" }
                        libraryUi = LibraryUi(now.toLocalDate(), hours, editing, hours?.openMin ?: dOpen, hours?.closeMin ?: dClose, start, days, preview, planned, message)
                    }
                }
                var dayOffSource by remember { mutableStateOf("planned") }
                /** C4, H2: reads the calendar and the work due, and works out the cost of the days picked, before anything is typed. */
                val refreshDayOff: (List<LocalDate>, String?, Boolean) -> Unit = { picked, message, claiming ->
                    scope.launch(Dispatchers.IO) {
                        val plan = PlanRepo(db); val now = ZonedDateTime.now(LONDON); val slots = plan.slots(); val uses = plan.uses(db)
                        val preview = picked.takeIf { it.isNotEmpty() && !claiming }?.let { dayOffPreview(slots, db.tasks().open().map { t -> t.toDue() }, now, it, uses, sameDay = it.first() == now.toLocalDate(), shortSleep = false) }
                        val claimable = (1L..7L).map { now.toLocalDate().minusDays(it) }.filter { d -> claimable(d, now.toLocalDate()) && db.daysOff().get(d.toString()) == null }
                        val upcoming = db.daysOff().all().filter { it.state != "cancelled" && LocalDate.parse(it.date) >= now.toLocalDate() }.map { r -> "${DateTimeFormatter.ofPattern("EEE d MMM").format(LocalDate.parse(r.date))}: ${r.source.replace('_', ' ')}${if (r.state == "pending") ", claim waiting" else ""}" }
                        dayOffUi = DayOffUi(now.toLocalDate(), picked, preview, usesInLast30Days(uses, now), claimable, upcoming, message, claiming)
                    }
                }
                var morningUi by remember { mutableStateOf(MorningSetupStatus()) }
                var morningMessage by remember { mutableStateOf<String?>(null) }
                var cameraKind by remember { mutableStateOf(RefKind.NIGHT) }
                var morning by remember { mutableStateOf<MorningRow?>(null) }
                var readyMessage by remember { mutableStateOf<String?>(null) }
                var hasReadyRef by remember { mutableStateOf(true) }
                val refreshMorning: suspend () -> Unit = {
                    val refs = ReferenceRepo(applicationContext, db).status()
                    val fmt = DateTimeFormatter.ofPattern("EEE HH:mm")
                    fun info(kind: String) = refs[kind]?.let { RefInfo(fmt.format(Instant.ofEpochMilli(it.takenAtMs).atZone(LONDON)), it.path,
                        stale = kind == RefKind.NIGHT && System.currentTimeMillis() - it.takenAtMs > NIGHT_REFERENCE_VALID.toMillis()) }
                    morningUi = MorningSetupStatus(info(RefKind.NIGHT), info(RefKind.STANDING), info(RefKind.READY), null, AlarmSession.stepsAvailable(applicationContext),
                        (getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager).getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) != null)
                }
                LaunchedEffect(screen, reload) { if (screen == Screen.MORNING || screen == Screen.SETUP) withContext(Dispatchers.IO) { refreshMorning() } }
                LaunchedEffect(screen, VrSignal.tick) {                                                      // VR14: home and the block screen follow the stored cycle, whoever changed it
                    if (screen == Screen.HOME || screen == Screen.BLOCK) while (true) {
                        vrLive = withContext(Dispatchers.IO) {
                            runCatching {
                                val pat = loaded?.pattern; val nowZ = ZonedDateTime.now(LONDON)
                                if (pat == null) null else app.efa.data.VrRepo(db).active(listOf(-1L, 0L).flatMap { slotsFor(pat, nowZ.toLocalDate().plusDays(it), nowZ.zone) })?.let { VrActive(it.first, it.second) }
                            }.getOrNull()
                        }
                        confirms = withContext(Dispatchers.IO) {                                               // CF1, VR15: ask once when a cycle outlives its block, close what went stale, list what is open
                            runCatching {
                                vrLive?.let { BlockEndAsk.check(applicationContext, it.ds, it.run) }
                                ConfirmActions.sweep(applicationContext)
                                app.efa.data.ConfirmRepo(db).open()
                            }.getOrDefault(emptyList())
                        }
                        delay(1000)
                    }
                }
                LaunchedEffect(screen) { if (screen == Screen.SETUP) vrCfg = withContext(Dispatchers.IO) { app.efa.data.VrRepo(db).config() } }
                LaunchedEffect(screen, reload, clock) {
                    withContext(Dispatchers.IO) {
                        morning = try { MorningRepo(db).latest() } catch (e: Exception) { morning }
                        hasReadyRef = ReferenceRepo(applicationContext, db).status().containsKey(RefKind.READY)
                    }
                }
                /** IM4, IM5: read, check and preview a file. Nothing is written. */
                val checkIncoming: suspend (Incoming?) -> Unit = { inc ->
                    importUi = if (inc == null) ImportUi.Failed("That file could not be read.") else try {
                        val now = ZonedDateTime.now(LONDON); val plan = PlanRepo(db)
                        db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)       // a file can arrive before the first screen has loaded
                        ImportUi.Checked(inc.name, verifyImportIn(ImportMode.ADD, parseTimetableCsv(inc.text, now.toLocalDate()), plan.slots(), plan.known(), db.tasks().open().map { it.toDue() }, now, hoursFor = CalibrationState.cal::hours, reflow = true), fileHash(inc.text), inc.text, ImportMode.ADD)
                    } catch (e: Exception) { ImportUi.Failed("That file could not be checked.") }
                    screen = Screen.IMPORT
                }
                val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) scope.launch(Dispatchers.IO) { checkIncoming(ImportSource.read(applicationContext, uri)) }
                }
                LaunchedEffect(incomingIntent.value) {
                    val i = incomingIntent.value ?: return@LaunchedEffect
                    incomingIntent.value = null
                    withContext(Dispatchers.IO) { checkIncoming(ImportSource.read(applicationContext, i)) }
                }
                LaunchedEffect(screen, reload) { if (screen == Screen.IMPORT) imports = withContext(Dispatchers.IO) { db.imports().all() } }
                // WK1: the walkthrough opens by itself the first time, and is always there again from Setup. null while the answer is being read.
                var walkthroughSeen by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) { walkthroughSeen = withContext(Dispatchers.IO) { !walkthroughDue(db.meta().get(app.efa.data.META_WALKTHROUGH_SEEN)) } }
                var liteDay by remember { mutableStateOf(false) }
                LaunchedEffect(reload, screen, clock.minute) { liteDay = withContext(Dispatchers.IO) { db.meta().get(app.efa.data.META_LITE) == "1" || db.meta().get(app.efa.data.META_LITE_AUTO) == "1" } }
                LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Backup.nightly(applicationContext) } }          // CT1: a backup file about once a day
                var importMeter by remember { mutableStateOf<String?>(null) }
                var importEconomy by remember { mutableStateOf<String?>(null) }
                /** IX2: switching mode checks the same file again in that mode. Nothing is written. */
                val setImportMode: (ImportMode) -> Unit = { m ->
                    val ui = importUi as? ImportUi.Checked
                    if (ui != null && ui.text.isNotEmpty()) scope.launch(Dispatchers.IO) {
                        importUi = try {
                            val now = ZonedDateTime.now(LONDON); val plan = PlanRepo(db)
                            ui.copy(verification = verifyImportIn(m, parseTimetableCsv(ui.text, now.toLocalDate()), plan.slots(), plan.known(), db.tasks().open().map { it.toDue() }, now, hoursFor = CalibrationState.cal::hours, reflow = true), mode = m)
                        } catch (e: Exception) { ImportUi.Failed("That file could not be checked.") }
                    }
                }
                LaunchedEffect(screen, reload, importUi) {
                    if (screen == Screen.IMPORT) withContext(Dispatchers.IO) {
                        val repo = app.efa.data.ChangeRepo(db); val h = repo.history(); val now = java.time.Instant.now()
                        importMeter = economyMeter(h, now)
                        val ui = importUi as? ImportUi.Checked
                        importEconomy = if (ui == null || !ui.verification.ok) null else when (val d = decideChange(h, importChangeClass(ui.verification, ui.mode), true, now, repo.installedAt())) {
                            is ChangeDecision.Free -> if (d.trial) "Trial week: counted, nothing locks yet." else if (ui.verification.needsWall) "This loosens, so it needs the typed wall." else "Free."
                            is ChangeDecision.Wall -> "${d.reason} It needs a typed string of ${d.length} characters."
                            is ChangeDecision.Cooldown -> d.reason + " Until " + java.time.ZonedDateTime.ofInstant(d.until, LONDON).format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm")) + "."
                            is ChangeDecision.Locked -> d.reason
                        }
                    }
                }
                /** IM11: one transaction; if it fails nothing was written and the screen says so. */
                val applyChecked: () -> Unit = {
                    val ui = importUi as? ImportUi.Checked
                    if (ui != null) scope.launch(Dispatchers.IO) {
                        importUi = try {
                            val v = ui.verification
                            val at = System.currentTimeMillis()
                            PlanRepo(db).apply(v, ui.name, ui.fileHash, at, LONDON.id, v.summaryLines().joinToString(". "))
                            app.efa.data.ChangeRepo(db).record(importChangeClass(v, ui.mode), true, at)          // CE1, CE5: it counts against the week
                            BlockingState.refresh(applicationContext)
                            ImportUi.Done(v.summaryLines())
                        } catch (e: Exception) { ImportUi.Failed("Applying failed (${e.javaClass.simpleName}).") }
                        reload++; screen = Screen.IMPORT
                    }
                }
                val askRuntime = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reload++ }
                val askSteps = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reload++ }
                val askRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { reload++ }
                val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    noticeOn = BufferNotifier.allowed(applicationContext); BufferNotifier.update(applicationContext)
                }
                LaunchedEffect(pendingAction.value) {
                    if (pendingAction.value == BufferNotifier.ACTION_QUICK_ADD) { screen = Screen.QUICK_ADD; pendingAction.value = null }
                    if (pendingAction.value == BufferNotifier.ACTION_READY) { screen = Screen.READY; pendingAction.value = null }
                    when (pendingAction.value) {                                   // WG8: tapping a widget opens its full screen
                        "widget_shopping" -> { screen = Screen.SHOPPING; pendingAction.value = null }
                        "widget_money" -> { screen = Screen.MONEY; pendingAction.value = null }
                        "widget_working", "widget_day", "widget_week" -> { screen = Screen.HOME; pendingAction.value = null }
                        PhotoAlarm.ACTION_OPEN -> { screen = Screen.HOME; homeTick.intValue++; pendingAction.value = null }       // PH1: the photo card is the first thing on the home screen
                    }
                }

                LaunchedEffect(reload) {
                    loaded = withContext(Dispatchers.IO) {
                        try {
                            db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
                            val pattern = PlanRepo(db).slots()
                            val repo = BlockRepo(applicationContext, db, pattern)
                            repo.cleanThumbs()                          // ST1, ST4: expired and over-ceiling thumbnails go
                            val pr = PlaceRepo(db)
                            places = PlacesState(pr.home(), pr.statuses(pattern.filter { it.type == "lecture" || it.type == "tutorial" }.map { it.venue }.filter { it.isNotBlank() }), GitConfigRepo(db).get())
                            blockList = BlockingRepo(db).list()
                            BlockingState.refresh(applicationContext)       // the blocking service sees the change at once
                            try { AlarmScheduler.refresh(applicationContext) } catch (e: Exception) { /* the last alarm stays set (X7) */ }
                            try { LectureCodeAlarm.refresh(applicationContext) } catch (e: Exception) { /* the minute loop sets it again (X7) */ }
                            try { PhotoAlarm.refresh(applicationContext) } catch (e: Exception) { /* the minute loop sets it again (X7) */ }
                            try { EventReminderAlarm.refresh(applicationContext) } catch (e: Exception) { /* the minute loop sets it again (X7) */ }
                            try { MealCheckAlarm.refresh(applicationContext) } catch (e: Exception) { /* the minute loop sets it again (X7) */ }
                            heldWaiting = HeldRepo(db).anyUnreviewed()
                            BufferNotifier.update(applicationContext)
                            // U1, U4: which setup steps are done right now, and a log entry once when one that was done goes off.
                            val done = SetupStatus.read(applicationContext, db)
                            val was = db.meta().get("setup_done") == "1"
                            val bl = BlockingRepo(db)
                            SETUP_ORDER.forEach { bl.noteStep(it.name, it in done, was, System.currentTimeMillis()) }
                            if (setupComplete(done) && !was) db.meta().put(MetaRow("setup_done", "1"))
                            CrashGuard.takeUnloggedHandOver(applicationContext)?.let { db.tamper().insert(TamperRow(atMs = it, kind = "launcher_crash_handover", detail = "3 crashes in 10 minutes")) }
                            launcherPaused = CrashGuard.handedOverAt(applicationContext) != null
                            everDone = was || setupComplete(done)
                            doneSteps = done; setupLoaded = true
                            val since = System.currentTimeMillis() - 36L * 3_600_000
                            val due = DateTimeFormatter.ofPattern("EEE HH:mm")
                            val moved = db.tasks().missedSince(since).map { "${it.reason}. Back in the Buffer: ${fmt1(it.hoursEstimate)}h, due ${due.format(it.dueAt())}." } + db.overlay().convertedSince(since).map { it.reason }
                            val hhmmFmt = DateTimeFormatter.ofPattern("HH:mm")
                            val night = SleepRepo(db).latestNight()
                            val sleep = SleepStatus(db.nights().all().size, night?.date, night?.sleepMin ?: 0,
                                night?.let { hhmmFmt.format(Instant.ofEpochMilli(it.startMs).atZone(LONDON)) }.orEmpty(), night?.let { hhmmFmt.format(Instant.ofEpochMilli(it.endMs).atZone(LONDON)) }.orEmpty(),
                                night?.checkCount ?: 0, night?.gapCount ?: 0, db.sensors().count(),
                                db.sensors().firstAt()?.let { DateTimeFormatter.ofPattern("EEE d MMM").format(Instant.ofEpochMilli(it).atZone(LONDON)) },
                                // S4: from waking (the first long screen-on) to the photo that stopped the alarm, for the night that ended that morning
                                run {
                                    val m = MorningRepo(db).latest()
                                    if (night != null && m?.stoppedMs != null && java.time.LocalDate.parse(night.date).plusDays(1) == java.time.LocalDate.parse(m.date))
                                        awakeBeforeAlarmStopped(night.endMs, m.stoppedMs).toMinutes().toInt().takeIf { it in 0..180 } else null
                                })
                            val nowZ = ZonedDateTime.now(LONDON)
                            val firstUse = db.meta().get(META_FIRST_USE)?.toLongOrNull() ?: nowZ.toInstant().toEpochMilli()
                            val rewards = RewardsRepo(db)
                            Loaded(pattern, db.tasks().open(), repo.verifiedKeys(), PlanRepo(db).periods(), moved, sleep,
                                isShortSleepToday(db, nowZ), rewards.view(pattern, firstUse, nowZ), rewards.earnings(nowZ), app.efa.data.RunRepo(db).forDay(nowZ.toLocalDate()),
                                app.efa.data.VrRepo(db).active(listOf(-1L, 0L).flatMap { slotsFor(pattern, nowZ.toLocalDate().plusDays(it), nowZ.zone) })?.let { VrActive(it.first, it.second) })
                        } catch (e: Exception) { Loaded(null, emptyList()) }   // never show a wrong schedule or Buffer
                    }
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        clock = ZonedDateTime.now(LONDON)
                        heldWaiting = withContext(Dispatchers.IO) { try { HeldRepo(db).anyUnreviewed() } catch (e: Exception) { heldWaiting } }
                        delay(30_000)
                    }
                }
                LaunchedEffect(screen, reload, clock) {
                    if (screen == Screen.DIGEST) digestState = withContext(Dispatchers.IO) { digest(HeldRepo(db).unreviewed(), BlockingState.quietWindow()?.until) { BlockingState.decide(it) } }
                }
                // U4, X5: whenever the app comes back to the front, check the blocking service is still on. Switching it off is logged once.
                LifecycleResumeEffect(Unit) {
                    serviceOn = isBlockerEnabled(applicationContext); listenerOn = isNotificationAccessOn(applicationContext); noticeOn = BufferNotifier.allowed(applicationContext)
                    reload++; onPauseOrDispose {}
                }
                LaunchedEffect(serviceOn, blockList) {
                    withContext(Dispatchers.IO) { BlockingRepo(db).noteServiceState(!blockList.isNullOrEmpty(), serviceOn, System.currentTimeMillis()) }
                }
                val setupNeeded = SetupGate.enforced && setupLoaded && !setupComplete(doneSteps)
                val act: (SetupStep) -> Unit = { step ->
                    when (step) {
                        SetupStep.BLOCK_LIST -> scope.launch(Dispatchers.IO) { apps = installedApps(applicationContext); withContext(Dispatchers.Main) { screen = Screen.BLOCKLIST } }
                        SetupStep.DEFAULT_HOME -> SetupStatus.settingsIntent(applicationContext, step)?.let { askRole.launch(it) }
                        else -> {
                            val perm = SetupStatus.runtimePermission(step)
                            if (perm != null && !(step == SetupStep.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < 33)) askRuntime.launch(perm)
                            else SetupStatus.settingsIntent(applicationContext, step)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }
                }

                val l = loaded
                val repo = remember(l) { l?.pattern?.let { BlockRepo(applicationContext, db, it) } }
                val buffer = l?.let { bufferOf(it, clock) } ?: BufferState.NoDeadline
                LaunchedEffect(voiceHeard.value) {
                    val heard = voiceHeard.value ?: return@LaunchedEffect
                    voiceHeard.value = null
                    val now = ZonedDateTime.now(LONDON)
                    val vocab = withContext(Dispatchers.IO) { voiceVocabulary(PlanRepo(db).slots()) }
                    when (val intent = parseVoice(heard, now.toLocalDate(), vocab)) {
                        is VoiceIntent.AddTask -> {
                            val due = ZonedDateTime.of(intent.day ?: now.toLocalDate().plusDays(1), intent.time ?: LocalTime.of(17, 0), now.zone)
                            voiceDraft = NewTask(intent.title, due, intent.heaviness ?: QUICK_ADD_DEFAULT_HEAVINESS) to intent.heard
                            screen = Screen.QUICK_ADD
                        }
                        is VoiceIntent.Go -> when (intent.target) {
                            VoiceTarget.BUFFER -> screen = Screen.HOME
                            VoiceTarget.MONEY -> screen = Screen.MONEY
                            VoiceTarget.SHOPPING -> screen = Screen.SHOPPING
                            VoiceTarget.SETUP -> screen = Screen.SETUP
                            VoiceTarget.WIDGETS -> { widgetMessage = null; screen = Screen.WIDGETS }
                            VoiceTarget.LIBRARY -> { refreshLibrary(null, 5, null, false, 8 * 60, 22 * 60); screen = Screen.LIBRARY }
                            VoiceTarget.IMPORT -> screen = Screen.IMPORT
                            VoiceTarget.HOME -> screen = Screen.HOME
                            VoiceTarget.QUICK_ADD -> { voiceDraft = null; screen = Screen.QUICK_ADD }
                            VoiceTarget.DAY_OFF -> { refreshDayOff(emptyList(), null, false); screen = Screen.DAYOFF }      // V4: this only opens the request
                            VoiceTarget.BLOCKLIST -> screen = Screen.LIST_GATE                                             // V4: the wall comes before any edit
                        }
                        is VoiceIntent.Unknown -> voiceMessage.value = "I heard \u201c${intent.heard}\u201d but did not understand it. Use the plus button to type it."
                    }
                }
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                BackHandler(enabled = screen == Screen.HOME) { /* a home screen has nowhere to go back to */ }

                // ST2, CF1: a late start, an overrun or a question's answer asks to slide the rest of today: the preview says what moves, and nothing changes until it is applied
                val slide: (Int) -> Unit = { m ->
                    val today = ZonedDateTime.now(LONDON).toLocalDate(); val inst = Instance(Preset.RUN_OVER, today, today, mapOf("minutes" to m.toString()))
                    modifyFromHome = true; modDraft = inst; modRec = emptyMap(); modMessage = null; modPreview = null; screen = Screen.MOD_FORM
                    scope.launch(Dispatchers.IO) { modPreview = app.efa.data.ModifierRepo(db).preview(inst, ZonedDateTime.now(LONDON)) }
                }
                // ST4, CD1: what is happening against the plan: over, late, something came up, or the door to every other change
                val reality: (String) -> Unit = reality@{ kind ->
                    if (kind == "door") { screen = Screen.CHANGE; return@reality }
                    modifyFromHome = true
                    val today = clock.toLocalDate()
                    val open = { inst: Instance, preview: Boolean -> modDraft = inst; modRec = emptyMap(); modMessage = null; modPreview = null; screen = Screen.MOD_FORM
                        if (preview) scope.launch(Dispatchers.IO) { modPreview = app.efa.data.ModifierRepo(db).preview(inst, ZonedDateTime.now(LONDON)) } }
                    when (kind) {
                        "night" -> { val tn = efa.nightOf(ZonedDateTime.now(LONDON)); open(Instance(Preset.NIGHT, tn, tn, emptyMap()), false) }
                        "late" -> open(Instance(Preset.LATE_START, today, today, mapOf("from_now" to "true")), true)
                        "cameup" -> screen = Screen.CAME_UP
                        else -> screen = Screen.MODIFY          // running over: the minutes are chosen under Right now
                    }
                }
                when (screen) {
                    Screen.HOME -> if (WalkthroughGate.enforced && walkthroughSeen == null) Box(Modifier.fillMaxSize().background(LocalColors.current.bg))
                    else if (WalkthroughGate.enforced && walkthroughSeen == false) WalkthroughScreen(firstRun = true, onFinish = { openSetup ->
                        walkthroughSeen = true; scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_WALKTHROUGH_SEEN, WALKTHROUGH_SEEN_VALUE)) }
                        if (openSetup) screen = Screen.SETUP
                    })
                    else if (!setupLoaded && SetupGate.enforced) Box(Modifier.fillMaxSize().background(LocalColors.current.bg))
                    else if (setupNeeded) SetupFlowScreen(doneSteps, restore = everDone, onAct = act,
                        onEssential = { launchEssential(it) }, onOpenSetup = { screen = Screen.SETUP },
                        crashNotice = launcherPaused, onRestoreLauncher = { CrashGuard.restore(applicationContext); launcherPaused = false; reload++ })
                    else {
                        val hhmm = DateTimeFormatter.ofPattern("HH:mm")
                        val period = l?.let { activePeriod(it.periods, clock.toLocalDate()) }
                        val openTasks = l?.tasks?.map { it.toOpen(period) }.orEmpty()
                        val photos = l?.pattern?.let { p -> photoPlan(p, clock, l.verified, nightPhotoMs) }.orEmpty()
                        val photoFocus = photoFocus(photos)?.takeIf { it.open || it.opens.isBefore(clock.plusHours(12)) }
                        val alloc = l?.pattern?.let { p -> allocateTasks(p, openTasks, clock) }
                        // SC3: the schedule page, and the Now and Next cards, are read from the same rows, so they cannot disagree. RS2, RS8: what is placed in a block is said in the block's own line.
                        val sched = l?.pattern?.let { p -> scheduleView(p, openTasks, alloc, clock, days = 36, verified = l.verified) }.orEmpty()
                        val hb = homeBlocks(sched)
                        val nowRow = hb.now
                        val dueMeal = if (mealChecks) l?.pattern?.let { p -> efa.dueMealCheck(p, efa.MealLog.decode(mealLogText), clock) } else null
                        val phoneDecision = blockList?.takeIf { it.isNotEmpty() }?.let { BlockingState.decide(it.first()) }
                        HomeScreen(
                            HomeState(
                                date = DateTimeFormatter.ofPattern("EEE d MMM").format(clock),
                                phone = if (blockList.isNullOrEmpty()) null else if (phoneDecision is Decision.Block) "Phone closed" else "Phone open",
                                phoneOpen = phoneDecision !is Decision.Block,
                                time = hhmm.format(clock), buffer = buffer,
                                nowLabel = nowRow?.let { "Now, until ${it.end}" },
                                nowTitle = nowRow?.title,
                                nowDetail = nowRow?.let { listOf(it.detail, proofHint(it.photo?.slot?.let { ds -> proofFor(ds.slot) } ?: Proof.NONE)).filter { s -> s.isNotBlank() }.joinToString(". ") },
                                nowLeftMin = nowRow?.endAt?.let { e -> java.time.Duration.between(clock, e).toMinutes().coerceAtLeast(0).toInt() },
                                nowFraction = nowRow?.let { r -> if (r.startAt == null || r.endAt == null) null else (java.time.Duration.between(r.startAt, clock).toMillis().toFloat() / java.time.Duration.between(r.startAt, r.endAt).toMillis().coerceAtLeast(1)).coerceIn(0f, 1f) },
                                progressFraction = l?.money?.week?.takeIf { it.plannedHours > 0 }?.let { (it.verifiedHours / it.plannedHours).toFloat() },
                                schedule = sched,
                                nowOpensBlock = nowRow?.photo != null || nowRow?.slot?.let { ds -> isRunnable(ds.slot) || routineForSlot(ds.slot) != null } == true,
                                next = hb.next.map { NextRow(it.start, it.title, it.detail) },
                                freeToSpendPence = l?.money?.takeIf { it.live }?.week?.money?.freeToSpendPence,      // G6: record-only for the first 14 days
                                progress = l?.money?.week?.takeIf { it.plannedHours > 0 }?.progress(),
                                moneyNote = l?.money?.takeIf { !it.live }?.let { "Money starts ${DateTimeFormatter.ofPattern("EEE d MMM").format(it.liveFrom)}. Recorded until then." },
                                earnedOn = l?.pattern?.let { p -> earnedAccessActive(clock, l.earnings, p) } == true,
                                photo = photoFocus, photoLeft = photoFocus?.minutesLeft(clock)?.toInt(),
                                meal = dueMeal?.title, mealKey = dueMeal?.key.orEmpty(), evening = clock.hour >= 21, lite = liteDay, adapt = AdaptState.view(), mode = l?.pattern?.let { efa.homeModeOf(it, clock) } ?: efa.HomeMode.FREE,
                                heldWaiting = heldWaiting,
                                unavailable = l != null && l.pattern == null,
                                launcherPaused = launcherPaused,
                                morning = when (morningPhase(morning?.toState(), clock)) {
                                    MorningPhase.WAITING_FOR_READY -> "Social is blocked until the ready photo. It ends by itself at ${hhmm.format(readyReleaseAt(morning!!.stoppedMs!!, clock.zone))}."
                                    MorningPhase.RINGING -> "The alarm is ringing."
                                    else -> null
                                },
                                widgets = shownWidgets,
                                voiceNote = voiceMessage.value,
                                newRoom = newRoom?.venueText,
                                voice = voiceAllowed(l?.pattern?.let { p -> slotsFor(p, clock.toLocalDate(), clock.zone).firstOrNull { !it.start.isAfter(clock) && it.end.isAfter(clock) }?.slot }, voiceQuietOff),
                                week = l?.pattern?.let { weekGlance(it, clock.toLocalDate(), clock.zone) }.orEmpty(),
                                shoppingDay = l?.pattern?.let { p -> slotsFor(p, clock.toLocalDate()).any { it.slot.title.contains("Aldi", ignoreCase = true) } } == true,
                                review = review?.let { "How long did \"${it.title}\" take?" },
                                period = if (l?.pattern?.let { isDayOff(it, clock.toLocalDate()) } == true) "Day off today. Nothing counts as lost." else period?.let { "${PeriodKind.entries.first { k -> k.csv == it.kind }.label}, until ${DateTimeFormatter.ofPattern("EEE d MMM").format(java.time.LocalDate.parse(it.endDate))}" },
                                moved = l?.moved.orEmpty(),
                                vr = vrLive,
                                confirms = confirms,
                            ),
                            onVr = { a ->
                                val act = vrLive
                                if (act != null) {
                                    if (a == VrAction.OPEN) { selected = act.ds; screen = Screen.BLOCK }
                                    else scope.launch(Dispatchers.IO) {
                                        val r = app.efa.data.VrRepo(db); val now = System.currentTimeMillis()
                                        when (a) {
                                            VrAction.PAUSE -> r.pause(act.ds, now); VrAction.RESUME -> r.resume(act.ds, now)
                                            VrAction.NEXT -> r.advance(act.ds, now, act.ds.start.zone); VrAction.PAGE -> r.page(act.ds, now); VrAction.OPEN -> {}
                                        }
                                        VrNotifier.refresh(applicationContext, act.ds)
                                        val after = r.get(act.ds)                                         // set at once, so the card shows the new step as soon as the hold is done
                                        withContext(Dispatchers.Main) { vrLive = after?.takeIf { vrView(it, System.currentTimeMillis()).status == VrStatus.RUNNING }?.let { VrActive(act.ds, it) } }
                                    }
                                }
                            },
                            onConfirm = { id, opt -> scope.launch(Dispatchers.IO) {
                                val out = ConfirmActions.answer(applicationContext, id, opt)
                                withContext(Dispatchers.Main) {
                                    confirms = confirms.filter { it.id != id }
                                    out?.message?.let { android.widget.Toast.makeText(applicationContext, it, android.widget.Toast.LENGTH_SHORT).show() }
                                    out?.then?.takeIf { it.startsWith("slide:") }?.removePrefix("slide:")?.toIntOrNull()?.let { slide(it) }
                                }
                            } },
                            onEssential = { key -> launchEssential(key) },
                            onSearch = { scope.launch(Dispatchers.IO) { apps = installedApps(applicationContext); withContext(Dispatchers.Main) { screen = Screen.SEARCH } } },
                            onRestoreLauncher = { CrashGuard.restore(applicationContext); launcherPaused = false; reload++ },
                            onHeld = { screen = Screen.DIGEST },
                            onReady = { screen = Screen.READY },
                            onReview = { screen = Screen.REVIEW },
                            onShopping = { screen = Screen.SHOPPING },
                            onVoice = { voiceDraft = null; startVoice(emptyList()) },
                            onNewRoom = { mapVenue = newRoom; venueBack = Screen.HOME; screen = Screen.MAP_VENUE },
                            onMoney = { screen = Screen.MONEY },
                            onMeal = { key, ate -> scope.launch(Dispatchers.IO) { MealCheckAlarm.answer(applicationContext, key, ate); reload++ } },
                            onEmergency = { a -> scope.launch(Dispatchers.IO) { Emergency.run(applicationContext, a) } },
                            onReality = reality,
                            onAdapt = { kind ->
                                when (kind) {
                                    "drift" -> AdaptState.proposal?.let { p ->
                                        scope.launch(Dispatchers.IO) { AdaptState.acknowledge(db, System.currentTimeMillis()) }
                                        modifyFromHome = true; modDraft = p.instance; modRec = emptyMap(); modMessage = null; modPreview = null; screen = Screen.MOD_FORM
                                        scope.launch(Dispatchers.IO) { modPreview = app.efa.data.ModifierRepo(db).preview(p.instance, ZonedDateTime.now(LONDON)) }
                                    }
                                    "dismiss" -> scope.launch(Dispatchers.IO) { AdaptState.acknowledge(db, System.currentTimeMillis()); reload++ }
                                    "full" -> scope.launch(Dispatchers.IO) { AdaptState.fullDay(db, clock.toLocalDate()); BlockingState.refresh(applicationContext); reload++ }
                                }
                            },
                            third = { WorkItOutScreen(db, moved = l?.moved.orEmpty(), onOpenPlan = { inst, preview -> modifyFromHome = true; modDraft = inst; modRec = emptyMap(); modMessage = null; modPreview = null; screen = Screen.MOD_FORM
                                if (preview) scope.launch(Dispatchers.IO) { modPreview = app.efa.data.ModifierRepo(db).preview(inst, ZonedDateTime.now(LONDON)) } },
                                onWall = { title, text, len, action -> wallBack = Screen.HOME; wallLength = len; wall = Triple(title, text, action); screen = Screen.GATE }) },
                            onNow = { (nowRow?.photo?.slot ?: nowRow?.slot?.takeIf { ds -> isRunnable(ds.slot) || routineForSlot(ds.slot) != null })?.let { selected = it; screen = Screen.BLOCK } },
                            onProofDue = { l?.pattern?.let { p -> proofDue(p, clock, l.verified).firstOrNull() }?.let { selected = it; screen = Screen.BLOCK } },
                            onPhoto = { photoFocus?.slot?.let { selected = it; screen = Screen.BLOCK } ?: run { screen = Screen.MORNING } },
                            onBuffer = { if (buffer is BufferState.Value) screen = Screen.WORKING },
                            onQuickAdd = { screen = Screen.QUICK_ADD },
                            onSetup = { screen = Screen.SETUP },
                            homeTick = homeTick.intValue,
                        )
                    }
                    Screen.WORKING -> (buffer as? BufferState.Value)?.let { WorkingScreen(it.result) { screen = Screen.HOME } } ?: run { screen = Screen.HOME }
                    Screen.BLOCK -> {
                        val ds = selected
                        if (ds == null || repo == null) screen = Screen.HOME
                        else BlockScreen(ds, object : BlockActions {
                            override suspend fun load(ds: DatedSlot): BlockRow? = repo.load(ds)
                            override suspend fun recordHold(ds: DatedSlot, atMs: Long) = repo.recordHold(ds, atMs)
                            override suspend fun submitPhoto(ds: DatedSlot, jpeg: ByteArray, now: ZonedDateTime, fix: Fix?): Verdict = repo.submitPhoto(ds, jpeg, now, fix)
                            override suspend fun checkCommit(ds: DatedSlot, now: ZonedDateTime): Verdict = repo.checkCommit(ds, now)
                            override suspend fun rewardLines(ds: DatedSlot, creditedMin: Int): List<String> =
                                verifiedLines(ds.slot, java.time.Duration.between(ds.start, ds.end).toMinutes().toInt(), creditedMin, l?.pattern.orEmpty(), ZonedDateTime.now(ds.start.zone))
                            override suspend fun vrRun(ds: DatedSlot): VrRun? = app.efa.data.VrRepo(db).get(ds)
                            override suspend fun vrPlannedN(): Int = app.efa.data.VrRepo(db).plannedN()
                            override suspend fun vrConfig(): VrConfig = app.efa.data.VrRepo(db).config()
                            override suspend fun vrStart(ds: DatedSlot, nowMs: Long) { app.efa.data.VrRepo(db).start(ds, nowMs); VrNotifier.refresh(applicationContext, ds); BlockingState.refresh(applicationContext) }
                            override suspend fun vrAdvance(ds: DatedSlot, nowMs: Long) { app.efa.data.VrRepo(db).advance(ds, nowMs, ds.start.zone); VrNotifier.refresh(applicationContext, ds) }
                            override suspend fun vrPage(ds: DatedSlot, nowMs: Long) = app.efa.data.VrRepo(db).page(ds, nowMs)
                            override suspend fun vrPause(ds: DatedSlot, nowMs: Long) { app.efa.data.VrRepo(db).pause(ds, nowMs); VrNotifier.refresh(applicationContext, ds) }
                            override suspend fun vrResume(ds: DatedSlot, nowMs: Long) { app.efa.data.VrRepo(db).resume(ds, nowMs); VrNotifier.refresh(applicationContext, ds) }
                            override suspend fun runRecord(ds: DatedSlot): RunRecord? = app.efa.data.RunRepo(db).get(ds)
                            override suspend fun runStart(ds: DatedSlot, nowMs: Long) { app.efa.data.RunRepo(db).start(ds, nowMs); BlockingState.refresh(applicationContext) }
                            override suspend fun runFinish(ds: DatedSlot, nowMs: Long) = app.efa.data.RunRepo(db).finish(ds, nowMs)
                            override suspend fun runSkip(ds: DatedSlot, reason: String) = app.efa.data.RunRepo(db).skip(ds, reason)
                            override suspend fun conversionNote(ds: DatedSlot): String? =
                                if (!isConversion(ds.slot)) null else db.overlay().conversion(ds.plannedDay.toString(), ds.slot.startMin)?.reason
                                    ?.let { "$it. A free slot is assigned the night before and does not change in the moment without the typed wall." }
                            override suspend fun currentFix(): Fix? = LocationFix.current(applicationContext)
                            override fun hasLocationPermission() = LocationFix.hasPermission(applicationContext)
                        }, onBack = { reload++; screen = Screen.HOME }, onStartRoutine = { id -> RoutineLaunch.pending = id; reload++; screen = Screen.HOME },
                            onShift = slide,
                            onCameUp = { reload++; screen = Screen.CAME_UP }, onTurnBack = { slot ->
                            wallBack = Screen.HOME; wallLength = efa.WallLength.FREE_SLOT_CHANGE
                            wall = Triple("Make it a break", "This was free time that the Buffer turned into work. Type the characters below by hand to turn it back. It will not be turned into work again.", {
                                scope.launch(Dispatchers.IO) { PlanRepo(db).turnBack(db, slot.plannedDay, slot.slot.startMin, slot.slot.endMin + RESET_MIN, System.currentTimeMillis()); BlockingState.refresh(applicationContext); reload++ }
                            }); screen = Screen.GATE
                        })
                    }
                    Screen.CHANGE -> ChangeDoorScreen(
                        onChoose = { ch ->
                            when (ch) {
                                ChangeChoice.OVER -> reality("over")
                                ChangeChoice.LATE -> reality("late")
                                ChangeChoice.CAME_UP -> reality("cameup")
                                ChangeChoice.DEADLINE -> screen = Screen.QUICK_ADD
                                ChangeChoice.DAY_OFF -> { refreshDayOff(emptyList(), null, false); screen = Screen.DAYOFF }
                                ChangeChoice.LIBRARY -> { refreshLibrary(null, 5, null, false, 8 * 60, 22 * 60); screen = Screen.LIBRARY }
                                ChangeChoice.FILE -> { importUi = ImportUi.Idle; screen = Screen.IMPORT }
                                ChangeChoice.PERIOD -> { modifyFromHome = false; modMessage = null; screen = Screen.MODIFY }
                            }
                        },
                        wake = wakeUi.copy(message = wakeMessage),
                        onWakeClear = { scope.launch(Dispatchers.IO) { app.efa.data.ModifierRepo(db).clearWakeChoices(); AlarmScheduler.setChosenWake(applicationContext, null); BlockingState.refresh(applicationContext); AlarmScheduler.refresh(applicationContext); wakeMessage = "Back to the normal alarm and the normal morning."; reload++ } },
                        onWakeSet = { h, m ->
                            scope.launch(Dispatchers.IO) {
                                val now = BlockingState.now(); val chosen = nextClockTime(now, h, m)
                                val onset = AlarmScheduler.liveOnset(app.efa.data.SleepRepo(db), now)
                                val problem = wakeChoiceProblem(now, onset, chosen)
                                if (problem != null) { wakeMessage = problem; return@launch }
                                val mods = app.efa.data.ModifierRepo(db)
                                mods.clearWakeChoices()                                       // CW1: start again from the normal morning, so a new choice never stacks on an old one
                                val plan = mods.wakeChoicePlan(chosen, now)
                                if (plan.problems.isNotEmpty()) {
                                    AlarmScheduler.setChosenWake(applicationContext, null); BlockingState.refresh(applicationContext); AlarmScheduler.refresh(applicationContext)
                                    wakeMessage = plan.problems.joinToString(" ") + " The normal alarm and morning are back."; reload++; return@launch
                                }
                                val normal = AlarmScheduler.planNow(applicationContext, now, useChosen = false)
                                val withChoice = planAlarmWithDaysOff(now, onset, PlanRepo(db).slots(), chosen)
                                val at = "%02d:%02d".format(chosen.hour, chosen.minute)
                                val save = {
                                    scope.launch(Dispatchers.IO) {
                                        wakeMessage = try {
                                            AlarmScheduler.setChosenWake(applicationContext, chosen)
                                            plan.inst?.let { mods.apply(it, ZonedDateTime.now(LONDON), 0) }
                                            BlockingState.refresh(applicationContext); AlarmScheduler.refresh(applicationContext)
                                            "The next alarm is set for $at." + if (plan.inst != null) " The morning starts when you wake." else ""
                                        } catch (e: Exception) { AlarmScheduler.setChosenWake(applicationContext, null); "Not set: ${e.message}" }
                                        reload++
                                    }; Unit
                                }
                                if (wakeNeedsWall(normal, withChoice) || plan.wallNeeded) {
                                    wallBack = Screen.CHANGE; wallLength = efa.WallLength.WAKE_LATER
                                    wall = Triple("Wake later", "You will sleep until $at, later than Spark would have woken you. The morning will start then, and anything fixed before it is missed and comes back as work. Type the characters below by hand.", { save() })
                                    screen = Screen.GATE
                                } else save()
                            }
                        },
                        onBack = { screen = Screen.HOME })
                    Screen.SETUP -> SetupScreen(places, db = db,
                        onWalkthrough = { screen = Screen.WALKTHROUGH },
                        vrCfg = vrCfg,
                        onVrCfg = { new0 ->
                            val nc = new0.clamped(); val old = vrCfg
                            // VR8: the Red Engine switch is a plain toggle, no wall. Switching it on also takes the remedial not yet started off the list.
                            vrCfg = nc
                            scope.launch(Dispatchers.IO) {
                                val r = app.efa.data.VrRepo(db); r.saveConfig(nc)
                                if (nc.redEngine && !old.redEngine) { r.removeOpenRemedial(); withContext(Dispatchers.Main) { reload++ } }
                            }
                        },
                        onHome = { screen = Screen.MAP_HOME },
                        onVenue = { mapVenue = it; venueBack = Screen.SETUP; screen = Screen.MAP_VENUE },
                        onBack = { screen = Screen.HOME },
                        onGit = { cfg -> scope.launch(Dispatchers.IO) { GitConfigRepo(db).save(cfg); reload++ } },
                        blocking = BlockingStatus(serviceOn, blockList?.size),
                        notifications = NotificationsStatus(listenerOn, noticeOn),
                        onOpenNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onAllowBufferNotice = { if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else { noticeOn = BufferNotifier.allowed(applicationContext); BufferNotifier.update(applicationContext) } },
                        onOpenAccessibility = { openAccessibilitySettings(applicationContext) },
                        onChange = { screen = Screen.CHANGE },
                        onMorning = { morningMessage = null; screen = Screen.MORNING },
                        onWidgets = { widgetMessage = null; screen = Screen.WIDGETS },
                        browsers = browsers,
                        onPc = { screen = Screen.PC },
                        look = look,
                        onLook = { icons, grid -> val next = look.change(clock.toLocalDate(), icons, grid); look = next; scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_LOOK, next.encode())) } },
                        appearance = appearance,
                        onAppearance = { m -> appearance = m; scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_APPEARANCE, m.name)) } },
                        myUoBNote = myUoBNote,
                        healthText = healthText,
                        onHealth = { try { healthPermission.launch(HealthLink.READ_SLEEP) } catch (e: Exception) { healthText = "Health Connect could not be opened on this phone." } },
                        onMyUoB = {                                                                // L7: opens the app, reads nothing, gates nothing
                            val pm = packageManager
                            val web = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            val installed = pm.queryIntentActivities(web, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                            val pkg = findMyUoB(installed)
                            val launch = pkg?.let { pm.getLaunchIntentForPackage(it) }
                            if (launch != null) { myUoBNote = null; startActivity(launch) } else myUoBNote = "MyUoB is not installed on this phone. Install it from the Play Store, then press this again."
                        },
                        pcText = if (pcAddress.isBlank()) "" else "Linked to $pcAddress. $pcStatus",
                        voiceQuietOff = voiceQuietOff,
                        onVoiceQuiet = { v -> scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_VOICE_QUIET_OFF, if (v) "1" else "0")); reload++ } },
                        codeReminders = codeReminders,
                        photoReminders = photoReminders,
                        mealChecks = mealChecks,
                        onMealChecks = { v -> scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_MEAL_CHECKS, if (v) "1" else "0")); MealCheckAlarm.refresh(applicationContext); reload++ } },
                        quietMode = quietMode,
                        onQuietMode = { on ->
                            if (on) scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_QUIET_MODE, "1")); BlockingState.refresh(applicationContext); reload++ }
                            else {                                                                               // QM1: turning it off lets notifications through again, so it needs the wall
                                wallBack = Screen.SETUP; wallLength = efa.WallLength.QUIET_OFF
                                wall = Triple("Turn quiet mode off", "Quiet mode holds every notification except your essentials while you work and at night. Turning it off lets them through again, so it needs the wall. Type the characters below by hand.", {
                                    scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_QUIET_MODE, "0")); BlockingState.refresh(applicationContext); reload++ }
                                })
                                screen = Screen.GATE
                            }
                        },
                        onPhotoReminders = { v -> scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_PHOTO_REMINDERS, if (v) "1" else "0")); PhotoAlarm.refresh(applicationContext); reload++ } },
                        onCodeReminders = { v -> scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_CODE_REMINDERS, if (v) "1" else "0")); LectureCodeAlarm.refresh(applicationContext); reload++ } },
                        onShopping = { screen = Screen.SHOPPING },
                        morningText = when { morningUi.night != null && morningUi.ready != null -> "Set up: tonight's photo and the ready spot."; morningUi.ready != null -> "Ready spot set. Take tonight's setup photo."; morningUi.night != null -> "Tonight's photo set. Take the ready spot photo."; else -> "" },
                        sleep = l?.sleep ?: SleepStatus(),
                        onChooseList = {
                            scope.launch(Dispatchers.IO) { apps = installedApps(applicationContext); withContext(Dispatchers.Main) { screen = if (blockList == null) Screen.BLOCKLIST else Screen.LIST_GATE } }
                        })
                    Screen.SEARCH -> SearchScreen(apps,
                        icons = look.effective(clock.toLocalDate()).icons, grid = look.effective(clock.toLocalDate()).grid,
                        iconFor = { pkg -> runCatching { packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() },
                        blockedReason = { pkg -> (BlockingState.decide(pkg) as? Decision.Block)?.let { "${it.label}, until ${DateTimeFormatter.ofPattern("HH:mm").format(it.until)}" } },
                        onOpen = { a -> packageManager.getLaunchIntentForPackage(a.pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                        onBack = { screen = Screen.HOME })
                    Screen.DIGEST -> DigestScreen(digestState, LONDON,
                        onOpenApp = { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                        onSeen = { scope.launch(Dispatchers.IO) { HeldRepo(db).markReviewed(digestState.ready.map { it.id }, System.currentTimeMillis()); reload++ } },
                        onBack = { screen = Screen.HOME })
                    Screen.IMPORT -> ImportScreen(importUi, imports,
                        onChoose = { chooseFile.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "text/plain")) },
                        onCopyFormat = {
                            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Spark timetable format", TIMETABLE_AI_PROMPT))
                            importUi = ImportUi.Done(listOf("Copied. Paste it into an AI app, then paste your timetable under it."))
                        },
                        onMode = setImportMode, meter = importMeter, economy = importEconomy,
                        onApply = {
                            val ui = importUi as? ImportUi.Checked
                            val v = ui?.verification
                            if (ui != null && v != null) scope.launch(Dispatchers.IO) {
                                val repo = app.efa.data.ChangeRepo(db)
                                val d = decideChange(repo.history(), importChangeClass(v, ui.mode), true, java.time.Instant.now(), repo.installedAt())
                                withContext(Dispatchers.Main) {
                                    when (d) {
                                        is ChangeDecision.Cooldown -> importUi = ImportUi.Failed(d.reason + " It opens again " + java.time.ZonedDateTime.ofInstant(d.until, LONDON).format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm")) + ".")
                                        is ChangeDecision.Locked -> importUi = ImportUi.Failed(d.reason)
                                        is ChangeDecision.Wall -> { wallBack = Screen.IMPORT; wallLength = maxOf(d.length, if (v.needsWall) efa.WallLength.IMPORT_LOOSEN else 0); wallDetails = if (v.needsWall) v.loosening else emptyList(); wall = Triple("Apply this file", d.reason + (if (v.needsWall) " " + efa.loosenWords(v.loosening) else "") + " Type the characters below by hand to apply it.", applyChecked); screen = Screen.GATE }
                                        is ChangeDecision.Free -> if (v.needsWall) { wallDetails = v.loosening; wall = Triple("Apply a file that loosens", efa.loosenWords(v.loosening) + " Type the characters below by hand to apply it.", applyChecked); screen = Screen.IMPORT_WALL } else applyChecked()
                                    }
                                }
                            }
                        },
                        onRemove = { row ->
                            if (System.currentTimeMillis() - row.atMs < 24L * 3_600_000L) {
                                // CE8: an undo within 24 hours is free and gives the count back
                                scope.launch(Dispatchers.IO) { PlanRepo(db).removeImport(row.id); app.efa.data.ChangeRepo(db).refund(row.atMs); BlockingState.refresh(applicationContext); reload++ }
                            } else {
                                wallDetails = emptyList(); wall = Triple("Remove an import", "Removing ${row.name} can take away a commitment or a deadline, so it needs the wall.", {
                                    scope.launch(Dispatchers.IO) { PlanRepo(db).removeImport(row.id); BlockingState.refresh(applicationContext); reload++ }
                                }); screen = Screen.IMPORT_WALL
                            }
                        },
                        onBack = { importUi = ImportUi.Idle; screen = Screen.CHANGE })
                    Screen.IMPORT_WALL -> wall?.let { w -> ImportWallGate(w.first, w.second, onPassed = { screen = Screen.IMPORT; w.third() }, onCancel = { screen = Screen.IMPORT }, details = wallDetails) } ?: run { screen = Screen.IMPORT }
                    Screen.MORNING -> MorningSetupScreen(morningUi, morningMessage,
                        onTake = { kind -> cameraKind = kind; morningMessage = null; screen = Screen.MORNING_CAMERA },
                        onClearStanding = { scope.launch(Dispatchers.IO) { ReferenceRepo(applicationContext, db).clear(RefKind.STANDING); refreshMorning() } },
                        onBack = { screen = Screen.SETUP },
                        onAllowSteps = { askSteps.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
                    Screen.MORNING_CAMERA -> CameraCapture(
                        onJpeg = { jpeg ->
                            scope.launch(Dispatchers.IO) {
                                morningMessage = when (ReferenceRepo(applicationContext, db).set(cameraKind, jpeg, System.currentTimeMillis())) {
                                    is RefResult.Saved -> "Saved. It replaces the old one."
                                    RefResult.TooDark -> "That photo shows nothing to match, it is too dark or blank. Turn a light on and take it again."
                                    RefResult.Unreadable -> "The photo could not be read. Take it again."
                                }
                                refreshMorning(); screen = Screen.MORNING
                            }
                        },
                        onCancel = { screen = Screen.MORNING })
                    Screen.MODIFY -> ModifyHomeScreen(modRows, clock.toLocalDate(),
                        onPick = { p ->
                            val today = clock.toLocalDate()
                            val first = if (p == Preset.LATE_START) today.plusDays(1) else today          // a late start is for a morning that has not come yet
                            modDraft = Instance(p, first, if (p == Preset.ASSIGNMENT || p == Preset.REVISION) today.plusDays(60) else first.plusDays(if (p == Preset.FOCUS) 4 else if (p == Preset.LATE_START || p == Preset.RUN_OVER || p == Preset.ADD_EVENT || p == Preset.CANCEL) 0 else if (p == Preset.GUESTS) 0 else if (p == Preset.AWAY) 2 else 1), emptyMap(), if (p == Preset.ASSIGNMENT || p == Preset.FOCUS || p == Preset.REVISION) listOf(mapOf("title" to "")) else emptyList())
                            modPreview = null; modRec = emptyMap(); modMessage = null; screen = Screen.MOD_FORM
                        },
                        onRemove = { r -> scope.launch(Dispatchers.IO) { app.efa.data.ModifierRepo(db).remove(r.id); BlockingState.refresh(applicationContext); AlarmScheduler.refresh(applicationContext); reload++; modMessage = "Removed." } },
                        onAdmin = { screen = Screen.ADMIN }, onEssentials = { essUi = essUi.copy(message = null); screen = Screen.ESSENTIALS }, message = modMessage, dropped = modDropped, onBack = { screen = if (modifyFromHome) Screen.HOME else Screen.CHANGE; modifyFromHome = false },
                        onQuick = { inst ->
                            modDraft = inst; modRec = emptyMap(); modMessage = null; modPreview = null; screen = Screen.MOD_FORM
                            scope.launch(Dispatchers.IO) { modPreview = app.efa.data.ModifierRepo(db).preview(inst, ZonedDateTime.now(LONDON)) }     // one tap to the preview
                        })
                    Screen.MOD_FORM -> modDraft?.let { draft ->
                        ModifyFormScreen(draft, clock.toLocalDate(), modPreview, modRec, modMessage,
                            onChange = { modDraft = it; modPreview = null },
                            onRecommend = {
                                scope.launch(Dispatchers.IO) {
                                    val base = PlanRepo(db).slots(); val cal = CalibrationRepo(db).calibration()
                                    val backlog = db.tasks().open().filter { it.source == "missed" }.sumOf { it.remainingHours() }
                                    val recs = LinkedHashMap(recommend(draft.preset, base, draft.start, draft.end, backlog, cal))
                                    var d2 = draft
                                    for ((k, r) in recs) if (ModDefs.settings(draft.preset).any { it.key == k } && d2.settings[k] == null) d2 = d2.copy(settings = d2.settings + (k to r.value))
                                    // each item: hours and lead time from heaviness and the person's own times
                                    if (draft.preset == Preset.ASSIGNMENT) d2 = d2.copy(items = d2.items.map { m -> val h = (m["heaviness"] ?: "5").toIntOrNull() ?: 5
                                        val r = recommendAssignment(h, cal, (recs["daily_cap_h"]?.value?.toDoubleOrNull() ?: 4.0)); recs.putAll(r.mapKeys { (k, _) -> "${m["title"].orEmpty().ifBlank { "assignment" }} ${k}" })
                                        m + r.mapValues { it.value.value }.filterKeys { k -> m[k] == null } })
                                    modDraft = d2; modRec = recs; modPreview = null
                                }
                            },
                            onPreview = { scope.launch(Dispatchers.IO) { modPreview = app.efa.data.ModifierRepo(db).preview(draft, ZonedDateTime.now(LONDON)) } },
                            onApply = {
                                val p = modPreview
                                if (p != null && p.canApply) {
                                    val now = ZonedDateTime.now(LONDON)
                                    scope.launch(Dispatchers.IO) {
                                        val repo = app.efa.data.ModifierRepo(db)
                                        val changes = app.efa.data.ChangeRepo(db); val doors = app.efa.data.DoorRepo(db)
                                        val cls = changeClassOfInstance(draft, p.wallNeeded, p.compiled.tasks.isNotEmpty())
                                        val decision = decideChange(changes.history(), cls, false, now.toInstant(), changes.installedAt())
                                        val obligation = obligationMinutesOf(draft, p.compiled)
                                        val door = if (obligation > 0) doors.decide(obligationDoor(if (draft.preset == Preset.PERIOD_OFF) Door.DAY_OFF else Door.RECOVERY, now.toInstant(), obligation), now.toInstant()) else null
                                        if (decision is ChangeDecision.Locked) { modMessage = decision.reason; return@launch }
                                        if (door is DoorDecision.Locked) { modMessage = door.reason; return@launch }
                                        var len = if (p.compiled.dayOffDates.isNotEmpty()) repo.periodOffWall(p.compiled.dayOffDates.size, now, p.compiled.dayOffDates.first() == now.toLocalDate()) else efa.WallLength.IMPORT_LOOSEN
                                        if (decision is ChangeDecision.Wall) len = maxOf(len, decision.length)
                                        if (door is DoorDecision.Open) len = maxOf(len, door.wallLength)
                                        val needsWall = p.wallNeeded || decision is ChangeDecision.Wall
                                        val doApply = { scope.launch(Dispatchers.IO) { try {
                                            val id = repo.apply(draft, ZonedDateTime.now(LONDON), len)
                                            if (cls == ChangeClass.LOOSEN || cls == ChangeClass.MOVE) changes.record(cls, false, id)          // CE3, CE4: counted like an import
                                            if (obligation > 0) doors.record(obligationDoor(if (draft.preset == Preset.PERIOD_OFF) Door.DAY_OFF else Door.RECOVERY, java.time.Instant.now(), obligation))          // DP2
                                            BlockingState.refresh(applicationContext); AlarmScheduler.refresh(applicationContext); reload++; modMessage = "Applied."; withContext(Dispatchers.Main) { screen = Screen.MODIFY } } catch (e: Exception) { modMessage = "Not applied: ${e.message}" } }; Unit }
                                        withContext(Dispatchers.Main) {
                                            if (needsWall) { wallBack = Screen.MOD_FORM; wallLength = len; wall = Triple("Apply ${draft.preset.label}", (p.compiled.loosening.joinToString(". ").ifBlank { (decision as? ChangeDecision.Wall)?.reason.orEmpty() }) + ". Type the characters below by hand to apply it.", { doApply() }); screen = Screen.GATE } else doApply()
                                        }
                                    }
                                }
                            },
                            onBack = { screen = Screen.MODIFY })
                    } ?: run { screen = Screen.MODIFY }
                    Screen.WALKTHROUGH -> WalkthroughScreen(firstRun = false, onFinish = { screen = Screen.SETUP })
                    Screen.CAME_UP -> CameUpScreen(db,
                        onPlan = { inst -> modifyFromHome = true; modDraft = inst; modRec = emptyMap(); modMessage = null; modPreview = null; screen = Screen.MOD_FORM
                            scope.launch(Dispatchers.IO) { modPreview = app.efa.data.ModifierRepo(db).preview(inst, ZonedDateTime.now(LONDON)) } },
                        onBack = { screen = Screen.HOME })
                    Screen.ADMIN -> AdminScreen(adminState, adminProblems,
                        onLoadSet = { l -> saveAdmin(adminState.copy(loadSet = l, on = emptySet(), off = emptySet())) },
                        onToggle = { j -> val on = adminState.enabled(j); saveAdmin(adminState.copy(on = if (on) adminState.on - j.id else adminState.on + j.id, off = if (on) adminState.off + j.id else adminState.off - j.id)) },
                        onCap = { v -> saveAdmin(adminState.copy(capMin = v.coerceIn(15, 300))) },
                        onLighter = { saveAdmin(adminState.copy(lighter = !adminState.lighter)) },
                        onTrim = {
                            if (adminState.trimWork) saveAdmin(adminState.copy(trimWork = false))
                            else { wallBack = Screen.ADMIN; wallLength = efa.WallLength.IMPORT_LOOSEN; wall = Triple("Let admin trim work", "Letting small jobs take minutes from work blocks loosens the plan, so it needs the typed wall.", { saveAdmin(adminState.copy(trimWork = true)) }); screen = Screen.GATE }
                        },
                        onBack = { screen = Screen.MODIFY })
                    Screen.ESSENTIALS -> EssentialsScreen(essUi,
                        onOpenWindow = { wallBack = Screen.ESSENTIALS; wallLength = efa.ESSENTIALS_ADD_WALL; wall = Triple("Add to the essentials", "Adding an app to the essentials loosens blocking. Type the characters below by hand. You then have 5 minutes to add up to 3 apps.", { scope.launch(Dispatchers.IO) { EssentialsRepo(applicationContext, db).openWindow(System.currentTimeMillis()); reload++ } }); screen = Screen.GATE },
                        onAdd = { a -> scope.launch(Dispatchers.IO) { val ok = EssentialsRepo(applicationContext, db).add(a.pkg, System.currentTimeMillis()); BlockingState.refresh(applicationContext); essUi = essUi.copy(message = if (ok) "${a.label} is now an essential." else "The window is closed or full."); reload++ } },
                        onRemove = { a -> scope.launch(Dispatchers.IO) { EssentialsRepo(applicationContext, db).remove(a.pkg); BlockingState.refresh(applicationContext); reload++ } },
                        onBack = { screen = Screen.MODIFY })
                    Screen.PC -> PcScreen(pcAddress, pcCode, pcStatus,
                        onSave = { a, k -> scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_PC_ADDRESS, a)); db.meta().put(MetaRow(app.efa.data.META_PC_CODE, k)); PcLink.push(applicationContext); reload++ } },
                        onSend = { scope.launch(Dispatchers.IO) { PcLink.push(applicationContext); reload++ } },
                        onBack = { screen = Screen.SETUP })
                    Screen.WIDGETS -> {
                        val edit: (((List<Placement>, LocalDate) -> List<Placement>), String?) -> Unit = { f, msg ->
                            scope.launch(Dispatchers.IO) {
                                val repo = WidgetRepo(db); val today = ZonedDateTime.now(LONDON).toLocalDate()
                                repo.save(f(repo.placements(today), today)); widgetMessage = msg; reload++
                            }
                        }
                        EditWidgetsScreen(widgetRows, WidgetKind.entries.filter { k -> widgetRows.none { it.kind == k } }, widgetMessage,
                            onAdd = { k -> edit({ l, d -> addWidget(l, k, WidgetSize.TWO_BY_ONE, d) }, "${k.label} starts tomorrow.") },
                            onRemove = { k -> edit({ l, _ -> removeWidget(l, k) }, "${k.label} removed.") },
                            onMove = { k, by -> edit({ l, _ -> moveWidget(l, k, by) }, null) },
                            onSize = { k, sz -> edit({ l, d -> resizeWidget(l, k, sz, d) }, null) },
                            onBack = { screen = Screen.SETUP })
                    }
                    Screen.SHOPPING -> ShoppingScreen(shopping,
                        onTick = { r -> scope.launch(Dispatchers.IO) { app.efa.data.ShoppingRepo(db).tick(r); reload++ } },
                        onPrice = { r, p -> scope.launch(Dispatchers.IO) { app.efa.data.ShoppingRepo(db).price(r, p); reload++ } },
                        onNewShop = { scope.launch(Dispatchers.IO) { app.efa.data.ShoppingRepo(db).newShop(); reload++ } },
                        onBack = { screen = Screen.HOME })
                    Screen.REVIEW -> review?.let { r ->
                        ReviewScreen(r,
                            onReport = { rep ->
                                val row = reviewRow
                                if (row != null && rep is TookReport.LessFraction) {                     // R10: saying it took less loosens, so it goes through the wall
                                    wallBack = Screen.HOME; wallLength = WallLength.CALIBRATE_LESS
                                    wall = Triple("It took less", "Saying a task took less makes later estimates smaller and the Buffer looser. Type the characters below by hand.", {
                                        scope.launch(Dispatchers.IO) { CalibrationRepo(db).report(row, rep, System.currentTimeMillis()); reload++ }
                                    }); screen = Screen.GATE
                                } else if (row != null) scope.launch(Dispatchers.IO) { CalibrationRepo(db).report(row, rep, System.currentTimeMillis()); BlockingState.refresh(applicationContext); reload++; withContext(Dispatchers.Main) { screen = Screen.HOME } }
                            },
                            onBack = { screen = Screen.HOME })
                    } ?: run { screen = Screen.HOME }
                    Screen.LIBRARY -> libraryUi?.let { u ->
                        LibraryScreen(u,
                            onHours = { o, c2 -> scope.launch(Dispatchers.IO) { PlanRepo(db).saveLibraryHours(db, LibraryHours(o, c2)); refreshLibrary(u.start, u.days, "Hours saved: ${LibraryHours(o, c2).text()}", false, o, c2) } },
                            onEditHours = { refreshLibrary(u.start, u.days, null, true, u.hours?.openMin ?: 8 * 60, u.hours?.closeMin ?: 22 * 60) },
                            onDraft = { o, c2 -> libraryUi = u.copy(draftOpen = o, draftClose = c2) },
                            onPick = { d, n -> refreshLibrary(d, n, null, false, u.draftOpen, u.draftClose) },
                            onPlan = {
                                val st = u.start
                                if (st != null) scope.launch(Dispatchers.IO) {
                                    val err = PlanRepo(db).addLibraryPeriod(db, st, st.plusDays(u.days - 1L), ZonedDateTime.now(LONDON))
                                    BlockingState.refresh(applicationContext); AlarmScheduler.refresh(applicationContext); reload++
                                    refreshLibrary(null, u.days, err ?: "Planned.", false, u.draftOpen, u.draftClose)
                                }
                            },
                            onRemove = { id -> scope.launch(Dispatchers.IO) { PlanRepo(db).removeLibraryPeriod(db, id); BlockingState.refresh(applicationContext); reload++; refreshLibrary(null, u.days, "Removed.", false, u.draftOpen, u.draftClose) } },
                            onBack = { screen = Screen.CHANGE })
                    } ?: run {                                                      // CD1: the screen opens before its data is read, so it waits for it (and gives up after 3 s) instead of bouncing back
                        Box(Modifier.fillMaxSize().background(LocalColors.current.bg))
                        LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); if (libraryUi == null) screen = Screen.CHANGE }
                    }
                    Screen.DAYOFF -> dayOffUi?.let { u ->
                        DayOffScreen(u,
                            onPick = { dates -> refreshDayOff(dates, null, false) },
                            onIll = { dayOffSource = "illness"; refreshDayOff(listOf(LocalDate.now(LONDON)), null, false) },
                            onClaim = { d ->
                                scope.launch(Dispatchers.IO) {
                                    val plan = PlanRepo(db); val now = ZonedDateTime.now(LONDON)
                                    val len = dayOffWall(plan.uses(db), now, sameDay = true)               // H6: the same string plus the same-day surcharge
                                    withContext(Dispatchers.Main) {
                                        wallBack = Screen.DAYOFF; wallLength = len
                                        wall = Triple("Claim ${DateTimeFormatter.ofPattern("EEE d MMM").format(d)} as a day off", "You forgot to ask, so that day counts as lost until the work that came back from it is done by its due times. Type the characters below by hand to claim it. It costs the same string plus 40.", {
                                            scope.launch(Dispatchers.IO) { plan.addDaysOff(db, listOf(d), "claim", len, ZonedDateTime.now(LONDON), sameDay = true); BlockingState.refresh(applicationContext); reload++; refreshDayOff(emptyList(), "Claimed. It holds when the work that came back from that day is done by its due times.", false) }
                                        }); screen = Screen.GATE
                                    }
                                }
                            },
                            onPlan = {
                                val p = u.preview
                                if (p != null) {
                                    val source = if (dayOffSource == "illness") "illness" else if (p.dates.size == 2) "weekend" else "planned"
                                    val sameDay = p.dates.first() == u.today
                                    wallBack = Screen.DAYOFF; wallLength = p.wall
                                    wall = Triple("Plan a day off", p.lines.first() + ". Type the characters below by hand to plan it.", {
                                        scope.launch(Dispatchers.IO) { PlanRepo(db).addDaysOff(db, p.dates, source, p.wall, ZonedDateTime.now(LONDON), sameDay); dayOffSource = "planned"; BlockingState.refresh(applicationContext); AlarmScheduler.refresh(applicationContext); reload++; refreshDayOff(emptyList(), "Planned.", false) }
                                    }); screen = Screen.GATE
                                }
                            },
                            onBack = { screen = Screen.CHANGE })
                    } ?: run {                                                      // CD1: the screen opens before its data is read, so it waits for it (and gives up after 3 s) instead of bouncing back
                        Box(Modifier.fillMaxSize().background(LocalColors.current.bg))
                        LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); if (dayOffUi == null) screen = Screen.CHANGE }
                    }
                    Screen.MONEY -> l?.money?.let { MoneyScreen(it) { screen = Screen.HOME } } ?: run { screen = Screen.HOME }
                    Screen.GATE -> wall?.let { w -> ImportWallGate(w.first, w.second, onPassed = { w.third(); screen = wallBack }, onCancel = { screen = wallBack }, length = wallLength, details = wallDetails) } ?: run { screen = Screen.HOME }
                    Screen.READY -> {
                        // Read the morning fresh on every entry: it may have changed since the last tick, and an old copy would send you home.
                        var fresh by remember { mutableStateOf(false) }
                        LaunchedEffect(reload) {
                            withContext(Dispatchers.IO) { morning = try { MorningRepo(db).latest() } catch (e: Exception) { morning }; hasReadyRef = ReferenceRepo(applicationContext, db).status().containsKey(RefKind.READY) }
                            fresh = true
                        }
                        val m = morning
                        val date = m?.let { java.time.LocalDate.parse(it.date) }
                        if (!fresh) Box(Modifier.fillMaxSize().background(LocalColors.current.bg))
                        else if (m == null || date == null || m.stoppedMs == null || m.readyMs != null) { screen = Screen.HOME }
                        else {
                            var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                            LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); nowMs = System.currentTimeMillis() } }
                            val offer = nextOffer(m.readyFailed, !hasReadyRef, 0, false, m.readyWaitStartedMs, nowMs)
                            LaunchedEffect(offer is Offer.Wait) { if (offer is Offer.Wait && m.readyWaitStartedMs == null) withContext(Dispatchers.IO) { MorningRepo(db).startReadyWait(date, System.currentTimeMillis()); reload++ } }
                            val msg = readyMessage ?: if (!hasReadyRef) "No ready spot photo is set, so it cannot be checked. Wait instead." else null
                            ReadyScreen(AlarmUi("", offer, msg, (offer as? Offer.Wait)?.let { ((it.untilMs - nowMs) / 1000).toInt().coerceAtLeast(0) }),
                                onTakePhoto = { readyMessage = null; screen = Screen.READY_CAMERA },
                                onCameraBroken = { scope.launch(Dispatchers.IO) { MorningRepo(db).let { r -> r.readyFailed(date); r.readyFailed(date) }; reload++ } },
                                onDone = { method -> scope.launch(Dispatchers.IO) { MorningRepo(db).ready(date, method, System.currentTimeMillis()); BlockingState.refresh(applicationContext); reload++; screen = Screen.HOME } },
                                onBack = { screen = Screen.HOME })
                        }
                    }
                    Screen.READY_CAMERA -> CameraCapture(
                        onJpeg = { jpeg ->
                            scope.launch(Dispatchers.IO) {
                                val m = morning; val date = m?.let { java.time.LocalDate.parse(it.date) }
                                if (date == null) { screen = Screen.HOME; return@launch }
                                val r = ReferenceRepo(applicationContext, db).match(listOf(RefKind.READY), jpeg, System.currentTimeMillis())
                                if (r.result.passed) { MorningRepo(db).ready(date, StopMethod.PHOTO, System.currentTimeMillis()); BlockingState.refresh(applicationContext); readyMessage = null; reload++; screen = Screen.HOME }
                                else { MorningRepo(db).readyFailed(date); readyMessage = if (r.result.tooDark) "That photo is too dark to check. Turn a light on." else "That does not match your ready spot (score ${r.result.score}, it needs ${MATCH_PASS_SCORE})."; reload++; screen = Screen.READY }
                            }
                        },
                        onCancel = { screen = Screen.READY })
                    Screen.LIST_GATE ->ListWallGate(onPassed = { screen = Screen.BLOCKLIST }, onCancel = { screen = Screen.SETUP })
                    Screen.BLOCKLIST -> BlockListScreen(apps, blockList,
                        onSave = { chosen -> scope.launch(Dispatchers.IO) { val bad = essentialsIn(chosen, EssentialsRepo(applicationContext, db).resolve()); if (bad.isNotEmpty()) { withContext(Dispatchers.Main) { android.widget.Toast.makeText(applicationContext, "These are essentials and cannot be blocked: " + apps.filter { it.pkg in bad }.joinToString { it.label }, android.widget.Toast.LENGTH_LONG).show() }; return@launch }; BlockingRepo(db).saveList(chosen); reload++; withContext(Dispatchers.Main) { screen = if (setupNeeded) Screen.HOME else Screen.SETUP } } },
                        onCancel = { screen = if (setupNeeded) Screen.HOME else Screen.SETUP })
                    Screen.MAP_HOME -> MapPicker("Set home", "Move the map until the cross is on your home area. It is saved to about 100 m, never a house.",
                        places.home ?: CAMPUS_CENTRE, maxZoom = 16.0,
                        onPick = { lat, lon -> scope.launch(Dispatchers.IO) { PlaceRepo(db).setHome(lat, lon); reload++; screen = Screen.SETUP } },
                        onCancel = { screen = Screen.SETUP })
                    Screen.MAP_VENUE -> mapVenue?.let { v -> MapPicker("Place ${v.venueText}", "Move the map until the cross is on the building.",
                        v.point ?: CAMPUS_CENTRE, maxZoom = 19.0,
                        onPick = { lat, lon -> scope.launch(Dispatchers.IO) { PlaceRepo(db).setVenuePoint(v.venueText, lat, lon, System.currentTimeMillis()); reload++; screen = venueBack; venueBack = Screen.SETUP } },
                        onCancel = { screen = venueBack; venueBack = Screen.SETUP }) } ?: run { screen = Screen.SETUP }
                    Screen.QUICK_ADD -> QuickAddScreen(
                        now = clock,
                        bufferIfAdded = { t -> l?.let { bufferWith(it, clock, t).let { s -> if (s is BufferState.Value) s.result.label() + " to " + s.result.deadlineLabel else "No deadline" } } ?: "Schedule unavailable" },
                        canvasInstalled = packageManager.getLaunchIntentForPackage(CANVAS_PACKAGE) != null,
                        onCheckCanvas = { packageManager.getLaunchIntentForPackage(CANVAS_PACKAGE)?.let { startActivity(it) } },   // D3: opens the app, reads nothing
                        prefill = voiceDraft?.first, heard = voiceDraft?.second,
                        onSave = { t ->
                            voiceDraft = null
                            scope.launch(Dispatchers.IO) {
                                db.tasks().insert(TaskRow(title = t.title, dueMs = t.due.toInstant().toEpochMilli(), dueZone = t.due.zone.id,
                                    heaviness = t.heaviness, hoursEstimate = CalibrationState.cal.hours(t.heaviness), createdMs = System.currentTimeMillis()))
                                reload++
                                screen = Screen.HOME
                            }
                        },
                        onCancel = { voiceDraft = null; screen = Screen.HOME },
                    )
                }
            }
        }
    }
}

/** Study cycles from the week plus stored manual tasks, then the Buffer (B1). Short-sleep is false until sleep logging exists (layer 2). */
private fun bufferOf(l: Loaded, now: ZonedDateTime, extra: DueWork? = null): BufferState {
    val pattern = l.pattern ?: return BufferState.NoDeadline
    val manual = l.tasks.map { it.toDue() }
    return bufferNow(now, studyCycles(pattern, now) + manual + listOfNotNull(extra), pattern, shortSleep = l.shortSleep)       // B8: short sleep counts remaining work heavier
}

private fun bufferWith(l: Loaded, now: ZonedDateTime, t: NewTask): BufferState =
    bufferOf(l, now, DueWork(t.title, CalibrationState.cal.hours(t.heaviness), t.heaviness, t.due))
