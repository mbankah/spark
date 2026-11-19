package efa

/** RT2: every routine has three tiers. The floor is never dropped. */
enum class Tier { FULL, SHORT, FLOOR }

/** What a step does to the phone while it is live (RT3). */
enum class StepGate { NONE, SOCIAL_CLOSED, PHONE_AWAY, ONLY_NEVER_BLOCKED }

/**
 * RT3: a step has a cue, one short instruction, a time, the proof it needs, a gate, and the tiers it belongs to. [say] is what Spark says: the verb first (CO4, VG1).
 * [alsoAccepts] lists lower proofs that count too, so an NFC tap can stand in once the tag is bought (FB8). [parallelWith] names a step it runs beside (RT4).
 */
data class Step(
    val id: String, val say: String, val minutes: Int, val proof: ProofTier, val gate: StepGate = StepGate.NONE,
    val tiers: Set<Tier> = setOf(Tier.FULL), val alsoAccepts: Set<ProofTier> = emptySet(), val parallelWith: String? = null,
)

/** A routine: ordered steps, and how it behaves when time is short. [neverDropped] routines can only shrink to their floor. */
data class Routine(val id: String, val title: String, val steps: List<Step>, val neverDropped: Boolean = false, val veryShortSleepFloor: Boolean = false) {
    fun stepsFor(t: Tier): List<Step> = steps.filter { t in it.tiers }
    fun minutes(t: Tier): Int = serialMinutes(stepsFor(t))
}

/** RT4: parallel steps overlap, so the time is the longer of the pair, not the sum. */
fun serialMinutes(steps: List<Step>): Int {
    var total = 0; val seen = HashSet<String>()
    for (s in steps) {
        if (s.id in seen) continue
        val mate = s.parallelWith?.let { id -> steps.firstOrNull { it.id == id } }
        if (mate != null && mate.id !in seen) { total += maxOf(s.minutes, mate.minutes); seen += mate.id } else total += s.minutes
        seen += s.id
    }
    return total
}

/** RT4: parallel steps must name each other, exist, and not both need the hands or the phone away at once with a conflicting gate. */
fun validateParallel(steps: List<Step>): List<String> {
    val byId = steps.associateBy { it.id }; val out = ArrayList<String>()
    for (s in steps) {
        val p = s.parallelWith ?: continue
        val m = byId[p]
        if (m == null) { out += "${s.id} runs beside $p, which is not in the routine."; continue }
        if (m.parallelWith != s.id) out += "${s.id} and ${m.id} must name each other."
        if (s.proof == ProofTier.PHOTO && m.proof == ProofTier.PHOTO) out += "${s.id} and ${m.id} both need a photo at once."
    }
    return out
}

/** RT5: the tier picked from the time there is, the night's sleep and the day. A routine never goes below its floor, even when the time is shorter than the floor. */
fun pickTier(r: Routine, availableMin: Int, sleepHours: Double? = null, dayType: DayType? = null): Tier {
    var tier = when { availableMin >= r.minutes(Tier.FULL) -> Tier.FULL; availableMin >= r.minutes(Tier.SHORT) -> Tier.SHORT; else -> Tier.FLOOR }
    val cap = when {
        dayType == DayType.ILL -> Tier.FLOOR
        sleepHours != null && sleepHours < 4.0 && r.veryShortSleepFloor -> Tier.FLOOR
        sleepHours != null && sleepHours < 5.0 -> Tier.SHORT
        dayType == DayType.EXAM -> Tier.SHORT
        else -> null
    }
    if (cap != null && cap.ordinal > tier.ordinal) tier = cap
    return tier
}

private fun s(id: String, say: String, m: Int, proof: ProofTier, tiers: Set<Tier>, gate: StepGate = StepGate.NONE, alsoAccepts: Set<ProofTier> = emptySet(), parallelWith: String? = null) =
    Step(id, say, m, proof, gate, tiers, alsoAccepts, parallelWith)

private val ALL = setOf(Tier.FULL, Tier.SHORT, Tier.FLOOR)
private val FS = setOf(Tier.FULL, Tier.SHORT)
private val F = setOf(Tier.FULL)

/** RT6: Bible first, always; 30, 20 and 10 minutes; the floor is never dropped and breakfast is cut before it (your plan). The seat tag counts when it exists (FB8). */
val BIBLE = Routine("bible", "Bible", listOf(
    s("bible.sit", "Sit in the seat.", 1, ProofTier.TAP, ALL, StepGate.SOCIAL_CLOSED, alsoAccepts = setOf(ProofTier.NFC)),
    s("bible.open", "Open the next passage.", 1, ProofTier.TAP, ALL, StepGate.SOCIAL_CLOSED),
    s("bible.read", "Read.", 20, ProofTier.SENSOR, F, StepGate.SOCIAL_CLOSED),
    s("bible.read.s", "Read the passage.", 15, ProofTier.SENSOR, setOf(Tier.SHORT), StepGate.SOCIAL_CLOSED),
    s("bible.read.f", "Read one chapter, or the first section.", 7, ProofTier.SENSOR, setOf(Tier.FLOOR), StepGate.SOCIAL_CLOSED),
    s("bible.lines", "Write three lines: what it says, what it means, what to do.", 5, ProofTier.TAP, F, StepGate.SOCIAL_CLOSED),
    s("bible.line", "Write one line: what it says to you today.", 3, ProofTier.TAP, setOf(Tier.SHORT), StepGate.SOCIAL_CLOSED),
    s("bible.sentence", "Write one sentence.", 1, ProofTier.TAP, setOf(Tier.FLOOR), StepGate.SOCIAL_CLOSED),
    s("bible.prayer", "Pray for three minutes.", 3, ProofTier.TAP, F, StepGate.SOCIAL_CLOSED),
), neverDropped = true, veryShortSleepFloor = true)

val WAKE = Routine("wake", "Wake", listOf(
    s("wake.cross", "Cross the room.", 1, ProofTier.SENSOR, ALL, StepGate.SOCIAL_CLOSED),
    s("wake.photo", "Photograph the setup.", 1, ProofTier.PHOTO, ALL, StepGate.SOCIAL_CLOSED),
    s("wake.water", "Drink the water.", 1, ProofTier.TAP, FS, StepGate.SOCIAL_CLOSED),
    s("wake.light", "Light: curtains open, or step outside.", 2, ProofTier.SENSOR, FS, StepGate.SOCIAL_CLOSED),
), neverDropped = true)

val GETTING_READY = Routine("ready", "Getting ready", listOf(
    s("ready.shower", "Shower.", 15, ProofTier.TAP, F, StepGate.PHONE_AWAY, alsoAccepts = setOf(ProofTier.NFC)),
    s("ready.wash", "Wash.", 5, ProofTier.TAP, setOf(Tier.SHORT, Tier.FLOOR), StepGate.PHONE_AWAY),
    s("ready.dress", "Dress.", 5, ProofTier.TAP, ALL, StepGate.PHONE_AWAY),
    s("ready.pocket", "Check phone, keys, ID, wallet.", 1, ProofTier.TAP, ALL, StepGate.SOCIAL_CLOSED, alsoAccepts = setOf(ProofTier.NFC)),
    s("ready.bag", "Bag by the door. Ready photo.", 1, ProofTier.PHOTO, ALL, StepGate.SOCIAL_CLOSED),
), neverDropped = true)

/** SL1: a wind-down anchored to lights-out, built backwards from it. Full is about 50 minutes; Short about 35; the floor 8. */
val WIND_DOWN = Routine("winddown", "Wind-down", listOf(
    s("wd.jobs", "Do the last jobs: dishes, bins, tidy.", 10, ProofTier.PHOTO, FS),
    s("wd.screen", "Screen goes grey and warm.", 0, ProofTier.NONE, ALL),
    s("wd.dump", "Park what is on your mind in three lines.", 5, ProofTier.TAP, FS),
    s("wd.fun", "Put tonight's idea in a slot tomorrow.", 1, ProofTier.TAP, F),
    s("wd.prayer", "Prayer and stretches.", 20, ProofTier.TAP, F, StepGate.PHONE_AWAY),
    s("wd.prayer.s", "Prayer and stretches, short.", 10, ProofTier.TAP, setOf(Tier.SHORT), StepGate.PHONE_AWAY),
    s("wd.prayer.f", "Pray for three minutes.", 3, ProofTier.TAP, setOf(Tier.FLOOR), StepGate.PHONE_AWAY),
    s("wd.milk", "Warm milk, teeth, wash.", 10, ProofTier.TAP, FS, StepGate.PHONE_AWAY),
    s("wd.teeth", "Brush your teeth.", 3, ProofTier.TAP, setOf(Tier.FLOOR), StepGate.PHONE_AWAY),
    s("wd.dock", "Phone to the charger across the room.", 1, ProofTier.SENSOR, ALL, alsoAccepts = setOf(ProofTier.NFC)),
    s("wd.photo", "Shutdown photo.", 1, ProofTier.PHOTO, ALL),
), neverDropped = true)

/** RT4, DAY_ATLAS 12: cooking with a shower in parallel. */
val PASTA_AND_SHOWER = Routine("pastashower", "Cook and shower", listOf(
    s("cook.main", "Start the main.", 14, ProofTier.TAP, ALL),
    s("cook.pasta", "Pasta in. Timer 11 minutes.", 11, ProofTier.TAP, ALL, parallelWith = "cook.shower"),
    s("cook.shower", "Shower. Door open, timer on.", 11, ProofTier.TAP, ALL, parallelWith = "cook.pasta"),
    s("cook.plate", "Drain, sauce, plate.", 3, ProofTier.TAP, ALL),
))

val STUDY_BLOCK = Routine("study", "Study block", listOf(
    s("study.sit", "Sit and open the thing.", 1, ProofTier.SENSOR, ALL, StepGate.SOCIAL_CLOSED, alsoAccepts = setOf(ProofTier.NFC)),
    s("study.five", "Five minutes only. Start.", 5, ProofTier.SENSOR, ALL, StepGate.SOCIAL_CLOSED),
    s("study.block", "Work the block.", 0, ProofTier.SENSOR, ALL, StepGate.SOCIAL_CLOSED),
    s("study.proof", "Photograph the notes.", 1, ProofTier.PHOTO, ALL, StepGate.SOCIAL_CLOSED),
    s("study.reset", "Reset: stand, water, walk.", 10, ProofTier.SENSOR, FS, StepGate.PHONE_AWAY),
))

val ROUTINES: List<Routine> = listOf(BIBLE, WAKE, GETTING_READY, WIND_DOWN, PASTA_AND_SHOWER, STUDY_BLOCK)

/** RT8: prompts fade as a habit holds and come back when it slips. */
enum class Scaffold { GUIDED, CUED, TRACKED, YOURS }

/** [onTime] is the last 28 days, oldest first, true when the routine was done on time that day. Up at 21 of 28; Tracked at 24 of 28 once Cued; down after 3 misses in the last 7. */
fun nextScaffold(current: Scaffold, onTime: List<Boolean>): Scaffold {
    val last28 = onTime.takeLast(28); val last7 = onTime.takeLast(7)
    val misses = last7.count { !it }
    if (current != Scaffold.GUIDED && misses >= 3) return Scaffold.entries[current.ordinal - 1]
    val hits = last28.count { it }
    return when (current) {
        Scaffold.GUIDED -> if (last28.size >= 28 && hits >= 21) Scaffold.CUED else current
        Scaffold.CUED -> if (last28.size >= 28 && hits >= 24) Scaffold.TRACKED else current
        else -> current       // Yours is chosen by the person, not reached by a count
    }
}
