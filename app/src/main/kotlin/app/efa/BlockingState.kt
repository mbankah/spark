package app.efa

import android.content.Context
import app.efa.data.BlockRepo
import app.efa.data.BlockingRepo
import app.efa.data.EfaDb
import app.efa.data.LocationFix
import app.efa.data.PlaceRepo
import app.efa.data.MetaRow
import app.efa.data.META_OUT_UNTIL
import app.efa.data.META_COACH_WD
import app.efa.data.META_FIRST_USE
import app.efa.data.MorningRepo
import app.efa.data.RewardsRepo
import app.efa.data.PlanRepo
import app.efa.data.toState
import efa.*
import java.time.ZonedDateTime

/**
 * What the blocking service and the blocked card need, held in memory so a window event is decided in microseconds, with no
 * database wait (LA4). It is reloaded every minute, when the service connects, and after any change (a wall pass, a new task, a
 * verified block). If the database cannot be read the last snapshot stays, and if there never was one nothing is blocked:
 * the failure is open, never a trapped phone (LA1, LA3).
 */
class Snapshot(
    val pattern: List<PatternSlot>, val list: Set<String>, val allow: Allowances,
    val verified: Set<String>, val tasks: List<DueWork>, val morning: MorningState? = null, val lateness: Lateness = emptyMap(),
    val earnings: List<Earning> = emptyList(), val shortSleep: Boolean = false, val essentials: Set<String> = ESSENTIAL_CORE,
    val quiet: Boolean = false,
)

object BlockingState {
    @Volatile var snapshot: Snapshot? = null; private set
    /** GT2: the WhatsApp cap, when each capped app was last opened, and whether a call is on. Held in memory like the snapshot, so a window event is decided in microseconds. */
    @Volatile var cap: AppCap = AppCap()
    @Volatile var capStart: Map<String, ZonedDateTime> = emptyMap()
    @Volatile var inCall: Boolean = false
    /** EM2: when you said you are out until, and whether the phone is at home right now (null when the place is not known). */
    @Volatile var outUntil: ZonedDateTime? = null
    @Volatile var atHome: Boolean? = null
    @Volatile private var lastPlaceCheck = 0L
    /** MX6: the minutes a day the blocked apps get while a period off carries a limit (0 means none), and when each was first opened today. */
    @Volatile var limitMin: Int = 0
    @Volatile var limitStart: Map<String, ZonedDateTime> = emptyMap()

    suspend fun noteLimitOpen(ctx: Context, pkg: String) {
        val s = snapshot ?: return
        if (limitMin <= 0 || pkg !in s.list) return
        val now = now()
        if (limitStatus(limitStart[pkg], now, limitMin).phase != LimitPhase.FREE) return
        limitStart = limitStart + (pkg to now)
        try { EfaDb.get(ctx).meta().put(app.efa.data.MetaRow("limit_start:" + pkg, now.toInstant().toEpochMilli().toString())) } catch (e: Exception) { }
    }

    /** GT2: opening a capped app starts its window, unless one is running or a cooldown is on. */
    suspend fun noteOpen(ctx: Context, pkg: String) {
        if (pkg !in cap.apps) return
        val now = now()
        if (capStatus(capStart[pkg], now, cap).phase != CapPhase.FREE) return
        capStart = capStart + (pkg to now)
        try { EfaDb.get(ctx).meta().put(app.efa.data.MetaRow(app.efa.data.META_CAP_START + pkg, now.toInstant().toEpochMilli().toString())) } catch (e: Exception) { /* X7: the window still runs in memory */ }
    }

    private suspend fun loadCap(db: EfaDb) {
        val w = db.meta().get(app.efa.data.META_CAP_WINDOW)?.toIntOrNull()?.coerceIn(1, 120) ?: 10
        val c = db.meta().get(app.efa.data.META_CAP_COOLDOWN)?.toIntOrNull()?.coerceIn(1, 480) ?: 30
        cap = AppCap(windowMin = w, cooldownMin = c)
        capStart = cap.apps.mapNotNull { p -> db.meta().get(app.efa.data.META_CAP_START + p)?.toLongOrNull()?.takeIf { it > 0 }?.let { p to java.time.Instant.ofEpochMilli(it).atZone(LONDON) } }.toMap()
    }
    /**
     * PA: part-of-an-app access (sleep audio). [scoped] is null while the option is off. The surface each scoped app last showed, the scroll events seen, and when a scroll trip ends are held
     * in memory like the snapshot. [surfaceLog] keeps the last few things the service recognised, with the view names it saw, so a change in an app's own names can be read off the phone.
     */
    @Volatile var scoped: ScopedAccess? = null
    @Volatile var surfaces: Map<String, Surface> = emptyMap()
    @Volatile var scrollTrip: Map<String, ZonedDateTime> = emptyMap()
    private val scrolls = HashMap<String, ArrayDeque<java.time.Instant>>()
    val surfaceLog = java.util.concurrent.ConcurrentLinkedDeque<String>()

    /** Whether the service should look at what [pkg] is showing: the option is on and the app is one it covers. */
    fun watchesSurface(pkg: String): Boolean = scoped?.apps?.contains(pkg) == true

    /** PA2: the finding credit: what is left of the scrolling you get at night. It starts full when the option is switched on, and listening earns it back. */
    @Volatile var findCredit: FindCredit = freshCredit()
    @Volatile private var lastScrollAt: java.time.Instant? = null

    /** PA2: whole credits left, for the card. */
    fun creditLeft(): Int { val a = scoped ?: return 0; return creditAt(findCredit, now().toInstant(), a).credit.toInt() }

    /** PA1: the service read the views of [pkg]. The surface is recognised and remembered; what was in front until now has earned or not, and a change of surface starts the burst count afresh. */
    fun noteSignals(pkg: String, signals: Signals) {
        val s = classifySurface(pkg, signals)
        if (surfaces[pkg] != s) {
            scoped?.let { findCredit = creditSurface(findCredit, now().toInstant(), s, it) }
            surfaces = surfaces + (pkg to s); synchronized(scrolls) { scrolls[pkg]?.clear() }
        }
        val line = now().toLocalTime().withNano(0).toString() + " " + s.name + ": " + (signals.ids.take(10) + signals.selectedLabels.take(3).map { "[" + it + "]" }).joinToString(", ")
        if (surfaceLog.firstOrNull()?.substringAfter(' ') != line.substringAfter(' ')) { surfaceLog.addFirst(line); while (surfaceLog.size > 6) surfaceLog.removeLast() }
    }

    /**
     * PA3: a scroll event. One swipe is many events, so only the first of a run is a gesture. On a playing video a gesture counts toward the burst limit; on any other open surface it is paid for
     * from the finding credit. Returns true when that was too much and the app is now paused.
     */
    fun noteScroll(pkg: String): Boolean {
        val access = scoped ?: return false
        if (pkg !in access.apps) return false
        val now = now(); val t = now.toInstant()
        val surface = surfaces[pkg] ?: Surface.OTHER
        val gesture = lastScrollAt?.let { java.time.Duration.between(it, t).toMillis() > 700 } ?: true
        lastScrollAt = t
        if (!gesture) return false
        var tripped = false
        if (costOf(access, surface) != null) {
            val (c, over) = spendGesture(findCredit, t, surface, access)
            findCredit = c; tripped = over
        } else {
            val budget = budgetOf(access, surface) ?: return false
            tripped = synchronized(scrolls) {
                val q = scrolls.getOrPut(pkg) { ArrayDeque() }
                q.addLast(t); while (q.size > 400 || (q.isNotEmpty() && q.first().isBefore(t.minusSeconds(budget.windowSec.toLong() + 5)))) q.removeFirst()
                scrollTripped(q.toList(), t, budget)
            }
        }
        if (tripped) { scrollTrip = scrollTrip + (pkg to now.plusMinutes(access.tripMinutes.toLong())); synchronized(scrolls) { scrolls[pkg]?.clear() } }
        return tripped
    }

    /** Tests move the clock here; the real service never sets it. */
    @Volatile var clockOverride: (() -> ZonedDateTime)? = null

    /** PL1, PL3: the zone the day is lived in: an Away span can set one, otherwise London. Alarms, meal checks, the night rule and the prompts all read "now" in it. */
    @Volatile var zone: java.time.ZoneId = LONDON

    fun now(): ZonedDateTime = clockOverride?.invoke() ?: ZonedDateTime.now(zone).plus(java.time.Duration.ofMillis(TimeTrust.correction()))      // G10

    suspend fun refresh(ctx: Context) {
        try {
            val db = EfaDb.get(ctx)
            val repo = BlockingRepo(db)
            val now = now()
            val plan = PlanRepo(db)
            // RS1, RS3: missed work comes back and free time turns into work. It must never stop blocking, so a failure here is ignored.
            try {
                val firstUse = db.meta().get(META_FIRST_USE)?.toLongOrNull() ?: now.toInstant().toEpochMilli()
                plan.automate(now, firstUse, BlockRepo(ctx, db, emptyList()).verifiedKeys())
            } catch (e: Exception) { android.util.Log.w("efa-plan", "automation skipped: ${e.message}") }
            val pattern = plan.slots()
            try { RewardsRepo(db).closeWeeks(pattern, db.meta().get(META_FIRST_USE)?.toLongOrNull() ?: now.toInstant().toEpochMilli(), now) } catch (e: Exception) { /* a week closes next minute */ }
            try { loadCap(db) } catch (e: Exception) { /* keep the last cap state */ }
            try { val was = scoped; scoped = if (db.meta().get(app.efa.data.META_SCOPED_ON) == "1") ScopedAccess() else null; if (was == null && scoped != null) findCredit = freshCredit(scoped!!) } catch (e: Exception) { /* PA7: keep the last state */ }
            try { zone = zoneFor(now.toLocalDate(), db.modifiers().all().filter { it.state == "active" }.mapNotNull { decodeInstance(it.instance) }) } catch (e: Exception) { zone = LONDON }
            try { RoutinePrompter.tick(ctx, pattern, now) } catch (e: Exception) { /* X7: a prompt that cannot go out changes nothing else */ }
            try { Backup.nightly(ctx) } catch (e: Exception) { /* CT1: a backup that cannot be written changes nothing else */ }
            try {      // MX6: a period off can carry a daily limit for the blocked apps
                val today = now.toLocalDate()
                limitMin = db.modifiers().all().filter { it.state == "active" }.mapNotNull { decodeInstance(it.instance) }.filter { it.preset == Preset.PERIOD_OFF && today in it.dates() }.maxOfOrNull { it.int("screen_limit_min") } ?: 0
                limitStart = if (limitMin <= 0) emptyMap() else pattern.let { snapshot?.list.orEmpty() }.mapNotNull { p -> db.meta().get("limit_start:" + p)?.toLongOrNull()?.takeIf { it > 0 }?.let { p to java.time.Instant.ofEpochMilli(it).atZone(LONDON) } }.toMap()
            } catch (e: Exception) { limitMin = 0; limitStart = emptyMap() }
            try { WindDown.sync(ctx, homeModeOf(pattern, now)) } catch (e: Exception) { /* SL9: never blocks the blocking */ }
            try {
                val ou = db.meta().get(META_OUT_UNTIL)?.toLongOrNull()?.takeIf { it > now.toInstant().toEpochMilli() }
                outUntil = ou?.let { java.time.Instant.ofEpochMilli(it).atZone(LONDON) }
                atHome = if (ou == null) null else {
                    val fix = LocationFix.current(ctx, 3_000); val home = PlaceRepo(db).home()
                    if (fix == null || home == null) null else distanceM(fix.lat, fix.lon, home.first, home.second) <= 250
                }
            } catch (e: Exception) { outUntil = null; atHome = null }
            try {      // HO2: once you have set the family home, Spark tells which home you are at, at most every 15 minutes, from one coarse reading
                val fam = db.meta().get(app.efa.data.META_FAMILY_HOME)
                if (fam != null && now.toInstant().toEpochMilli() - lastPlaceCheck > 15 * 60_000L) {
                    lastPlaceCheck = now.toInstant().toEpochMilli()
                    val fix = LocationFix.current(ctx, 3_000); val flat = PlaceRepo(db).home()
                    if (fix != null) {
                        val (la, lo) = fam.split("|").map { it.toDouble() }
                        val kind = when { distanceM(fix.lat, fix.lon, la, lo) <= 150 -> PlaceKind.FAMILY_HOME; flat != null && distanceM(fix.lat, fix.lon, flat.first, flat.second) <= 150 -> PlaceKind.FLAT; else -> null }
                        if (kind != null) db.meta().put(MetaRow(app.efa.data.META_PLACE_NOW, kind.name))
                    }
                }
            } catch (e: Exception) { /* X7: the place stays as you last chose it */ }
            try {      // CO2: the wind-down prompt, once a night, in the feel of the goodnight pattern and the spoken line if the voice is on
                if (homeModeOf(pattern, now) == HomeMode.WIND_DOWN && db.meta().get(META_COACH_WD) != now.toLocalDate().toString()) {
                    db.meta().put(MetaRow(META_COACH_WD, now.toLocalDate().toString())); Coach.prompt(ctx, "Wind-down. Last jobs, then the screen goes quiet.", Haptic.GOODNIGHT)
                }
                ReminderAlarm.refresh(ctx, now)
            } catch (e: Exception) { /* X7 */ }
            android.util.Log.i("efa-state", "snapshot loaded: ${pattern.size} blocks")
            snapshot = Snapshot(
                pattern = pattern, list = repo.list().orEmpty(), allow = repo.allowances(now),
                verified = BlockRepo(ctx, db, pattern).verifiedKeys(),
                tasks = db.tasks().open().map { it.toDue() },
                morning = MorningRepo(db).latest()?.toState(),
                lateness = MorningRepo(db).lateness(pattern, now.toLocalDate()),
                earnings = RewardsRepo(db).earnings(now),
                shortSleep = isShortSleepToday(db, now),
                essentials = app.efa.EssentialsRepo(ctx, db).resolve(),                       // MG1, MG2: worked out each time
                quiet = db.meta().get(app.efa.data.META_QUIET_MODE) == "1",                  // QM1
            )
        } catch (e: Exception) { /* keep the last snapshot */ }
    }

    /** QM1: the quiet window the phone is in now (a work block or the night), or null. Always null with quiet mode off. */
    fun quietWindow(): Decision.Block? {
        val s = snapshot ?: return null
        if (!s.quiet) return null
        val now = now()
        return efa.quietWindow(now, s.pattern, s.allow, s.verified, s.morning, s.lateness, earnedAccessActive(now, s.earnings, s.pattern), s.essentials)
    }

    /** QM1: whether quiet mode is on, whatever the time. */
    fun quietOn(): Boolean = snapshot?.quiet == true

    /** [web] is true for the website of an app in a browser: sleep audio covers the app only (PA10), so the site is decided as if the option were off. */
    fun decide(pkg: String, web: Boolean = false): Decision {
        val s = snapshot ?: return Decision.Allow
        val now = now()
        return decide(pkg, now, s.pattern, s.list, s.allow, s.verified, s.morning, s.lateness, earnedAccess = earnedAccessActive(now, s.earnings, s.pattern), essentials = s.essentials, cap = cap, capStart = capStart, inCall = inCall, outUntil = outUntil, atHome = atHome, limit = if (limitMin > 0) ScreenLimit(s.list, limitMin) else null, limitStart = limitStart,
            scoped = if (web) null else scoped, surface = surfaces[pkg], scrollTripUntil = scrollTrip[pkg])
    }

    /**
     * K10: the website in a browser's address bar. Returns the app the site belongs to and the block, or null when the site is not listed or not blocked right now.
     * It uses the app's own decision, so the same work blocks, night rule and wall allowance apply to the website as to the app.
     */
    fun decideHost(host: String?): Pair<String, Decision.Block>? {
        val s = snapshot ?: return null
        val owner = ownerOfHost(host, domainOwners(s.list)) ?: return null
        return (decide(owner, web = true) as? Decision.Block)?.let { owner to it }
    }

    /** N5: whether the day this night belongs to was fully verified, which makes an override 45 minutes and not 30. */
    fun fullyVerifiedTonight(): Boolean {
        val s = snapshot ?: return false
        val now = now()
        val wake = driftedWake(s.pattern, now, s.lateness) ?: return false
        return fullyVerifiedDay(s.pattern, dayOfNight(wake), s.verified, now)
    }

    /** U7: the Buffer now and the Buffer if the current work block is lost, as arithmetic and not a warning. */
    fun bufferCost(d: Decision.Block): Pair<BufferState, BufferState>? {
        val s = snapshot ?: return null
        val now = now()
        val work = studyCycles(s.pattern, now) + s.tasks
        val before = bufferNow(now, work, s.pattern, shortSleep = s.shortSleep)
        val current = nowAndNext(s.pattern, now).now ?: return before to before
        if (d.unprovenMinutes <= 0) return before to before
        val without = s.pattern.filter { it != current.slot }
        return before to bufferNow(now, work, without, shortSleep = s.shortSleep)
    }
}

/** B8, F6: after a night of 6.5 hours of sleep or less, today's remaining work counts heavier. The night that ended this morning is the one that counts. */
suspend fun isShortSleepToday(db: EfaDb, now: ZonedDateTime): Boolean {
    val n = db.nights().latest() ?: return false
    val endedToday = java.time.Instant.ofEpochMilli(n.endMs).atZone(now.zone).toLocalDate() == now.toLocalDate()
    return endedToday && isShortSleep(n.sleepMin / 60.0)
}
