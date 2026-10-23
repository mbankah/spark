package efa

import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.*

/** What proves a block (G1, Y1, Y2, L1). */
enum class Proof { NONE, WORK_PHOTO, WINDOW_PHOTO, ATTENDANCE, GIT_COMMIT }

fun proofFor(s: PatternSlot): Proof = when (s.type) {
    "lecture", "tutorial" -> Proof.ATTENDANCE                                   // Y6: tutorials count as lectures
    "code" -> Proof.GIT_COMMIT                                                  // G1
    "study", "study-standard", "study-light" -> Proof.WORK_PHOTO                // G1: photo of notes or worked solutions
    "training", "match", "gym", "church", "mytutor", "chore" -> Proof.WINDOW_PHOTO   // Y1
    else -> Proof.NONE                                                          // meals are not photographed (Y1); transitions, routines, free time carry no proof
}

/** W1: the "done" control must be held for one second without letting go. Time comes from a monotonic clock supplied by the caller. */
class HoldTracker(private val requiredMs: Long = 1000) {
    private var downAt: Long? = null
    var completed: Boolean = false; private set
    fun down(nowMs: Long) { if (!completed) downAt = nowMs }
    /** Returns true when this release completed the hold. Letting go early restarts it. */
    fun up(nowMs: Long): Boolean {
        val d = downAt ?: return false
        downAt = null
        if (nowMs - d >= requiredMs) completed = true
        return completed
    }
    fun cancel() { downAt = null }
    /** 0..1 for a progress ring while the finger is down. */
    fun progress(nowMs: Long): Double = if (completed) 1.0 else downAt?.let { ((nowMs - it).toDouble() / requiredMs).coerceIn(0.0, 1.0) } ?: 0.0
}

enum class LocationResult { NEAR_VENUE, AWAY_FROM_HOME, REJECTED_NEAR_HOME, VENUE_UNKNOWN, NO_FIX, HOME_UNSET }

const val NEAR_VENUE_M = 150.0      // L2: within about 150 m of the named building
const val AWAY_FROM_HOME_M = 250.0  // L2: at least about 250 m (3-minute walk) from home; inside it the proof is rejected (L3, Q-G9)

fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
    val a = sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

/** One location read, taken when the proof photo is taken and never kept (A8). */
data class Fix(val lat: Double, val lon: Double, val accuracyM: Double)

/** A fix less precise than this is treated as no fix, so an imprecise indoor reading cannot pass or fail a lecture (E11). */
const val MAX_FIX_ACCURACY_M = 250.0

/** Strongest first (L2): within 150 m of the named building passes (O4: a venue close to home passes on this check alone). */
fun classifyLocation(distVenueM: Double?, distHomeM: Double?): LocationResult = when {
    distVenueM != null && distVenueM <= NEAR_VENUE_M -> LocationResult.NEAR_VENUE
    distHomeM == null -> LocationResult.HOME_UNSET
    distVenueM == null && distHomeM >= AWAY_FROM_HOME_M -> LocationResult.VENUE_UNKNOWN   // L5: venue not in the directory, weaker check only
    distHomeM >= AWAY_FROM_HOME_M -> LocationResult.AWAY_FROM_HOME                          // L2: weaker proof
    else -> LocationResult.REJECTED_NEAR_HOME                                               // L3
}

/** From a fix, the venue point and the home point (each may be missing). Distances are used and dropped, never stored (ST7). */
fun locate(fix: Fix?, venue: Pair<Double, Double>?, home: Pair<Double, Double>?): LocationResult {
    if (fix == null || fix.accuracyM > MAX_FIX_ACCURACY_M) return LocationResult.NO_FIX
    val dv = venue?.let { haversineM(fix.lat, fix.lon, it.first, it.second) }
    val dh = home?.let { haversineM(fix.lat, fix.lon, it.first, it.second) }
    return classifyLocation(dv, dh)
}

/** L8: the home point is saved at about 100 m resolution, never a house. 3 decimal places of a degree is 111 m of latitude. */
fun coarseHome(lat: Double, lon: Double): Pair<Double, Double> =
    Pair(Math.round(lat * 1000) / 1000.0, Math.round(lon * 1000) / 1000.0)

/** O4: which timetable venues are within the home radius, so those rely on the named-building check alone. */
fun venuesNearHome(home: Pair<Double, Double>, venues: List<Building>): List<Building> =
    venues.filter { haversineM(home.first, home.second, it.lat, it.lon) < AWAY_FROM_HOME_M }

/** ST2: a distance band, never coordinates. */
fun distanceBand(m: Double?): String = when {
    m == null -> "unknown"; m < 50 -> "<50m"; m < 150 -> "<150m"; m < 250 -> "<250m"; m < 1000 -> "<1km"; else -> ">=1km"
}

/** ST2: 64-bit difference hash from a 9 x 8 grey image (row-major, 72 values). Survives re-compression, not a different scene. */
fun differenceHash(gray9x8: IntArray): Long {
    require(gray9x8.size == 72)
    var h = 0L
    for (y in 0 until 8) for (x in 0 until 8) {
        h = (h shl 1) or (if (gray9x8[y * 9 + x] > gray9x8[y * 9 + x + 1]) 1L else 0L)
    }
    return h
}

enum class Reason {
    OK, NOT_REQUIRED, TIMER_RUNNING, NEEDS_HOLD, TOO_EARLY, OUTSIDE_WINDOW, DUPLICATE, REJECTED_NEAR_HOME, NO_LOCATION,
    COMMIT_CHECK_UNAVAILABLE, BACKFILL_CAP, HOME_NOT_SET, NO_COMMIT, COMMIT_USED, GITHUB_UNREACHABLE, REPO_NOT_FOUND,
}

/** creditedMinutes is how much of the block counts as confirmed (W2). recovered marks backfilled time visibly (B7). */
data class Verdict(val verified: Boolean, val reason: Reason, val creditedMinutes: Int = 0, val recovered: Boolean = false, val weaker: Boolean = false)

val ON_TIME_GRACE: Duration = Duration.ofMinutes(15)   // Q-G15: a work proof within 15 min of the end is on time
const val BACKFILL_CAP_MIN = 60                         // B7 / O1: up to 1 hour recovered per night

/**
 * Decide whether a proof photo verifies a block (W1, W2, Y1 to Y3, L1 to L3, ST2, B7).
 * [backfillDeadline] is the next target wake time; after it nothing can be recovered.
 * [recoveredSoFarMin] is the backfill already used since the last morning.
 */
fun verifyProof(
    proof: Proof, start: ZonedDateTime, end: ZonedDateTime, now: ZonedDateTime,
    holdDone: Boolean, duplicate: Boolean, location: LocationResult?,
    backfillDeadline: ZonedDateTime, recoveredSoFarMin: Int,
    commit: CommitOutcome = CommitOutcome.NOT_CONFIGURED,
): Verdict {
    val blockMin = Duration.between(start, end).toMinutes().toInt()
    fun fail(r: Reason) = Verdict(false, r)
    when (proof) {
        Proof.NONE -> return fail(Reason.NOT_REQUIRED)
        else -> {}
    }
    var recovered = false
    var credit = blockMin
    when (proof) {
        Proof.WORK_PHOTO, Proof.GIT_COMMIT -> {      // a code block ends the same way, with a commit where a photo would go (G1)
            if (now.isBefore(end)) return fail(Reason.TIMER_RUNNING)
            if (!holdDone) return fail(Reason.NEEDS_HOLD)
            if (now.isAfter(backfillDeadline)) return fail(Reason.OUTSIDE_WINDOW)
            if (now.isAfter(end.plus(ON_TIME_GRACE))) {
                val left = BACKFILL_CAP_MIN - recoveredSoFarMin
                if (left <= 0) return fail(Reason.BACKFILL_CAP)
                recovered = true; credit = minOf(blockMin, left)
            }
        }
        Proof.WINDOW_PHOTO, Proof.ATTENDANCE -> {
            if (now.isBefore(start)) return fail(Reason.TOO_EARLY)
            if (now.isAfter(end)) return fail(Reason.OUTSIDE_WINDOW)   // Y3: without its photo in the window the time is lost
        }
        else -> {}
    }
    if (proof == Proof.GIT_COMMIT) {
        when (commit) {
            CommitOutcome.FOUND -> {}
            CommitOutcome.NONE_FOUND -> return fail(Reason.NO_COMMIT)
            CommitOutcome.ALREADY_USED -> return fail(Reason.COMMIT_USED)
            CommitOutcome.UNREACHABLE -> return fail(Reason.GITHUB_UNREACHABLE)
            CommitOutcome.REPO_NOT_FOUND -> return fail(Reason.REPO_NOT_FOUND)
            CommitOutcome.NOT_CONFIGURED -> return fail(Reason.COMMIT_CHECK_UNAVAILABLE)
        }
    } else if (duplicate) return fail(Reason.DUPLICATE)
    var weaker = false
    if (proof == Proof.ATTENDANCE) {
        when (location) {
            null, LocationResult.NO_FIX -> return fail(Reason.NO_LOCATION)
            LocationResult.REJECTED_NEAR_HOME -> return fail(Reason.REJECTED_NEAR_HOME)
            LocationResult.HOME_UNSET -> return fail(Reason.HOME_NOT_SET)
            LocationResult.AWAY_FROM_HOME, LocationResult.VENUE_UNKNOWN -> weaker = true
            LocationResult.NEAR_VENUE -> {}
        }
    }
    return Verdict(true, Reason.OK, credit, recovered, weaker)
}

/** Plain words for each outcome (W6: exact, no nagging). */
fun Reason.text(): String = when (this) {
    Reason.OK -> "Verified."
    Reason.NOT_REQUIRED -> "This block needs no photo."
    Reason.TIMER_RUNNING -> "The timer is still running."
    Reason.NEEDS_HOLD -> "Hold done for one second first."
    Reason.TOO_EARLY -> "This block has not started."
    Reason.OUTSIDE_WINDOW -> "The photo is outside the block's time window, so this time counts as lost."
    Reason.DUPLICATE -> "This photo matches an earlier proof. Take a new photo."
    Reason.REJECTED_NEAR_HOME -> "You are too close to home. Retake the photo once you are out."
    Reason.NO_LOCATION -> "No precise location. Move outdoors or near a window and take the photo again."
    Reason.COMMIT_CHECK_UNAVAILABLE -> "GitHub is not set up, so this code block cannot be verified. Add your username and repos in Setup."
    Reason.NO_COMMIT -> "No commit of yours was found in this block's time. Push your commit, then check again."
    Reason.COMMIT_USED -> "Your commits in this block's time already proved an earlier block. Make a new commit and push it."
    Reason.REPO_NOT_FOUND -> "A repo in Setup was not found or is private. Only public repos can be checked, because no GitHub token is stored."
    Reason.GITHUB_UNREACHABLE -> "GitHub could not be reached, or it is limiting requests. Check again when you are online. You can still prove this block until your next wake."
    Reason.BACKFILL_CAP -> "The 1 hour recovery limit for this night is used."
    Reason.HOME_NOT_SET -> "Set your home point in Setup first. It is needed to tell a lecture from your room."
}

/** Stable key for one block on one planned day, used to store its state. */
fun blockKey(s: DatedSlot) = "${s.plannedDay}|${s.slot.startMin}|${s.slot.title}"

/** The next planned wake after [after]; backfill and proof windows end there (B7, Z3). */
fun nextWake(pattern: List<PatternSlot>, after: ZonedDateTime): ZonedDateTime? =
    (0L..2L).flatMap { slotsFor(pattern, after.toLocalDate().plusDays(it), after.zone) }
        .filter { it.slot.title == "Wake, shower" && it.start.isAfter(after) }.minOfOrNull { it.start }

/**
 * Work blocks whose timer has ended and still have no proof, newest first, while a proof can still count:
 * until the next planned wake (B7). Blocks that need no photo, or a different proof, are not listed.
 */
fun proofDue(pattern: List<PatternSlot>, now: ZonedDateTime, verifiedKeys: Set<String>): List<DatedSlot> =
    (-1L..0L).flatMap { slotsFor(pattern, now.toLocalDate().plusDays(it), now.zone) }
        .filter { proofFor(it.slot) == Proof.WORK_PHOTO && !it.end.isAfter(now) && blockKey(it) !in verifiedKeys }
        .filter { s -> nextWake(pattern, s.end)?.let { !now.isAfter(it) } ?: false }
        .sortedByDescending { it.end }


/** What the commit check found (G1). */
enum class CommitOutcome { FOUND, NONE_FOUND, ALREADY_USED, UNREACHABLE, REPO_NOT_FOUND, NOT_CONFIGURED }

data class Commit(val sha: String, val at: java.time.Instant)
data class CommitMatch(val outcome: CommitOutcome, val sha: String? = null)

/**
 * A commit proves a block if it was made from the block's start until the on-time grace after its end, and it has not already
 * proved another block (the seed says one commit per block). Commits are the user's own: the caller asks GitHub for the
 * configured author only.
 */
fun matchCommit(commits: List<Commit>, start: ZonedDateTime, end: ZonedDateTime, usedShas: Set<String>): CommitMatch {
    val lo = start.toInstant(); val hi = end.plus(ON_TIME_GRACE).toInstant()
    val inWindow = commits.filter { !it.at.isBefore(lo) && !it.at.isAfter(hi) }.sortedBy { it.at }
    if (inWindow.isEmpty()) return CommitMatch(CommitOutcome.NONE_FOUND)
    val fresh = inWindow.firstOrNull { it.sha !in usedShas } ?: return CommitMatch(CommitOutcome.ALREADY_USED)
    return CommitMatch(CommitOutcome.FOUND, fresh.sha)
}

sealed interface GitConfig {
    data class Ok(val user: String, val repos: List<String>) : GitConfig
    data class Invalid(val message: String) : GitConfig
}

private val GH_USER = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
private val GH_REPO = Regex("^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$")
const val MAX_GIT_REPOS = 10

/** Checks the username and repo list typed in Setup. Accepts owner/name or a github.com link; repos are public (no token is stored). */
fun parseGitConfig(user: String, reposText: String): GitConfig {
    val u = user.trim().removePrefix("@")
    if (!GH_USER.matches(u)) return GitConfig.Invalid("Enter your GitHub username, letters, digits and hyphens only.")
    val repos = reposText.split(',', ' ', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
        .map { it.removePrefix("https://github.com/").removePrefix("http://github.com/").removeSuffix(".git").trimEnd('/') }
        .distinctBy { it.lowercase() }
    if (repos.isEmpty()) return GitConfig.Invalid("Add at least one repo as owner/name.")
    if (repos.size > MAX_GIT_REPOS) return GitConfig.Invalid("At most $MAX_GIT_REPOS repos.")
    repos.firstOrNull { !GH_REPO.matches(it) }?.let { return GitConfig.Invalid("\"$it\" is not owner/name.") }
    return GitConfig.Ok(u, repos)
}
