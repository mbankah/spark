package efa

/**
 * The steps of first-run setup (U1, U2, X6), in the order they are asked. Each has a plain reason (A9). A step whose feature is not
 * built yet is listed as "later" and is not asked for, because a permission with nothing behind it would be a request for nothing.
 *
 * The default home app is the LAST step. If it were set first, an unfinished setup would be the phone's home screen, and the launcher
 * must never trap the person (LA1, LA3).
 */
enum class SetupStep(val title: String, val reason: String, val builtYet: Boolean = true, val laterBecause: String = "") {
    ACCESSIBILITY("Accessibility service", "Lets Spark see which app is in front, only to close the apps you chose. It does not read what is on screen. Android makes you switch it on yourself."),
    BLOCK_LIST("Block list", "Choose the apps to close in work blocks and at night. You choose once, and changing it later takes 75 typed characters."),
    NOTIFICATION_ACCESS("Notification access", "Lets Spark hold notifications from blocked apps in a digest instead of showing them during a block. Never calls, alarms, authenticator or banking."),
    POST_NOTIFICATIONS("Buffer notification", "Shows the Buffer, the block now and the next one on the lock screen. Silent, no badge."),
    CAMERA("Camera", "Used only to take proof photos inside Spark. Photos stay on this phone."),
    LOCATION("Location", "Read once when you take an attendance photo, to check you are at the lecture. Never tracked or stored."),
    BATTERY("Battery exemption", "Stops Android putting the blocking service to sleep, so blocking keeps working through the day and night."),
    DEFAULT_HOME("Home app", "Makes Spark your home screen, so it starts at every boot and blocking is the first thing running."),

    // U2 lists these too. They have no feature behind them yet, so they are not asked for.
    OVERLAY("Display over other apps", "", false, "Not needed yet: blocked apps are closed with a full card, not an overlay."),
    USAGE_ACCESS("Usage access", "", false, "Not needed yet: foreground apps are read by the accessibility service."),
    EXACT_ALARMS("Exact alarms", "", false, "Needed with the morning alarm (layer 3)."),
    MICROPHONE("Microphone", "", false, "Needed with voice quick-add (layer 8). Never used otherwise."),
    ACTIVITY_RECOGNITION("Physical activity", "", false, "Needed with the steps fallback for the alarm (layer 3)."),
}

/** The order the steps are asked in: built ones only, default home last. */
val SETUP_ORDER: List<SetupStep> = SetupStep.entries.filter { it.builtYet }

/** The next step still to do, or null when setup is complete. */
fun nextSetupStep(done: Set<SetupStep>): SetupStep? = SETUP_ORDER.firstOrNull { it !in done }

fun setupComplete(done: Set<SetupStep>) = nextSetupStep(done) == null

/** Steps listed for the person but not asked for yet, each with why. */
fun laterSteps(): List<SetupStep> = SetupStep.entries.filter { !it.builtYet }

/**
 * LA3: if the launcher crashes it restarts at once. After [CRASH_LIMIT] crashes in [CRASH_WINDOW_MS] it hands over to Android's own
 * home screen, shows a notice and logs it. Blocking keeps running because the accessibility service is separate.
 */
const val CRASH_LIMIT = 3
const val CRASH_WINDOW_MS = 10 * 60 * 1000L

fun recentCrashes(times: List<Long>, nowMs: Long): List<Long> = times.filter { nowMs - it in 0..CRASH_WINDOW_MS }
fun shouldHandOver(times: List<Long>, nowMs: Long): Boolean = recentCrashes(times, nowMs).size >= CRASH_LIMIT

/**
 * LA5: typing a few letters finds an app. Matches are ranked: the name starts with the letters, then a word in the name starts with
 * them, then the letters appear in order. There is no recent list. Case does not matter.
 */
fun matchApps(query: String, labels: List<String>): List<Int> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    fun rank(label: String): Int? {
        val l = label.lowercase()
        if (l.startsWith(q)) return 0
        if (l.split(' ', '-', '.', '_').any { it.startsWith(q) }) return 1
        var i = 0
        for (ch in l) if (i < q.length && ch == q[i]) i++
        return if (i == q.length) 2 else null
    }
    return labels.withIndex().mapNotNull { (i, l) -> rank(l)?.let { it to i } }.sortedWith(compareBy({ it.first }, { labels[it.second].lowercase() })).map { it.second }
}
