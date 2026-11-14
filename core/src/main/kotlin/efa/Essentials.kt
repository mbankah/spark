package efa

/**
 * The essentials (docs/WEEK_TYPES_SPEC.md, MG1 to MG3a, X1, LA1). Calls, messages, maps, banking, authenticators, the camera, the clock, Android Settings, the launcher and
 * Spark are never blocked and never counted by anything: not a block, the night rule, a quiet window, a modifier or "every non-essential app". The fixed core cannot be
 * removed. The person can add to it, which loosens blocking, so it goes through the typed wall, and passing the wall opens a 5 minute window that takes at most 3 apps.
 */
val ESSENTIAL_CORE: Set<String> = setOf(
    "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer", "com.android.phone", "com.android.server.telecom", "com.android.emergency",
    "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging",
    "com.google.android.apps.maps",
    "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera",
    "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
    "com.android.settings", "com.android.systemui",
    "com.google.android.apps.authenticator2", "com.azure.authenticator", "com.authy.authy",
    "com.monzo.android", "com.starlingbank.android", "com.revolut.revolut",
    "app.efa",
)

/** The essentials in force: the fixed core, the phone's own default dialer, messages and home apps (worked out when it is enforced, MG2), and the ones the person added. */
fun resolveEssentials(defaults: Collection<String?> = emptyList(), added: Set<String> = emptySet()): Set<String> = ESSENTIAL_CORE + defaults.filterNotNull() + added

/** MG3: the essentials a block list contains, so the list can be refused with the apps named. */
fun essentialsIn(list: Collection<String>, essentials: Set<String> = ESSENTIAL_CORE): Set<String> = list.filter { it in essentials }.toSet()

/** MG2: "every non-essential app" is this, worked out at the moment it is used and never saved as a list. */
fun nonEssential(installed: Collection<String>, essentials: Set<String> = ESSENTIAL_CORE): List<String> = installed.filter { it !in essentials }

const val ESSENTIALS_WINDOW_MS = 5 * 60_000L
const val ESSENTIALS_WINDOW_MAX_APPS = 3

/**
 * MG3a: the add-essentials window. It opens when the wall is passed, lasts 5 minutes counted from then, takes at most 3 apps, and then closes. It cannot be extended:
 * [reopen] only exists as a new wall. [added] is how many have been added in this window.
 */
data class EssentialsWindow(val openedAtMs: Long? = null, val added: Int = 0) {
    fun open(nowMs: Long) = openedAtMs != null && nowMs >= openedAtMs && nowMs < openedAtMs + ESSENTIALS_WINDOW_MS && added < ESSENTIALS_WINDOW_MAX_APPS
    fun remainingMs(nowMs: Long) = if (open(nowMs)) openedAtMs!! + ESSENTIALS_WINDOW_MS - nowMs else 0L
    fun canAdd(nowMs: Long) = open(nowMs)
    /** The window after one more app is added. Only valid while it is open. */
    fun afterAdd(nowMs: Long): EssentialsWindow = if (canAdd(nowMs)) copy(added = added + 1) else this
    fun encode() = "${openedAtMs ?: ""}|$added"

    companion object {
        /** Passing the wall opens a fresh window. */
        fun passWall(nowMs: Long) = EssentialsWindow(nowMs, 0)
        fun decode(s: String?): EssentialsWindow {
            val p = s?.split("|") ?: return EssentialsWindow()
            return EssentialsWindow(p.getOrNull(0)?.toLongOrNull(), p.getOrNull(1)?.toIntOrNull() ?: 0)
        }
    }
}

/** Adding to the essentials goes through the wall, as every other loosening of blocking (WT4). */
val ESSENTIALS_ADD_WALL = WALL_BASE
