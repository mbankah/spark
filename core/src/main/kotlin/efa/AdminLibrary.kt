package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields

/**
 * The Admin library (docs/WEEK_TYPES_SPEC.md section 4A): a catalogue of upkeep jobs (the bed, the kitchen, the bathroom, laundry, bins, the shop, money, post, health
 * reminders, digital and university admin, guests), each placed by its own frequency and window into free and empty time, spread over the week under a daily cap.
 * Admin never changes the Buffer, the money or earned phone access, and never goes on top of a protected block, in the night or past its day's cap (MG19 to MG24).
 *
 * Built: the catalogue, load sets, switching a job on or off, the daily cap, Lighter (minimum lengths) and an optional "may trim work" switch. Not built: editing a
 * job's frequency, window or checklist, "Add a job", and triggered jobs being set off by an event, so Triggered jobs are listed but never placed by themselves.
 */
enum class Area(val label: String) {
    BED("Bed and bedroom"), KITCHEN("Kitchen"), BATHROOM("Bathroom"), FLOORS("Floors and surfaces"), LAUNDRY("Laundry and clothes"), BINS("Bins and recycling"),
    SHOPPING("Shopping and food"), MONEY("Money and bills"), POST("Post and parcels"), HEALTH("Health and body"), DIGITAL("Digital"), UNI("University admin"),
    FAITH("Faith and life"), HOUSEHOLD("Household"), TRANSPORT("Transport"), GUESTS("Guests and events"),
}

enum class JobSet { STANDARD, OPTIONAL, TRIGGERED }

sealed interface Freq {
    data object Daily : Freq
    data object Weekdays : Freq
    data class Weekly(val day: DayOfWeek) : Freq
    data class TimesPerWeek(val n: Int) : Freq
    data class EveryNDays(val n: Int) : Freq
    data class Fortnightly(val day: DayOfWeek) : Freq
    data class Monthly(val dayOfMonth: Int) : Freq
    data object AsNeeded : Freq
}

/** [anchor] "wake" means the window opens at the day's wake block. [match] are words of an existing block that already does this job, so it is not added twice. */
data class AdminJob(
    val id: String, val area: Area, val title: String, val set: JobSet, val freq: Freq, val from: Int, val to: Int, val minutes: Int, val minMinutes: Int,
    val type: String = "chore", val anchor: String? = null, val match: List<String> = emptyList(), val priority: Int = 2,
)

private const val EVENING_FROM = 17 * 60 + 30
private const val EVENING_TO = 22 * 60 + 30
private fun j(id: String, area: Area, title: String, set: JobSet, freq: Freq, minutes: Int, minMinutes: Int, from: Int = EVENING_FROM, to: Int = EVENING_TO, type: String = "chore", anchor: String? = null, match: List<String> = emptyList(), priority: Int = 2) =
    AdminJob(id, area, title, set, freq, from, to, minutes, minMinutes, type, anchor, match, priority)

object AdminLibrary {
    private val S = JobSet.STANDARD; private val O = JobSet.OPTIONAL; private val T = JobSet.TRIGGERED
    private const val MORNING_TO = 11 * 60

    val jobs: List<AdminJob> = listOf(
        // bed and bedroom
        j("make_bed", Area.BED, "Make the bed", S, Freq.Daily, 3, 2, 0, MORNING_TO, anchor = "wake", match = listOf("make the bed"), priority = 3),
        j("air_room", Area.BED, "Open the window and air the room", O, Freq.Daily, 2, 2, 0, MORNING_TO, anchor = "wake"),
        j("tidy_room", Area.BED, "Tidy the room", S, Freq.Daily, 10, 5, match = listOf("10-minute tidy", "room reset")),
        j("room_reset", Area.BED, "Room reset (surfaces, bed, dishes)", S, Freq.Weekly(DayOfWeek.SUNDAY), 10, 10, match = listOf("room reset")),
        j("sheets", Area.BED, "Change the bed sheets", S, Freq.Fortnightly(DayOfWeek.SATURDAY), 20, 10),
        j("pillow", Area.BED, "Change the pillow cases", O, Freq.Weekly(DayOfWeek.SATURDAY), 5, 3),
        j("desk", Area.BED, "Wipe the desk and clear the study space", O, Freq.Daily, 5, 3, 0, MORNING_TO, anchor = "wake"),
        j("study_setup", Area.BED, "Set up the study space for tomorrow", S, Freq.Daily, 5, 3, 20 * 60, EVENING_TO),
        // kitchen
        j("dishes", Area.KITCHEN, "Wash the dishes", S, Freq.Daily, 15, 10, match = listOf("wash dishes", "dishes")),
        j("counters", Area.KITCHEN, "Wipe the counters and hob", S, Freq.Daily, 5, 3),
        j("microwave", Area.KITCHEN, "Clean the microwave", O, Freq.Fortnightly(DayOfWeek.SUNDAY), 5, 3),
        j("fridge", Area.KITCHEN, "Clean the fridge and bin the old food", S, Freq.Weekly(DayOfWeek.SUNDAY), 10, 5),
        j("oven", Area.KITCHEN, "Clean the oven", O, Freq.Monthly(1), 20, 10),
        j("batch_cook", Area.KITCHEN, "Meal prep and batch cook", S, Freq.Weekly(DayOfWeek.SUNDAY), 45, 30, match = listOf("batch cook")),
        j("pack_lunch", Area.KITCHEN, "Pack tomorrow's lunch", S, Freq.Daily, 5, 3, 20 * 60, EVENING_TO, match = listOf("pack lunch", "pack tomorrow")),
        // bathroom
        j("bath_wipe", Area.BATHROOM, "Wipe the bathroom sink and surfaces", O, Freq.TimesPerWeek(2), 5, 3),
        j("bathroom", Area.BATHROOM, "Clean the bathroom", S, Freq.Weekly(DayOfWeek.SATURDAY), 25, 15, match = listOf("clean the bathroom")),
        j("shower_drain", Area.BATHROOM, "Clean the shower drain and tiles", O, Freq.Monthly(15), 15, 10),
        // floors and surfaces
        j("hoover", Area.FLOORS, "Hoover the floors", S, Freq.Weekly(DayOfWeek.SATURDAY), 20, 10, match = listOf("hoover")),
        j("mop", Area.FLOORS, "Mop the floors", O, Freq.Fortnightly(DayOfWeek.SUNDAY), 20, 10),
        j("dust", Area.FLOORS, "Dust", O, Freq.Weekly(DayOfWeek.WEDNESDAY), 10, 5),
        j("windows", Area.FLOORS, "Clean the windows and mirrors", O, Freq.Monthly(20), 15, 10),
        // laundry and clothes
        j("wash_on", Area.LAUNDRY, "Put a wash on", S, Freq.TimesPerWeek(2), 5, 3, match = listOf("wash on")),
        j("hang_washing", Area.LAUNDRY, "Take the washing out and hang it", S, Freq.TimesPerWeek(2), 10, 5, match = listOf("take the washing out")),
        j("put_away", Area.LAUNDRY, "Put the clean clothes away", S, Freq.Weekly(DayOfWeek.SATURDAY), 15, 10, match = listOf("clothes away")),
        j("ironing", Area.LAUNDRY, "Ironing", S, Freq.Weekly(DayOfWeek.SATURDAY), 30, 15, match = listOf("ironing")),
        j("lay_out", Area.LAUNDRY, "Lay out clothes and the bag for tomorrow", S, Freq.Daily, 5, 3, 20 * 60, EVENING_TO, match = listOf("breakfast, dress, bag")),
        j("kit", Area.LAUNDRY, "Gym and football kit washed and packed", S, Freq.TimesPerWeek(2), 5, 3),
        // bins
        j("bins_out", Area.BINS, "Take the bins out", S, Freq.Weekly(DayOfWeek.THURSDAY), 5, 3, match = listOf("bins out")),
        j("empty_bins", Area.BINS, "Empty the room and kitchen bins", S, Freq.TimesPerWeek(2), 5, 3),
        j("recycling", Area.BINS, "Recycling and glass", O, Freq.Weekly(DayOfWeek.WEDNESDAY), 5, 3),
        // shopping and food
        j("shop", Area.SHOPPING, "The weekly shop", S, Freq.Weekly(DayOfWeek.MONDAY), 60, 45, type = "errand", match = listOf("aldi", "weekly shop")),
        j("topup", Area.SHOPPING, "Top-up shop for a missing item", T, Freq.AsNeeded, 20, 10, type = "errand"),
        j("list_check", Area.SHOPPING, "Check the shopping list and prices", S, Freq.Weekly(DayOfWeek.MONDAY), 10, 5, 15 * 60, 18 * 60, type = "errand"),
        j("shop_away", Area.SHOPPING, "Put the shopping away", S, Freq.Weekly(DayOfWeek.MONDAY), 10, 5, 19 * 60, EVENING_TO),
        // money and bills
        j("money_check", Area.MONEY, "Weekly money check", S, Freq.Weekly(DayOfWeek.SUNDAY), 10, 5, type = "errand"),
        j("bills", Area.MONEY, "Pay bills and rent", O, Freq.Monthly(1), 15, 10, type = "errand"),
        j("bank", Area.MONEY, "Check the bank and statements", O, Freq.Weekly(DayOfWeek.FRIDAY), 10, 5, type = "errand"),
        j("receipts", Area.MONEY, "Put receipts into the app", O, Freq.Weekly(DayOfWeek.SUNDAY), 5, 3, type = "errand"),
        // post
        j("post", Area.POST, "Collect the post and parcels", O, Freq.TimesPerWeek(2), 5, 3, type = "errand"),
        j("return_item", Area.POST, "Return an item", T, Freq.AsNeeded, 20, 10, type = "errand"),
        // health and body (reminders with your own text and dates, never advice)
        j("refill", Area.HEALTH, "Prescription refill (your reminder)", O, Freq.Monthly(1), 15, 10, type = "errand"),
        j("appointment", Area.HEALTH, "Appointment (your reminder)", T, Freq.AsNeeded, 30, 15, type = "errand"),
        j("haircut", Area.HEALTH, "Haircut and nails", O, Freq.EveryNDays(28), 30, 20, type = "errand"),
        // digital
        j("charge", Area.DIGITAL, "Charge the phone and devices", S, Freq.Daily, 5, 3, 21 * 60 + 30, 23 * 60, type = "errand"),
        j("inbox", Area.DIGITAL, "Clear the email inbox", O, Freq.Weekdays, 10, 5, type = "errand"),
        j("backup", Area.DIGITAL, "Back up files and the laptop", O, Freq.Weekly(DayOfWeek.SUNDAY), 10, 5, type = "errand"),
        j("phone_storage", Area.DIGITAL, "Phone storage and updates", O, Freq.Monthly(1), 10, 5, type = "errand"),
        // university admin
        j("tt_check", Area.UNI, "Check the timetable for changes", S, Freq.Weekly(DayOfWeek.SUNDAY), 10, 5, type = "errand"),
        j("canvas_check", Area.UNI, "Check Canvas and MyUoB", S, Freq.Weekly(DayOfWeek.SUNDAY), 10, 5, type = "errand"),
        j("forms", Area.UNI, "Registration, forms and extensions", T, Freq.AsNeeded, 20, 10, type = "errand"),
        // faith and life
        j("church_bag", Area.FAITH, "Pack the church bag", S, Freq.Weekly(DayOfWeek.SUNDAY), 10, 5, match = listOf("church bag")),
        j("family", Area.FAITH, "Call or message family", O, Freq.Weekly(DayOfWeek.SATURDAY), 15, 10, type = "errand"),
        j("birthdays", Area.FAITH, "Birthdays and cards", T, Freq.AsNeeded, 10, 5, type = "errand"),
        // household, transport, guests
        j("plants", Area.HOUSEHOLD, "Water the plants", O, Freq.Weekly(DayOfWeek.SATURDAY), 5, 3),
        j("bulb", Area.HOUSEHOLD, "Replace a bulb, a battery or bin bags", T, Freq.AsNeeded, 10, 5),
        j("bus_pass", Area.TRANSPORT, "Top up the bus pass or buy tickets", T, Freq.AsNeeded, 10, 5, type = "errand"),
        j("guest_prep", Area.GUESTS, "Room prep for guests", T, Freq.AsNeeded, 150, 90),
        j("guest_clear", Area.GUESTS, "Clear up after guests", T, Freq.AsNeeded, 60, 30),
    )

    val byId: Map<String, AdminJob> = jobs.associateBy { it.id }
}

enum class LoadSet(val label: String) { LIGHT("Light upkeep"), STANDARD("Standard"), THOROUGH("Thorough") }

/** The person's choices. [loadSet] null means the library has not been set up, so nothing is placed (no change to a week until the person opts in). */
data class AdminState(
    val loadSet: LoadSet? = null, val capMin: Int = 90, val lighter: Boolean = false, val trimWork: Boolean = false, val on: Set<String> = emptySet(), val off: Set<String> = emptySet(),
) {
    fun enabled(job: AdminJob): Boolean {
        if (loadSet == null) return false
        if (job.set == JobSet.TRIGGERED) return false                   // triggered jobs are listed but never placed by themselves
        if (job.id in off) return false
        if (job.id in on) return true
        return when (loadSet) { LoadSet.LIGHT, LoadSet.STANDARD -> job.set == JobSet.STANDARD; LoadSet.THOROUGH -> true }
    }
    fun minutesFor(job: AdminJob) = if (lighter || loadSet == LoadSet.LIGHT) job.minMinutes else job.minutes
    fun encode() = "set=${loadSet?.name ?: ""};cap=$capMin;lighter=$lighter;trim=$trimWork;on=${on.sorted().joinToString(",")};off=${off.sorted().joinToString(",")}"

    companion object {
        fun decode(s: String?): AdminState {
            if (s.isNullOrBlank()) return AdminState()
            val m = s.split(";").mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 } }.associate { it[0] to it[1] }
            return AdminState(runCatching { LoadSet.valueOf(m["set"].orEmpty()) }.getOrNull(), m["cap"]?.toIntOrNull()?.coerceIn(15, 300) ?: 90, m["lighter"] == "true", m["trim"] == "true",
                m["on"].orEmpty().split(",").filter { it.isNotBlank() }.toSet(), m["off"].orEmpty().split(",").filter { it.isNotBlank() }.toSet())
        }
    }
}

private fun timesDays(n: Int): Set<DayOfWeek> = when {
    n <= 1 -> setOf(DayOfWeek.WEDNESDAY)
    n == 2 -> setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)
    n == 3 -> setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    else -> DayOfWeek.entries.take(n).toSet()
}

/** Whether a job is due on a date by its own frequency. */
fun dueOn(job: AdminJob, date: LocalDate): Boolean = when (val f = job.freq) {
    Freq.Daily -> true
    Freq.Weekdays -> date.dayOfWeek.value <= 5
    is Freq.Weekly -> date.dayOfWeek == f.day
    is Freq.TimesPerWeek -> date.dayOfWeek in timesDays(f.n)
    is Freq.EveryNDays -> (date.toEpochDay() + job.id.hashCode().toLong().mod(f.n.toLong())) % f.n == 0L
    is Freq.Fortnightly -> date.dayOfWeek == f.day && date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2 == 0
    is Freq.Monthly -> date.dayOfMonth == minOf(f.dayOfMonth, date.lengthOfMonth())
    Freq.AsNeeded -> false
}

data class AdminPlan(val slots: List<PatternSlot>, val problems: List<String>, val minutesByDay: Map<LocalDate, Int>)

private fun covered(job: AdminJob, base: List<PatternSlot>, date: LocalDate, zone: ZoneId) =
    job.match.isNotEmpty() && slotsFor(base, date, zone).any { ds -> job.match.any { ds.slot.title.contains(it, ignoreCase = true) } }

private fun weekKey(d: LocalDate) = d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

/**
 * Places the enabled jobs for [days] days from [from]. Spread over the week by frequency, never past the day's cap, only into free and empty time (the protected 6 free
 * hours a week are never used up, G3) and, only if the person allows it, by trimming a work block. A job that cannot be placed is reported with its day, never dropped
 * silently (4A.2).
 */
fun placeAdmin(base: List<PatternSlot>, state: AdminState, from: LocalDate, days: Int = 14, zone: ZoneId = LONDON): AdminPlan {
    if (state.loadSet == null) return AdminPlan(emptyList(), emptyList(), emptyMap())
    val out = ArrayList<PatternSlot>(); val problems = ArrayList<String>(); val perDay = LinkedHashMap<LocalDate, Int>()
    val freeUsed = HashMap<Int, Int>()
    fun freeBaseline(date: LocalDate): Int { val mon = date.with(DayOfWeek.MONDAY); return (0..6).sumOf { k -> slotsFor(base, mon.plusDays(k.toLong()), zone).filter { it.slot.type == "free" }.sumOf { it.slot.endMin - it.slot.startMin } } }
    val enabled = AdminLibrary.jobs.filter { state.enabled(it) }
    // The week is worked out from its Monday, so what was placed earlier in the week counts and a day does not move as the week goes on.
    val start = from.with(DayOfWeek.MONDAY)
    for (date in generateSequence(start) { it.plusDays(1) }.take(days + java.time.temporal.ChronoUnit.DAYS.between(start, from).toInt())) {
        val due = enabled.filter { dueOn(it, date) && !covered(it, base, date, zone) }.sortedWith(compareBy({ -it.priority }, { it.from }))
        var used = 0
        for (job in due) {
            val len = state.minutesFor(job)
            val name = "${date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}"
            if (used + len > state.capMin) { if (date >= from) problems += "$name: ${job.title} does not fit under the daily cap of ${state.capMin} minutes."; continue }
            var from0 = job.from; val to0 = job.to
            if (job.anchor == "wake") {
                val wake = slotsFor(base, date, zone).firstOrNull { it.slot.title.startsWith("Wake") } ?: run { if (date >= from) problems += "$name: ${job.title} needs a wake block and there is none."; null } ?: continue
                from0 = wake.slot.startMin
            }
            val current = base + out
            val allowFree = max0(freeBaseline(date) - 360) - (freeUsed[weekKey(date)] ?: 0)
            var placed: Pair<Int, Int>? = null
            for (g in freeGaps(current, date, from0, to0, len, zone)) {
                // try the start of the gap, then later starts inside it, so a gap partly over free time can still be used
                var s = g.first
                while (s + len <= g.second) {
                    val freeOverlap = slotsFor(current, date, zone).filter { it.slot.type == "free" }.sumOf { maxOf(0, minOf(it.slot.endMin, s + len) - maxOf(it.slot.startMin, s)) }
                    if (freeOverlap <= allowFree) { placed = s to freeOverlap; break }
                    s += 5
                }
                if (placed != null) break
            }
            if (placed == null && state.trimWork) {
                val piece = slotsFor(current, date, zone).filter { it.slot.date == null && it.slot.type in CUTTABLE_WORK && it.slot.endMin > from0 && it.slot.startMin < to0 && it.slot.endMin - it.slot.startMin >= len + 2 * MIN_PIECE_MIN }
                    .minByOrNull { it.slot.startMin }
                if (piece != null) placed = (piece.slot.startMin + MIN_PIECE_MIN) to 0
            }
            if (placed == null) { if (date >= from) problems += "$name: no room for ${job.title} (${len} minutes${if (job.anchor == "wake") " after waking" else ""})."; continue }
            freeUsed[weekKey(date)] = (freeUsed[weekKey(date)] ?: 0) + placed.second
            out += PatternSlot(date.dayOfWeek, placed.first, placed.first + len, job.title, job.type, "", false, "Admin: ${job.area.label}.", date = date)
            used += len; if (date >= from) perDay[date] = used
        }
    }
    return AdminPlan(out.filter { (it.date ?: from) >= from }, problems, perDay)
}

private fun max0(v: Int) = if (v < 0) 0 else v

/** MG19 to MG24 on a finished plan. */
fun checkAdmin(base: List<PatternSlot>, plan: AdminPlan, state: AdminState, zone: ZoneId = LONDON): List<String> {
    val out = ArrayList<String>(); val after = base + plan.slots
    for (date in plan.slots.mapNotNull { it.date }.distinct()) {
        val day = slotsFor(after, date, zone).filter { it.slot.type != "free" }.sortedBy { it.slot.startMin }
        for ((a, b) in day.zipWithNext()) if (b.slot.startMin < a.slot.endMin) out += "$date: ${a.slot.title} overlaps ${b.slot.title}."
        val before = slotsFor(base, date, zone).filter { it.slot.type == "sleep" || it.slot.type == "meal" || it.slot.hardAlarm }.map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }.toSet()
        val now = slotsFor(after, date, zone).filter { it.slot.type == "sleep" || it.slot.type == "meal" || it.slot.hardAlarm }.map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }.toSet()
        if (!now.containsAll(before)) out += "$date: a protected block would be changed."
        if ((plan.minutesByDay[date] ?: 0) > state.capMin) out += "$date: more than the daily cap of ${state.capMin} minutes."
    }
    for (s in plan.slots) {
        val job = AdminLibrary.jobs.firstOrNull { it.title == s.title } ?: continue
        if (s.endMin - s.startMin < job.minMinutes) out += "${job.title} is shorter than its minimum."
        if (s.startMin < 6 * 60 || s.endMin > 23 * 60) out += "${job.title} would be in the night."
    }
    return out.distinct()
}
