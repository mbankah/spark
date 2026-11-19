package efa

/**
 * WK: the walkthrough. A short tour on first open, and always available again from Setup. It says what Spark does in plain words, with the numbers taken from the same constants the
 * rules use, so it cannot drift from the app. It has no motivation in it, no goals-speak and no promise the app cannot keep (P6, X7): where Android limits what Spark can do, it says so.
 */
enum class WalkArt { SPARK, BUFFER, PHONE, PROOF, LIFE, WALL, SLEEP, YOURS, START }

/** [label] is the small capitals line, [title] the one sentence, [lines] the few short lines under it. */
data class WalkPage(val id: String, val art: WalkArt, val label: String, val title: String, val lines: List<String>)

/** The meta value set when the walkthrough has been finished or skipped. */
const val WALKTHROUGH_SEEN_VALUE = "1"

/** WK1: whether the walkthrough opens by itself: only until it has been seen once, and never when a build turns it off (a test). */
fun walkthroughDue(seen: String?, enforced: Boolean = true): Boolean = enforced && seen != WALKTHROUGH_SEEN_VALUE

fun walkthroughPages(
    cap: AppCap = AppCap(), limits: ChangeLimits = ChangeLimits(), wallBase: Int = WALL_BASE, glassWall: Int = BREAK_GLASS_WALL,
): List<WalkPage> = listOf(
    WalkPage("spark", WalkArt.SPARK, "WELCOME", "Friction for your intentions, freedom for reality.", listOf(
        "Telling Spark what happened is free and one tap.",
        "Wanting less than you planned costs something.",
        "Nothing is ever a dead end.",
    )),
    WalkPage("buffer", WalkArt.BUFFER, "THE ONE NUMBER", "The Buffer", listOf(
        "The hours you have before a deadline, minus the hours of work still to do.",
        "Tap any number to see how it was worked out.",
        "It is arithmetic. There is no fake pressure.",
    )),
    WalkPage("phone", WalkArt.PHONE, "THE PHONE", "Spark is your home screen", listOf(
        "In work blocks and at night it closes the apps you chose. Calls, maps, banking and your authenticator are never blocked.",
        "WhatsApp opens for ${cap.windowMin} minutes at a time, then a ${cap.cooldownMin} minute cooldown. A call is never cut off.",
    )),
    WalkPage("proof", WalkArt.PROOF, "PROOF", "Nothing counts until it has proof", listOf(
        "A photo of your notes, a commit for code, a ready photo in the morning.",
        "Photos stay on your phone. NFC stickers are an option for later.",
    )),
    WalkPage("life", WalkArt.LIFE, "WHEN LIFE HAPPENS", "The plan bends. You do not have to explain.", listOf(
        "Running over, Started late, Something came up: say what happened and Spark finds a place for it.",
        "If you fall behind it offers one replan. Work that cannot fit comes back as work, so moving it is free.",
        "The day steps down by itself and steps back up.",
    )),
    WalkPage("wall", WalkArt.WALL, "WANTING LESS", "Wanting less costs a string you type by hand", listOf(
        "Dropping work, loosening blocking or ending a cooldown needs $wallBase characters or more.",
        "Each week ${limits.freeImportsPerWeek} imports and ${limits.freeMovesPerWeek} moves are free, then the string grows.",
        "Break glass lifts one lock, once in 30 days, for $glassWall characters.",
    )),
    WalkPage("sleep", WalkArt.SLEEP, "SLEEP", "Sleep is protected", listOf(
        "Lights-out and wake move together, so a late night is planned and not fought. Nothing relaxes the cutoff for sleep.",
        "Sleep audio, off by default, lets a YouTube podcast play at night while Shorts stay closed.",
    )),
    WalkPage("yours", WalkArt.YOURS, "YOURS", "Your data stays on your phone", listOf(
        "Photos never leave it. A backup file is written to Downloads/Spark each day, so an update or a reinstall keeps your plan.",
        "Android can switch Spark off or stop it. Spark cannot prevent that. It notices and tells you.",
    )),
    WalkPage("start", WalkArt.START, "START", "Two things first, then three pages", listOf(
        "Switch on blocking and choose your block list. Then import your timetable: Setup shows the file format an AI can write for you.",
        "Swipe left for the Schedule, and again for Work it out. The gear opens Setup, and this walkthrough is there too.",
    )),
)

val WALKTHROUGH: List<WalkPage> = walkthroughPages()
