package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

/** GT1, DP5, VG1, VG3, HS7, HS8: the rules that say how the pieces fit, checked in one place. */
class RulesTest {
    private val zone = ZoneId.of("Europe/London")
    private val noon = ZonedDateTime.of(2026, 11, 19, 12, 0, 0, 0, zone)
    private val manual = java.io.File("../docs/MANUAL.md").readText().replace("\r\n", "\n")

    @Test fun GT1_one_function_decides_and_it_reads_the_day_the_cap_the_limit_and_the_allowances() {
        val thu = DayOfWeek.THURSDAY
        val work = listOf(PatternSlot(thu, 600, 780, "CS Project", "code", "", false, ""))
        val pkg = "com.instagram.android"
        val blocked = decide(pkg, noon, work, setOf(pkg))
        assertTrue(blocked is Decision.Block && blocked.reason == BlockReason.WORK_BLOCK, "the day model closes it")
        val opened = decide(pkg, noon, work, setOf(pkg), Allowances(appUntil = mapOf(pkg to noon.plusMinutes(20))))
        assertEquals(Decision.Allow, opened, "the wall's allowance opens it")
        val lim = ScreenLimit(setOf(pkg), 30)
        val spent = decide(pkg, noon, emptyList(), emptySet(), limit = lim, limitStart = mapOf(pkg to noon.minusMinutes(40)))
        assertTrue(spent is Decision.Block && spent.reason == BlockReason.SCREEN_LIMIT, "the limit closes it")
        val wa = "com.whatsapp"
        val capped = decide(wa, noon.plusMinutes(12), emptyList(), emptySet(), essentials = setOf(wa), cap = AppCap(), capStart = mapOf(wa to noon))
        assertTrue(capped is Decision.Block && capped.reason == BlockReason.APP_CAP, "the cap closes it")
        assertEquals(Decision.Allow, decide("com.android.dialer", noon, work, setOf(pkg), essentials = setOf("com.android.dialer")), "an essential is never blocked")
    }

    @Test fun DP5_the_order_is_safety_sleep_hard_routine_study_free_reward() {
        assertEquals(listOf("SAFETY", "SLEEP", "HARD_COMMITMENT", "ROUTINE_FLOOR", "STUDY", "FREE_TIME", "REWARD"), Precedence.entries.map { it.name })
        assertEquals(Precedence.SLEEP, precedenceOf("sleep")); assertEquals(Precedence.HARD_COMMITMENT, precedenceOf("lecture")); assertEquals(Precedence.HARD_COMMITMENT, precedenceOf("study", hard = true))
        assertEquals(Precedence.ROUTINE_FLOOR, precedenceOf("meal")); assertEquals(Precedence.STUDY, precedenceOf("code")); assertEquals(Precedence.FREE_TIME, precedenceOf("free"))
        assertTrue(yieldsFirst("free", "study") && yieldsFirst("study", "meal") && yieldsFirst("meal", "lecture") && !yieldsFirst("sleep", "free"))
    }

    @Test fun DP5_the_engine_gives_way_free_time_before_study_when_something_is_added() {
        val d = DayOfWeek.MONDAY
        val day = listOf(PatternSlot(d, 780, 900, "CS Project", "code", "", false, ""), PatternSlot(d, 900, 960, "Free time", "free", "", false, ""))
        val b = replanDay(day, LocalDate.of(2026, 11, 23), Disturbance.TimeTaken(900, 960, "Dentist", "errand"), emptySet(), zone)
        val after = slotsFor(day + b.slots, LocalDate.of(2026, 11, 23), zone).map { it.slot }
        val project = after.filter { it.title == "CS Project" && !it.cancelled }.sumOf { it.endMin - it.startMin }
        assertEquals(120, project, "the study block keeps all its time: free time gave way")
    }

    private fun allTexts(): List<String> =
        ROUTINES.flatMap { r -> r.steps.map { it.say } } + SUPPORT_ROUTES.flatMap { listOf(it.name, it.what) } + LOST_PHONE_STEPS + EmergencyAction.entries.flatMap { listOf(it.label, it.how) } +
            BlockReason.entries.map { refusalLine(it, "CS Project", "15:40") }

    @Test fun VG1_nothing_Spark_says_holds_a_banned_word() {
        for (t in allTexts()) assertEquals(emptyList(), voiceProblems(t), t)
    }

    @Test fun VG3_the_manual_names_every_routine_every_support_route_and_the_whatsapp_cap() {
        for (r in ROUTINES) assertTrue(r.title.lowercase() in manual.lowercase(), "MANUAL.md has no mention of the routine ${r.title}")
        for (s in SUPPORT_ROUTES) assertTrue(s.name.lowercase() in manual.lowercase() || s.name.lowercase().take(12) in manual.lowercase(), "MANUAL.md has no mention of ${s.name}")
        assertTrue("whatsapp" in manual.lowercase() && "10 minutes" in manual)
    }

    @Test fun HS7_a_label_is_short_and_has_no_sentence_in_it() {
        for (a in EmergencyAction.entries) { assertTrue(a.label.split(" ").size <= 4, a.label); assertFalse(a.label.contains(". "), a.label) }
        for (r in ROUTINES) for (s in r.steps) assertTrue(s.say.length <= 90, "${s.id}: ${s.say}")
    }

    @Test fun HS8_every_refusal_says_what_happened_why_and_when_it_ends_or_what_to_do() {
        for (r in BlockReason.entries) {
            val s = refusalLine(r, "CS Project", "15:40")
            assertTrue(s.length in 20..160, s)
            val next = listOf("until", "lifts", "ends", "cooldown", "first").any { it in s }
            assertTrue(next, "$r: $s has no end or next step")
        }
    }
}
