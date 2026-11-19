package efa

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * PA: part-of-an-app access. Some apps hold two uses at once: YouTube plays a podcast and also feeds you Shorts. A block that is all or nothing has to close the one to stop the other.
 * This is the general layer for that: an app can be opened at night on terms. What you started playing is yours; the parts built to be scrolled are not.
 *
 * The terms are two things, both worked out from facts the accessibility service can see and nothing else:
 *  - a surface the app is showing (a video, Shorts, a feed, search), recognised from the names of the views on screen, and
 *  - how much scrolling is going on, counted as gestures, not as events.
 * Shorts is closed outright, because it is a scroll made into a product. Every other surface is open: a podcast plays, you can search for the next one, and finding it is paid for out of a
 * credit that listening earns back, so looking for a while is fine and a feed cannot be thumbed through instead of listening.
 *
 * Failure behaviour (X7): a surface that cannot be recognised is [Surface.OTHER] and costs double, so a change in the app's view names never traps the phone and never
 * opens a feed for free either. The recognition is a table, so a change is one edit and one test.
 */
enum class Surface(val label: String) { WATCH("a video"), SHORTS("Shorts"), FEED("the feed"), SEARCH("search"), OTHER("somewhere else") }

/** The scroll budget of a surface: more than [max] gestures in [windowSec] seconds is too much. A closed surface has a budget of none. */
data class ScrollBudget(val windowSec: Int, val max: Int)

/**
 * What is open at night for an app on terms. Two kinds of scrolling are told apart, because they are different things:
 *  - scrolling to find something (search, a feed, a library list) is paid for out of a finding credit that starts full and is earned back by listening, so a slow search is fine and a
 *    scroll-instead-of-listen habit runs dry, and
 *  - scrolling while a video plays (the description, the comments, the suggestions) has a burst limit per minute.
 * A surface in neither list is closed.
 */
data class ScopedAccess(
    val apps: Set<String> = SCOPED_APPS,
    val budgets: Map<Surface, ScrollBudget> = DEFAULT_BUDGETS,
    val findCost: Map<Surface, Int> = DEFAULT_FIND_COST,
    val findMax: Int = 40,
    val earnPerWatchMinute: Double = 1.0,
    val tripMinutes: Int = 3,
)

val SCOPED_APPS: Set<String> = setOf("com.google.android.youtube")

/** PA2: on a playing video a few flicks are fine (12 a minute). Everything else that scrolls is paid for from the finding credit, and Shorts is closed. */
val DEFAULT_BUDGETS: Map<Surface, ScrollBudget> = mapOf(Surface.WATCH to ScrollBudget(60, 12))

/** PA2: what one scroll gesture costs from the finding credit. Search is cheapest, because looking for the thing is the point; a feed costs double, and so does a surface nobody recognised, which could be anything. */
val DEFAULT_FIND_COST: Map<Surface, Int> = mapOf(Surface.SEARCH to 1, Surface.FEED to 2, Surface.OTHER to 2)

/** What the service saw in the window: the names of the views (the part after `:id/`) and the labels of the views that are selected, such as the chosen tab. No other text. */
data class Signals(val ids: Set<String>, val selectedLabels: Set<String> = emptySet())

/**
 * How an app's views are told apart. Each list holds fragments of a view's name. The names are the app's own and can change with an update; that is why the service keeps a
 * short log of what it saw (see the sleep audio card) and why an unrecognised surface is [Surface.OTHER], never an error.
 */
data class SurfaceMap(
    val pkg: String, val shorts: List<String>, val watch: List<String>, val miniplayer: List<String>, val feed: List<String>, val search: List<String>,
    val shortsTabs: List<String> = listOf("shorts"), val feedTabs: List<String> = listOf("home", "subscriptions", "explore"),
)

val YOUTUBE_SURFACES = SurfaceMap(
    pkg = "com.google.android.youtube",
    shorts = listOf("reel_", "shorts_", "shorts_player", "reel_player"),
    watch = listOf("watch_player", "watch_while_time_bar", "watch_metadata", "player_fragment"),
    miniplayer = listOf("floaty", "miniplayer", "minimized"),
    feed = listOf("rich_grid", "rich_item", "feed_content", "browse_", "results"),
    search = listOf("search_edit_text", "search_results", "search_query"),
)

val SURFACE_MAPS: List<SurfaceMap> = listOf(YOUTUBE_SURFACES)

fun surfaceMapFor(pkg: String): SurfaceMap? = SURFACE_MAPS.firstOrNull { it.pkg == pkg }

/**
 * PA1: which surface the signals show. Shorts first (its own views, or the Shorts tab being the selected one), then search, then a video that is playing full size (a miniplayer does
 * not count, because a miniplayer sits on top of a feed), then a feed. Anything else is [Surface.OTHER].
 */
fun classifySurface(map: SurfaceMap, s: Signals): Surface {
    val ids = s.ids.map { it.lowercase() }
    val tabs = s.selectedLabels.map { it.lowercase().trim() }
    fun hit(keys: List<String>) = ids.any { id -> keys.any { id.contains(it) } }
    if (hit(map.shorts) || tabs.any { it in map.shortsTabs }) return Surface.SHORTS
    if (hit(map.search)) return Surface.SEARCH
    if (hit(map.watch) && !hit(map.miniplayer)) return Surface.WATCH
    if (hit(map.feed) || tabs.any { it in map.feedTabs }) return Surface.FEED
    return Surface.OTHER
}

fun classifySurface(pkg: String, s: Signals): Surface = surfaceMapFor(pkg)?.let { classifySurface(it, s) } ?: Surface.OTHER

/**
 * PA3: a scroll is one gesture, however many events it makes. One swipe fires a run of scroll events a few milliseconds apart, so events closer than [gapMs] are one gesture.
 * Returns the time each gesture began.
 */
fun gestures(events: List<Instant>, gapMs: Long = 700): List<Instant> {
    val out = ArrayList<Instant>(); var last: Instant? = null
    for (e in events.sorted()) { if (last == null || Duration.between(last, e).toMillis() > gapMs) out += e; last = e }
    return out
}

/** PA3: whether the scrolling in the last [budget] window is over the budget. */
fun scrollTripped(events: List<Instant>, now: Instant, budget: ScrollBudget): Boolean {
    val from = now.minusSeconds(budget.windowSec.toLong())
    return gestures(events.filter { !it.isBefore(from) && !it.isAfter(now) }).size > budget.max
}

/** The burst limit of a surface under [access], or null when it has none. */
fun budgetOf(access: ScopedAccess, surface: Surface): ScrollBudget? = access.budgets[surface]

/** What a gesture on [surface] costs from the finding credit, or null when the surface is not paid for that way. */
fun costOf(access: ScopedAccess, surface: Surface): Int? = access.findCost[surface]

/** Whether a surface is open at all under [access]. */
fun isOpen(access: ScopedAccess, surface: Surface): Boolean = surface in access.budgets || surface in access.findCost

/**
 * PA2: the finding credit. It starts full, each scroll gesture on a paid surface spends its cost, and every minute a video is the surface in front earns [ScopedAccess.earnPerWatchMinute]
 * back, up to the maximum. [surface] and [at] say what was in front since [at], which is what earns.
 */
data class FindCredit(val credit: Double, val at: Instant? = null, val surface: Surface? = null)

fun freshCredit(access: ScopedAccess = ScopedAccess()) = FindCredit(access.findMax.toDouble())

/** The credit at [now]: what was earned while a video was in front is added. */
fun creditAt(c: FindCredit, now: Instant, access: ScopedAccess): FindCredit {
    val earned = if (c.surface == Surface.WATCH && c.at != null && now.isAfter(c.at)) Duration.between(c.at, now).toMillis() / 60_000.0 * access.earnPerWatchMinute else 0.0
    return FindCredit(minOf(access.findMax.toDouble(), c.credit + earned), now, c.surface)
}

/** What is in front from [now] on. */
fun creditSurface(c: FindCredit, now: Instant, surface: Surface, access: ScopedAccess): FindCredit = creditAt(c, now, access).copy(surface = surface)

/** One scroll gesture on [surface]. Returns the credit after it and whether it was more than the credit allowed. On a trip the credit is left at zero. */
fun spendGesture(c: FindCredit, now: Instant, surface: Surface, access: ScopedAccess): Pair<FindCredit, Boolean> {
    val cost = costOf(access, surface) ?: return c to false
    val cur = creditAt(c, now, access).copy(surface = surface)
    val left = cur.credit - cost
    return if (left < 0) cur.copy(credit = 0.0) to true else cur.copy(credit = left) to false
}

/** PA4: the verdict for a scoped app at night. Allowed, or why not and until when. */
sealed interface ScopedVerdict {
    data object Open : ScopedVerdict
    data class Closed(val surface: Surface, val until: ZonedDateTime) : ScopedVerdict
    data class Tripped(val until: ZonedDateTime) : ScopedVerdict
}

fun scopedVerdict(access: ScopedAccess, surface: Surface?, tripUntil: ZonedDateTime?, now: ZonedDateTime): ScopedVerdict {
    if (tripUntil != null && tripUntil.isAfter(now)) return ScopedVerdict.Tripped(tripUntil)
    val s = surface ?: Surface.OTHER
    return if (!isOpen(access, s)) ScopedVerdict.Closed(s, now.plusMinutes(1)) else ScopedVerdict.Open
}
