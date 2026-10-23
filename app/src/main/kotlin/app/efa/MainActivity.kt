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
import app.efa.data.LocationFix
import app.efa.data.PlaceRepo
import app.efa.data.VenueStatus
import app.efa.data.TaskRow
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
private enum class Screen { HOME, WORKING, QUICK_ADD, BLOCK, SETUP, MAP_HOME, MAP_VENUE, LIST_GATE, BLOCKLIST, DIGEST }

private val CAMPUS_CENTRE = Pair(52.4508, -1.9305)   // Edgbaston campus: where map pickers open before anything is set

/** Everything the screens need, loaded from the database. null pattern means the database failed (X7). */
private class Loaded(val pattern: List<PatternSlot>?, val tasks: List<TaskRow>, val verified: Set<String> = emptySet())

class MainActivity : ComponentActivity() {
    /** An action asked for by the Buffer notification (LA11), consumed by the screen state once. */
    private val pendingAction = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction.value = intent.getStringExtra(BufferNotifier.EXTRA_ACTION)
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
                val scope = rememberCoroutineScope()
                val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    noticeOn = BufferNotifier.allowed(applicationContext); BufferNotifier.update(applicationContext)
                }
                LaunchedEffect(pendingAction.value) { if (pendingAction.value == BufferNotifier.ACTION_QUICK_ADD) { screen = Screen.QUICK_ADD; pendingAction.value = null } }

                LaunchedEffect(reload) {
                    loaded = withContext(Dispatchers.IO) {
                        try {
                            db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
                            val pattern = db.pattern().all().map { it.toSlot() }
                            val repo = BlockRepo(applicationContext, db, pattern)
                            repo.cleanThumbs()                          // ST1, ST4: expired and over-ceiling thumbnails go
                            val pr = PlaceRepo(db)
                            places = PlacesState(pr.home(), pr.statuses(pattern.filter { it.type == "lecture" || it.type == "tutorial" }.map { it.venue }.filter { it.isNotBlank() }), GitConfigRepo(db).get())
                            blockList = BlockingRepo(db).list()
                            BlockingState.refresh(applicationContext)       // the blocking service sees the change at once
                            heldWaiting = HeldRepo(db).anyUnreviewed()
                            BufferNotifier.update(applicationContext)
                            Loaded(pattern, db.tasks().open(), repo.verifiedKeys())
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
                val restoreNeeded = !blockList.isNullOrEmpty() && !serviceOn

                val l = loaded
                val repo = remember(l) { l?.pattern?.let { BlockRepo(applicationContext, db, it) } }
                val buffer = l?.let { bufferOf(it, clock) } ?: BufferState.NoDeadline
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

                when (screen) {
                    Screen.HOME -> if (restoreNeeded) RestoreScreen(onOpenSettings = { openAccessibilitySettings(applicationContext) }, onSetup = { screen = Screen.SETUP }) else {
                        val nn = l?.pattern?.let { nowAndNext(it, clock) }
                        val hhmm = DateTimeFormatter.ofPattern("HH:mm")
                        val phoneDecision = blockList?.takeIf { it.isNotEmpty() }?.let { BlockingState.decide(it.first()) }
                        HomeScreen(
                            HomeState(
                                date = DateTimeFormatter.ofPattern("EEE d MMM").format(clock),
                                phone = if (blockList.isNullOrEmpty()) null else if (phoneDecision is Decision.Block) "Phone closed" else "Phone open",
                                phoneOpen = phoneDecision !is Decision.Block,
                                time = hhmm.format(clock), buffer = buffer,
                                nowLabel = nn?.now?.let { "Now, until ${hhmm.format(it.end)}" },
                                nowTitle = nn?.now?.slot?.title,
                                nowDetail = nn?.now?.let { listOf(it.detail(), proofHint(proofFor(it.slot))).filter { s -> s.isNotBlank() }.joinToString(". ") },
                                nowOpensBlock = nn?.now?.let { proofFor(it.slot) != Proof.NONE } == true,
                                next = l?.pattern?.let { p -> upcoming(p, clock, 2).map { NextRow(hhmm.format(it.start), it.slot.title, it.detail()) } }.orEmpty(),
                                freeToSpendPence = null,    // record-only for the first 14 days (G6)
                                proofDue = l?.pattern?.let { p -> proofDue(p, clock, l.verified).firstOrNull() }?.let { "${it.slot.title}, ended ${hhmm.format(it.end)}. Take its photo." },
                                heldWaiting = heldWaiting,
                                unavailable = l != null && l.pattern == null,
                            ),
                            onEssential = { key -> launchEssential(key) },
                            onHeld = { screen = Screen.DIGEST },
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
                            override suspend fun currentFix(): Fix? = LocationFix.current(applicationContext)
                            override fun hasLocationPermission() = LocationFix.hasPermission(applicationContext)
                        }, onBack = { reload++; screen = Screen.HOME })
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
                        onChooseList = {
                            scope.launch(Dispatchers.IO) { apps = installedApps(applicationContext); withContext(Dispatchers.Main) { screen = if (blockList == null) Screen.BLOCKLIST else Screen.LIST_GATE } }
                        })
                    Screen.DIGEST -> DigestScreen(digestState, LONDON,
                        onOpenApp = { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                        onSeen = { scope.launch(Dispatchers.IO) { HeldRepo(db).markReviewed(digestState.ready.map { it.id }, System.currentTimeMillis()); reload++ } },
                        onBack = { screen = Screen.HOME })
                    Screen.LIST_GATE ->ListWallGate(onPassed = { screen = Screen.BLOCKLIST }, onCancel = { screen = Screen.SETUP })
                    Screen.BLOCKLIST -> BlockListScreen(apps, blockList,
                        onSave = { chosen -> scope.launch(Dispatchers.IO) { BlockingRepo(db).saveList(chosen); reload++; withContext(Dispatchers.Main) { screen = Screen.SETUP } } },
                        onCancel = { screen = Screen.SETUP })
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
                        onSave = { t ->
                            scope.launch(Dispatchers.IO) {
                                db.tasks().insert(TaskRow(title = t.title, dueMs = t.due.toInstant().toEpochMilli(), dueZone = t.due.zone.id,
                                    heaviness = t.heaviness, hoursEstimate = quickAddHours(t.heaviness), createdMs = System.currentTimeMillis()))
                                reload++
                                screen = Screen.HOME
                            }
                        },
                        onCancel = { screen = Screen.HOME },
                    )
                }
            }
        }
    }
}

/** Study cycles from the week plus stored manual tasks, then the Buffer (B1). Short-sleep is false until sleep logging exists (layer 2). */
private fun bufferOf(l: Loaded, now: ZonedDateTime, extra: DueWork? = null): BufferState {
    val pattern = l.pattern ?: return BufferState.NoDeadline
    val manual = l.tasks.map { DueWork(it.title, it.hoursEstimate, it.heaviness, Instant.ofEpochMilli(it.dueMs).atZone(ZoneId.of(it.dueZone))) }
    return bufferNow(now, studyCycles(pattern, now) + manual + listOfNotNull(extra), pattern, shortSleep = false)
}

private fun bufferWith(l: Loaded, now: ZonedDateTime, t: NewTask): BufferState =
    bufferOf(l, now, DueWork(t.title, quickAddHours(t.heaviness), t.heaviness, t.due))
