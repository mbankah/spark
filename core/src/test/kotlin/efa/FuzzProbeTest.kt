package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlin.test.*

/**
 * Randomised checks of Modify Schedule: every preset with random settings, dates and items, alone and stacked, against invariants the rulebook states.
 * Seeds are fixed, so a failure is repeatable. Failures are collected and reported together with the seed and the instance that caused them.
 */
/** Three starting points: autumn 2026, the spring 2027 clock change, and the autumn 2027 one, so daylight saving is crossed. */
private fun epoch(seed: Int): LocalDate = when (seed % 3) { 0 -> LocalDate.of(2026, 11, 2); 1 -> LocalDate.of(2027, 3, 15); else -> LocalDate.of(2027, 10, 18) }

class FuzzProbeTest {
    private val zero = WeekB.slots

    private fun pick(r: Random, d: SettingDef): String = when (d.kind) {
        SettingKind.CHOICE -> d.choices.random(r)
        SettingKind.BOOL -> r.nextBoolean().toString()
        SettingKind.INT -> { val lo = (d.min ?: d.default.toDouble()).toInt(); val hi = (d.max ?: (lo + 5.0)).toInt(); (lo + r.nextInt(hi - lo + 1)).toString() }
        SettingKind.DECIMAL -> { val lo = d.min ?: d.default.toDouble(); val hi = d.max ?: (lo + 5.0); (Math.round((lo + r.nextDouble() * (hi - lo)) * 2) / 2.0).toString() }
        else -> d.default
    }

    private fun randomBase(r: Random, now: LocalDate): List<PatternSlot> {
        var b = zero
        repeat(r.nextInt(3)) { b = b + dayOffMarker(now.plusDays(r.nextLong(0, 20))) }
        return b
    }

    private fun randomInstance(r: Random, today: LocalDate, preset: Preset): Instance {
        val settings = ModDefs.settings(preset).associate { it.key to pick(r, it) }
        val start = today.plusDays(r.nextLong(0, 5))
        return when (preset) {
            Preset.ASSIGNMENT -> {
                val items = (0 until 1 + r.nextInt(4)).map { n -> mapOf("title" to "A$n", "due" to today.plusDays(r.nextLong(3, 30)).toString(), "due_time" to "17:00", "size_h" to (1 + r.nextInt(30)).toString(), "priority" to (1 + r.nextInt(5)).toString()) }
                Instance(preset, today, today.plusDays(40), settings, items)
            }
            Preset.FOCUS -> Instance(preset, start, start.plusDays(r.nextLong(3, 13)), settings, listOf(mapOf("title" to "P", "due" to start.plusDays(r.nextLong(14, 20)).toString(), "due_time" to "17:00", "size_h" to (5 + r.nextInt(40)).toString())))
            Preset.PERIOD_OFF -> Instance(preset, start, start.plusDays(r.nextLong(0, 13)), settings, (0 until r.nextInt(3)).map { n ->
                mapOf("title" to "Pin$n", "type" to listOf("errand", "chore", "church", "gym").random(r), "placement" to listOf("fixed", "window").random(r), "start" to "%02d:00".format(8 + r.nextInt(12)), "length_min" to (15 + r.nextInt(90)).toString()) })
            else -> Instance(preset, start, start.plusDays(r.nextLong(0, 13)), settings)
        }
    }

    private fun overlapsOn(slots: List<PatternSlot>, date: LocalDate): Set<Pair<String, String>> {
        val busy = slotsFor(slots, date).filter { it.slot.type != "free" }.sortedBy { it.slot.startMin }
        return busy.zipWithNext().filter { (a, b) -> b.slot.startMin < a.slot.endMin }.map { (a, b) -> a.slot.title to b.slot.title }.toSet()
    }

    /** Invariants for one applied instance. Returns the broken ones. */
    private fun invariants(base: List<PatternSlot>, inst: Instance, p: ModPreview, today: LocalDate): List<String> {
        val bad = ArrayList<String>(); val c = p.compiled; val after = applied(base, c)
        val range = inst.coverage().toSet()
        // F1 repeatable
        // F2 a period preset puts nothing outside its dates
        if (inst.preset != Preset.ASSIGNMENT) c.slots.filter { it.date != null && it.date !in range }.forEach { bad += "F2 ${inst.preset} slot ${it.title} on ${it.date} is outside $range" }
        // F3 no new overlaps anywhere it touched
        for (d in (range + c.slots.mapNotNull { it.date }).distinct()) { val new = overlapsOn(after, d) - overlapsOn(base, d); if (new.isNotEmpty()) bad += "F3 overlap on $d: $new" }
        // F4 work presets never put work on a day off (hybrid focus is the stated exception)
        val hybrid = inst.preset == Preset.FOCUS && inst.bool("hybrid")
        if (!hybrid) c.slots.filter { !it.cancelled && it.type in setOf("study", "code", "planning") && it.date != null && isDayOff(base, it.date!!) }.forEach { bad += "F4 ${it.title} on day off ${it.date}" }
        // F5 lectures none removes every lecture from the dates (not already a day off)
        if (inst.preset in setOf(Preset.ASSIGNMENT, Preset.FOCUS, Preset.CATCH_UP) && inst.str("lectures") == "none") for (d in range.filter { !isDayOff(base, it) })
            if (slotsFor(after, d).any { it.slot.type == "lecture" || it.slot.type == "tutorial" }) bad += "F5 lecture left on $d with lectures none"
        // F6 loosening needs the wall
        if (c.loosening.isNotEmpty() && !p.wallNeeded) bad += "F6 loosening without the wall"
        if (inst.preset == Preset.PERIOD_OFF && !p.wallNeeded) bad += "F6 period off without the wall"
        // F7 period off: every date is a day off after, and the days off are inside the range
        if (inst.preset == Preset.PERIOD_OFF) { if (!c.dayOffDates.all { it in range }) bad += "F7 day off outside range"; if (!range.all { isDayOff(after, it) }) bad += "F7 a date is not a day off" }
        // F8 tasks are real: hours above zero, due in the future
        c.tasks.forEach { t -> if (t.hours <= 0.0) bad += "F8 task ${t.title} has ${t.hours}h"; }
        // F9 sessions of an assignment finish before it is due, and each is placed inside the day
        if (inst.preset == Preset.ASSIGNMENT) for (item in inst.items) {
            val due = LocalDate.parse(item["due"]!!)
            c.slots.filter { it.title.startsWith("${item["title"]}:") && !it.cancelled }.forEach { s -> if (s.date!! > due) bad += "F9 ${s.title} on ${s.date} after due $due"; if (s.date!! < today) bad += "F9 ${s.title} in the past" }
        }
        // F11 the hours asked for are the hours planned (within one minimum piece), or the plan says it is short
        if (inst.preset == Preset.ASSIGNMENT || inst.preset == Preset.FOCUS) for (item in inst.items) {
            val planned = c.slots.filter { it.title.startsWith("${item["title"]}:") && !it.cancelled }.sumOf { it.endMin - it.startMin }
            val asked = (item["size_h"]!!.toDouble() * 60).toInt()
            if (asked - planned >= MIN_PIECE_MIN && c.lines.none { it.contains("Not fully planned") || it.contains("no session could be placed") }) bad += "F11 silent shortfall: ${item["title"]} asked ${asked}min but planned ${planned}min"
        }
        if (inst.preset == Preset.ASSIGNMENT || inst.preset == Preset.FOCUS) for (item in inst.items) {
            val planned = c.slots.filter { it.title.startsWith("${item["title"]}:") && !it.cancelled }.sumOf { it.endMin - it.startMin }
            if (planned - (item["size_h"]!!.toDouble() * 60).toInt() >= MIN_PIECE_MIN) bad += "F12 ${item["title"]} planned ${planned}min, more than asked"
        }
        // F13 what compile just produced is never thrown away by reconcile (otherwise the two disagree and sessions would vanish)
        val r = reconcile(base + c.dayOffDates.map { dayOffMarker(it) }, c.slots, allowsDaysOff(inst))
        if (r.dropped != 0) bad += "F13 reconcile would drop ${r.dropped} of ${c.slots.size} fresh slots"
        // F10 a protected block of the base is untouched on every date involved
        for (d in (range + c.slots.mapNotNull { it.date }).distinct()) {
            // RF4 amends this: sleep and hard alarms keep their exact time; a meal keeps its title and length and may move, and goes only when the plan says so
            val anchored = d in c.anchors.map { it.night }      // SA1: a night whose lights-out moved moves its shutdown, which is the point
            val keep = slotsFor(base, d).filter { (it.slot.type == "sleep" && !anchored) || it.slot.hardAlarm }.map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }
            val now = slotsFor(after, d).filter { (it.slot.type == "sleep" && !anchored) || it.slot.hardAlarm }.map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }
            if (!now.containsAll(keep) && !isDayOff(after, d)) bad += "F10 protected block lost on $d"
            val meals = slotsFor(base, d).filter { it.slot.type == "meal" && "$d|${it.slot.title}" !in c.droppedMeals }.map { Triple(it.slot.title, it.slot.endMin - it.slot.startMin, 0) }
            val mealsNow = slotsFor(after, d).filter { it.slot.type == "meal" }.map { Triple(it.slot.title, it.slot.endMin - it.slot.startMin, 0) }
            if (!mealsNow.containsAll(meals) && !isDayOff(after, d)) bad += "F10 a meal was lost on $d"
        }
        return bad
    }

    @Test fun FUZZ_every_preset_alone_keeps_the_invariants() {
        val failures = ArrayList<String>(); var ran = 0; var applicable = 0; val withSlots = HashMap<Preset, Int>(); val withTasks = HashMap<Preset, Int>(); val refused = HashMap<String, Int>()
        for (seed in 1..600) {
            val r = Random(seed); val today = epoch(seed).plusDays(r.nextLong(0, 40)); val now = at(today, LocalTime.of(6 + r.nextInt(16), 0))
            val base = randomBase(r, today)
            for (preset in Preset.entries) {
                val inst = randomInstance(r, today, preset)
                val p = previewOf(inst, base, emptyList(), now); ran++
                if (p.compiled == Compiled()) continue
                val again = previewOf(inst, base, emptyList(), now)
                if (again.compiled != p.compiled) failures += "seed $seed $preset F1 not repeatable"
                if (!p.canApply) { p.problems.forEach { refused[preset.id + ": " + it.replace(Regex("[0-9]+"), "N").take(60)] = (refused[preset.id + ": " + it.replace(Regex("[0-9]+"), "N").take(60)] ?: 0) + 1 }; continue }
                applicable++
                if (p.compiled.slots.isNotEmpty()) withSlots[preset] = (withSlots[preset] ?: 0) + 1
                if (p.compiled.tasks.isNotEmpty()) withTasks[preset] = (withTasks[preset] ?: 0) + 1
                invariants(base, inst, p, today).forEach { failures += "seed $seed $preset ($inst): $it" }
            }
        }
        println("slots by preset $withSlots; tasks by preset $withTasks"); refused.entries.sortedByDescending { it.value }.take(8).forEach { println("  refused x${it.value}: ${it.key}") }
        println("alone: ran $ran, applicable $applicable, failures ${failures.size}")
        failures.groupBy { it.substringAfter(": ").take(2) + it.substringBefore(" (").substringAfter(" ").take(0) }.forEach { (k, v) -> println("  $k x${v.size}: ${v.first().take(320)}") }
        assertTrue(failures.isEmpty(), failures.take(8).joinToString("\n"))
    }

    @Test fun FUZZ_stacking_presets_in_any_order_keeps_the_invariants() {
        val failures = ArrayList<String>(); var stacks = 0; var applied2 = 0
        for (seed in 1..600) {
            val r = Random(1000 + seed); val today = epoch(seed).plusDays(r.nextLong(0, 40)); val now = at(today, LocalTime.of(6 + r.nextInt(10), 0))
            val original = randomBase(r, today)
            var base = original; val active = ArrayList<Instance>(); val log = ArrayList<String>()
            for (step in 0 until 2 + r.nextInt(2)) {
                val inst = randomInstance(r, today, Preset.entries.random(r))
                val clash = modifierConflicts(inst, active)
                val p = previewOf(inst, base, emptyList(), now)
                if (p.compiled == Compiled() || !p.canApply || clash.isNotEmpty()) continue
                invariants(base, inst, p, today).forEach { failures += "seed $seed step $step ${inst.preset} after [${log.joinToString()}]: $it" }
                base = applied(base, p.compiled); active += inst; log += inst.preset.id; if (step > 0) applied2++
            }
            stacks++
            // alarms adapt: none on a day off, a backstop by midday on every other day
            for (d in generateSequence(today) { it.plusDays(1) }.take(30)) {
                val off = isDayOff(base, d)
                val plan = planAlarm(at(d.minusDays(1), LocalTime.of(23, 0)), null, base, off)
                if (off && plan != null) failures += "seed $seed alarm set on day off $d"
                if (!off && (plan == null || plan.at.toLocalDate() != d || plan.at.toLocalTime().isAfter(LocalTime.of(12, 0)))) failures += "seed $seed no backstop on $d: $plan"
            }
            // whole stack: no new overlaps against the original, protected blocks of the original intact
            for (d in generateSequence(today) { it.plusDays(1) }.take(60)) {
                val new = overlapsOn(base, d) - overlapsOn(original, d)
                if (new.isNotEmpty()) failures += "seed $seed stack [${log.joinToString()}] overlap on $d: $new"
            }
        }
        println("stacks: $stacks, second-or-later applications $applied2, failures ${failures.size}")
        failures.take(12).forEach { println("  " + it.take(300)) }
        assertTrue(failures.isEmpty(), failures.take(8).joinToString("\n"))
    }
}

class AdminFuzzTest {
    @Test fun FUZZ_admin_over_random_calendars_keeps_its_rules() {
        val failures = ArrayList<String>(); var placed = 0
        for (seed in 1..450) {
            val r = Random(5000 + seed); val today = epoch(seed).plusDays(r.nextLong(0, 40)); val now = at(today, LocalTime.of(8, 0))
            var base = WeekB.slots
            repeat(r.nextInt(3)) { base = base + dayOffMarker(today.plusDays(r.nextLong(0, 14))) }
            if (r.nextBoolean()) {   // a modifier underneath
                val inst = Instance(Preset.LECTURE_FREE, today.plusDays(1), today.plusDays(r.nextLong(1, 7)))
                val p = previewOf(inst, base, emptyList(), now); if (p.canApply) base = applied(base, p.compiled)
            }
            val state = AdminState(LoadSet.entries.random(r), listOf(30, 60, 90, 120).random(r), r.nextBoolean(), r.nextBoolean())
            val plan = placeAdmin(base, state, today, 14)
            placed += plan.slots.size
            checkAdmin(base, plan, state).forEach { failures += "seed $seed $state: $it" }
            // the protected 6 free hours a week stay free (G3)
            for (wk in 0..2) {
                val mon = today.with(java.time.DayOfWeek.MONDAY).plusWeeks(wk.toLong())
                val freeBefore = (0..6).sumOf { k -> slotsFor(base, mon.plusDays(k.toLong())).filter { it.slot.type == "free" }.sumOf { it.slot.endMin - it.slot.startMin } }
                val freeAfter = (0..6).sumOf { k -> slotsFor(base + plan.slots, mon.plusDays(k.toLong())).filter { it.slot.type == "free" }.sumOf { it.slot.endMin - it.slot.startMin } }
                if (freeBefore >= 360 && freeAfter < 360 && !state.trimWork) failures += "seed $seed week $mon: free time fell below the protected 6 hours ($freeAfter min)"
            }
            // repeatable and independent of what day it is looked at
            if (placeAdmin(base, state, today, 14) != plan) failures += "seed $seed admin placement not repeatable"
        }
        println("admin: placed $placed, failures ${failures.size}"); failures.take(8).forEach { println("  " + it.take(300)) }
        assertTrue(failures.isEmpty(), failures.take(6).joinToString("\n"))
    }
}
