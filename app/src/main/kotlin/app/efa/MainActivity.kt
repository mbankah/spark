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
private enum class Screen { HOME, WORKING, QUICK_ADD, BLOCK, SETUP, MAP_HOME, MAP_VENUE, LIST_GATE, BLOCKLIST, DIGEST, SEARCH, IMPORT, IMPORT_WALL, MORNING, MORNING_CAMERA, READY, READY_CAMERA, MONEY, GATE, DAYOFF, LIBRARY, REVIEW, SHOPPING, WIDGETS }

private val CAMPUS_CENTRE = Pair(52.4508, -1.9305)   // Edgbaston campus: where map pickers open before anything is set

/** Everything the screens need, loaded from the database. null pattern means the database failed (X7). */
private class Loaded(val pattern: List<PatternSlot>?, val tasks: List<TaskRow>, val verified: Set<String> = emptySet(), val periods: List<PlannedPeriodRow> = emptyList(), val moved: List<String> = emptyList(), val sleep: SleepStatus = SleepStatus(),
    val shortSleep: Boolean = false, val money: MoneyView? = null, val earnings: List<Earning> = emptyList())

class MainActivity : ComponentActivity() {
    /** An action asked for by the Buffer notification (LA11), consumed by the screen state once. */
    private val pendingAction = mutableStateOf<String?>(null)

    /** V1: the text the phone's speech recogniser heard, read once by the screen state. */
    private val voiceHeard = mutableStateOf<String?>(null)
    private val voiceLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        voiceHeard.value = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
    }
    private val voiceMessage = mutableStateOf<String?>(null)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
            EfaTheme {
                var screen by remember { mutableStateOf(Screen.HOME) }
                var reload by remember { mutableIntStateOf(0) }
                var loaded by remember { mutableStateOf<Loaded?>(null) }
                var places by remember { mutableStateOf(PlacesState(null, emptyList())) }
                var blockList by remember { mutableStateOf<Set<String>?>(null) }
                var serviceOn by remember { mutableStateOf(isBlockerEnabled(applicationContext)) }
                var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
                var mapVenue by remember { mutableStateOf<VenueStatus?>(null) }
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
                var dayOffUi by remember { mutableStateOf<DayOffUi?>(null) }
                var libraryUi by remember { mutableStateOf<LibraryUi?>(null) }
                var review by remember { mutableStateOf<ReviewTask?>(null) }
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
                LaunchedEffect(screen, reload) { voiceQuietOff = withContext(Dispatchers.IO) { db.meta().get(app.efa.data.META_VOICE_QUIET_OFF) != "0" } }
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
                        val upcoming = db.daysOff().all().filter { LocalDate.parse(it.date) >= now.toLocalDate() }.map { r -> "${DateTimeFormatter.ofPattern("EEE d MMM").format(LocalDate.parse(r.date))}: ${r.source.replace('_', ' ')}${if (r.state == "pending") ", claim waiting" else ""}" }
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
                        ImportUi.Checked(inc.name, verifyImport(parseTimetableCsv(inc.text, now.toLocalDate()), plan.slots(), plan.known(), db.tasks().open().map { it.toDue() }, now, hoursFor = CalibrationState.cal::hours), fileHash(inc.text))
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
                /** IM11: one transaction; if it fails nothing was written and the screen says so. */
                val applyChecked: () -> Unit = {
                    val ui = importUi as? ImportUi.Checked
                    if (ui != null) scope.launch(Dispatchers.IO) {
                        importUi = try {
                            val v = ui.verification
                            PlanRepo(db).apply(v, ui.name, ui.fileHash, System.currentTimeMillis(), LONDON.id, v.summaryLines().joinToString(". "))
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
                                isShortSleepToday(db, nowZ), rewards.view(pattern, firstUse, nowZ), rewards.earnings(nowZ))
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
                    if (screen == Screen.DIGEST) digestState = withContext(Dispatchers.IO) { digest(HeldRepo(db).unreviewed()) { BlockingState.decide(it) } }
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

                when (screen) {
                    Screen.HOME -> if (!setupLoaded && SetupGate.enforced) Box(Modifier.fillMaxSize().background(LocalColors.current.bg))
                    else if (setupNeeded) SetupFlowScreen(doneSteps, restore = everDone, onAct = act,
                        onEssential = { launchEssential(it) }, onOpenSetup = { screen = Screen.SETUP },
                        crashNotice = launcherPaused, onRestoreLauncher = { CrashGuard.restore(applicationContext); launcherPaused = false; reload++ })
                    else {
                        val nn = l?.pattern?.let { nowAndNext(it, clock) }
                        val hhmm = DateTimeFormatter.ofPattern("HH:mm")
                        val period = l?.let { activePeriod(it.periods, clock.toLocalDate()) }
                        val alloc = l?.pattern?.let { p -> allocateTasks(p, l.tasks.map { it.toOpen(period) }, clock) }
                        // RS2, RS8: what is placed in a block is said in the block's own line, split across blocks when it is big.
                        val includes: (DatedSlot) -> String = { ds -> alloc?.inSlot(ds).orEmpty().takeIf { it.isNotEmpty() }?.joinToString(", ", "Includes: ") { "${it.task.title} (${it.minutes} min)" }.orEmpty() }
                        val phoneDecision = blockList?.takeIf { it.isNotEmpty() }?.let { BlockingState.decide(it.first()) }
                        HomeScreen(
                            HomeState(
                                date = DateTimeFormatter.ofPattern("EEE d MMM").format(clock),
                                phone = if (blockList.isNullOrEmpty()) null else if (phoneDecision is Decision.Block) "Phone closed" else "Phone open",
                                phoneOpen = phoneDecision !is Decision.Block,
                                time = hhmm.format(clock), buffer = buffer,
                                nowLabel = nn?.now?.let { "Now, until ${hhmm.format(it.end)}" },
                                nowTitle = nn?.now?.slot?.title,
                                nowDetail = nn?.now?.let { listOf(it.detail(), includes(it), proofHint(proofFor(it.slot))).filter { s -> s.isNotBlank() }.joinToString(". ") },
                                nowOpensBlock = nn?.now?.let { proofFor(it.slot) != Proof.NONE } == true,
                                next = l?.pattern?.let { p -> upcoming(p, clock, 2).map { NextRow(hhmm.format(it.start), it.slot.title, listOf(it.detail(), includes(it)).filter { s -> s.isNotBlank() }.joinToString(". ")) } }.orEmpty(),
                                freeToSpendPence = l?.money?.takeIf { it.live }?.week?.money?.freeToSpendPence,      // G6: record-only for the first 14 days
                                progress = l?.money?.week?.takeIf { it.plannedHours > 0 }?.progress(),
                                moneyNote = l?.money?.takeIf { !it.live }?.let { "Money starts on ${DateTimeFormatter.ofPattern("EEE d MMM").format(it.liveFrom)}. Until then it is only recorded." },
                                earnedOn = l?.pattern?.let { p -> earnedAccessActive(clock, l.earnings, p) } == true,
                                proofDue = l?.pattern?.let { p -> proofDue(p, clock, l.verified).firstOrNull() }?.let { "${it.slot.title}, ended ${hhmm.format(it.end)}. Take its photo." },
                                heldWaiting = heldWaiting,
                                unavailable = l != null && l.pattern == null,
                                launcherPaused = launcherPaused,
                                morning = when (morningPhase(morning?.toState(), clock)) {
                                    MorningPhase.WAITING_FOR_READY -> "Social is blocked until you take the ready photo. It ends by itself at ${hhmm.format(readyReleaseAt(morning!!.stoppedMs!!, clock.zone))} if you cannot."
                                    MorningPhase.RINGING -> "The alarm is ringing."
                                    else -> null
                                },
                                widgets = shownWidgets,
                                voiceNote = voiceMessage.value,
                                voice = voiceAllowed(l?.pattern?.let { p -> slotsFor(p, clock.toLocalDate(), clock.zone).firstOrNull { !it.start.isAfter(clock) && it.end.isAfter(clock) }?.slot }, voiceQuietOff),
                                week = l?.pattern?.let { weekGlance(it, clock.toLocalDate(), clock.zone) }.orEmpty(),
                                shoppingDay = l?.pattern?.let { p -> slotsFor(p, clock.toLocalDate()).any { it.slot.title.contains("Aldi", ignoreCase = true) } } == true,
                                review = review?.let { "How long did \"${it.title}\" take?" },
                                period = if (l?.pattern?.let { isDayOff(it, clock.toLocalDate()) } == true) "Day off today. Nothing counts as lost." else period?.let { "${PeriodKind.entries.first { k -> k.csv == it.kind }.label}, until ${DateTimeFormatter.ofPattern("EEE d MMM").format(java.time.LocalDate.parse(it.endDate))}" },
                                moved = l?.moved.orEmpty(),
                            ),
                            onEssential = { key -> launchEssential(key) },
                            onSearch = { scope.launch(Dispatchers.IO) { apps = installedApps(applicationContext); withContext(Dispatchers.Main) { screen = Screen.SEARCH } } },
                            onRestoreLauncher = { CrashGuard.restore(applicationContext); launcherPaused = false; reload++ },
                            onHeld = { screen = Screen.DIGEST },
                            onReady = { screen = Screen.READY },
                            onReview = { screen = Screen.REVIEW },
                            onShopping = { screen = Screen.SHOPPING },
                            onVoice = { voiceDraft = null; startVoice(emptyList()) },
                            onMoney = { screen = Screen.MONEY },
                            onNow = { nn?.now?.let { selected = it; screen = Screen.BLOCK } },
                            onProofDue = { l?.pattern?.let { p -> proofDue(p, clock, l.verified).firstOrNull() }?.let { selected = it; screen = Screen.BLOCK } },
                            onBuffer = { if (buffer is BufferState.Value) screen = Screen.WORKING },
                            onQuickAdd = { screen = Screen.QUICK_ADD },
                            onSetup = { screen = Screen.SETUP },
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
                            override suspend fun conversionNote(ds: DatedSlot): String? =
                                if (!isConversion(ds.slot)) null else db.overlay().conversion(ds.plannedDay.toString(), ds.slot.startMin)?.reason
                                    ?.let { "$it. A free slot is assigned the night before and does not change in the moment without the typed wall." }
                            override suspend fun currentFix(): Fix? = LocationFix.current(applicationContext)
                            override fun hasLocationPermission() = LocationFix.hasPermission(applicationContext)
                        }, onBack = { reload++; screen = Screen.HOME }, onTurnBack = { slot ->
                            wallBack = Screen.HOME; wallLength = efa.WallLength.FREE_SLOT_CHANGE
                            wall = Triple("Make it a break", "This was free time that the Buffer turned into work. Type the characters below by hand to turn it back. It will not be turned into work again.", {
                                scope.launch(Dispatchers.IO) { PlanRepo(db).turnBack(db, slot.plannedDay, slot.slot.startMin, slot.slot.endMin + RESET_MIN, System.currentTimeMillis()); BlockingState.refresh(applicationContext); reload++ }
                            }); screen = Screen.GATE
                        })
                    }
                    Screen.SETUP -> SetupScreen(places,
                        onHome = { screen = Screen.MAP_HOME },
                        onVenue = { mapVenue = it; screen = Screen.MAP_VENUE },
                        onBack = { screen = Screen.HOME },
                        onGit = { cfg -> scope.launch(Dispatchers.IO) { GitConfigRepo(db).save(cfg); reload++ } },
                        blocking = BlockingStatus(serviceOn, blockList?.size),
                        notifications = NotificationsStatus(listenerOn, noticeOn),
                        onOpenNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onAllowBufferNotice = { if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else { noticeOn = BufferNotifier.allowed(applicationContext); BufferNotifier.update(applicationContext) } },
                        onOpenAccessibility = { openAccessibilitySettings(applicationContext) },
                        onImport = { importUi = ImportUi.Idle; screen = Screen.IMPORT },
                        onMorning = { morningMessage = null; screen = Screen.MORNING },
                        onDaysOff = { refreshDayOff(emptyList(), null, false); screen = Screen.DAYOFF },
                        onLibrary = { refreshLibrary(null, 5, null, false, 8 * 60, 22 * 60); screen = Screen.LIBRARY },
                        onWidgets = { widgetMessage = null; screen = Screen.WIDGETS },
                        browsers = browsers,
                        voiceQuietOff = voiceQuietOff,
                        onVoiceQuiet = { v -> scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(app.efa.data.META_VOICE_QUIET_OFF, if (v) "1" else "0")); reload++ } },
                        onShopping = { screen = Screen.SHOPPING },
                        morningText = when { morningUi.night != null && morningUi.ready != null -> "Set up: tonight's photo and the ready spot."; morningUi.ready != null -> "Ready spot set. Take tonight's setup photo."; morningUi.night != null -> "Tonight's photo set. Take the ready spot photo."; else -> "" },
                        sleep = l?.sleep ?: SleepStatus(),
                        onChooseList = {
                            scope.launch(Dispatchers.IO) { apps = installedApps(applicationContext); withContext(Dispatchers.Main) { screen = if (blockList == null) Screen.BLOCKLIST else Screen.LIST_GATE } }
                        })
                    Screen.SEARCH -> SearchScreen(apps,
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
                        onApply = {
                            val v = (importUi as? ImportUi.Checked)?.verification
                            if (v != null && v.needsWall) {
                                wall = Triple("Apply a file that loosens", v.loosening.joinToString(". ") + ". Type the characters below by hand to apply it.", applyChecked); screen = Screen.IMPORT_WALL
                            } else applyChecked()
                        },
                        onRemove = { row ->
                            wall = Triple("Remove an import", "Removing ${row.name} can take away a commitment or a deadline, so it needs the wall.", {
                                scope.launch(Dispatchers.IO) { PlanRepo(db).removeImport(row.id); BlockingState.refresh(applicationContext); reload++ }
                            }); screen = Screen.IMPORT_WALL
                        },
                        onBack = { importUi = ImportUi.Idle; screen = Screen.SETUP })
                    Screen.IMPORT_WALL -> wall?.let { w -> ImportWallGate(w.first, w.second, onPassed = { screen = Screen.IMPORT; w.third() }, onCancel = { screen = Screen.IMPORT }) } ?: run { screen = Screen.IMPORT }
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
                            onBack = { screen = Screen.SETUP })
                    } ?: run { screen = Screen.SETUP }
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
                            onBack = { screen = Screen.SETUP })
                    } ?: run { screen = Screen.SETUP }
                    Screen.MONEY -> l?.money?.let { MoneyScreen(it) { screen = Screen.HOME } } ?: run { screen = Screen.HOME }
                    Screen.GATE -> wall?.let { w -> ImportWallGate(w.first, w.second, onPassed = { w.third(); screen = wallBack }, onCancel = { screen = wallBack }, length = wallLength) } ?: run { screen = Screen.HOME }
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
                        onSave = { chosen -> scope.launch(Dispatchers.IO) { BlockingRepo(db).saveList(chosen); reload++; withContext(Dispatchers.Main) { screen = if (setupNeeded) Screen.HOME else Screen.SETUP } } },
                        onCancel = { screen = if (setupNeeded) Screen.HOME else Screen.SETUP })
                    Screen.MAP_HOME -> MapPicker("Set home", "Move the map until the cross is on your home area. It is saved to about 100 m, never a house.",
                        places.home ?: CAMPUS_CENTRE, maxZoom = 16.0,
                        onPick = { lat, lon -> scope.launch(Dispatchers.IO) { PlaceRepo(db).setHome(lat, lon); reload++; screen = Screen.SETUP } },
                        onCancel = { screen = Screen.SETUP })
                    Screen.MAP_VENUE -> mapVenue?.let { v -> MapPicker("Place ${v.venueText}", "Move the map until the cross is on the building.",
                        v.point ?: CAMPUS_CENTRE, maxZoom = 19.0,
                        onPick = { lat, lon -> scope.launch(Dispatchers.IO) { PlaceRepo(db).setVenuePoint(v.venueText, lat, lon, System.currentTimeMillis()); reload++; screen = Screen.SETUP } },
                        onCancel = { screen = Screen.SETUP }) } ?: run { screen = Screen.SETUP }
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
