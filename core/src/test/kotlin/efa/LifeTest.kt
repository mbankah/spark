package efa

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

/** HO, SL, CO, VG, HS: the winter arc, the gradual shift, the coach's small pieces, the home modes. */
class LifeTest {
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 11, 18)

    @Test fun HO3_the_winter_arc_covers_the_vacation_in_four_spans_with_exams_and_three_without() {
        val arc = winterArc(true, today)
        assertEquals(4, arc.size)
        assertEquals(WINTER_VACATION_FROM, arc.first().instance.start)
        assertEquals(WINTER_VACATION_TO.plusDays(2), arc.last().instance.end)
        assertEquals(3, winterArc(false, today).size)
        // no gaps and no overlaps from the first day to the last
        for ((a, b) in arc.zipWithNext()) assertEquals(a.instance.end.plusDays(1), b.instance.start, "${a.label} to ${b.label}")
    }

    @Test fun HO3_every_span_of_the_arc_is_a_valid_modifier_and_ramp_up_asks_for_more_with_exams() {
        for (w in winterArc(true, today)) assertEquals(emptyList(), validate(w.instance, today), w.label)
        val rampExams = winterArc(true, today).first { it.label == "Ramp up" }.instance
        val rampNone = winterArc(false, today).first { it.label == "Ramp up" }.instance
        assertTrue(rampExams.dbl("work_h_max") > rampNone.dbl("work_h_max"))
    }

    @Test fun HO3_a_span_that_has_already_started_is_left_out() {
        val late = winterArc(true, LocalDate.of(2026, 12, 20))
        assertTrue(late.none { it.instance.start.isBefore(LocalDate.of(2026, 12, 20)) })
        assertEquals("Holiday", late.first().label)
    }

    @Test fun SL5_the_wake_moves_earlier_in_steps_of_15_every_two_nights_and_stops_at_the_target() {
        val plan = gradualShift(LocalTime.of(11, 0), LocalTime.of(8, 0), LocalDate.of(2026, 12, 20))
        assertEquals(12, plan.size)
        assertEquals(LocalTime.of(10, 45), plan.first().second); assertEquals(LocalTime.of(8, 0), plan.last().second)
        assertEquals(LocalDate.of(2026, 12, 22), plan[1].first)
        for ((a, b) in plan.zipWithNext()) assertTrue(java.time.Duration.between(b.second, a.second).toMinutes() in 0..30)
    }

    @Test fun SL5_it_never_moves_more_than_30_minutes_and_can_move_later_too() {
        assertFailsWith<IllegalArgumentException> { gradualShift(LocalTime.of(11, 0), LocalTime.of(8, 0), today, stepMin = 45) }
        val later = gradualShift(LocalTime.of(8, 0), LocalTime.of(9, 0), today, stepMin = 30, everyDays = 1)
        assertEquals(listOf(LocalTime.of(8, 30), LocalTime.of(9, 0)), later.map { it.second })
        assertEquals(emptyList(), gradualShift(LocalTime.of(8, 0), LocalTime.of(8, 0), today))
    }

    @Test fun HO5_leaving_and_arriving_have_floors_and_the_arrival_takes_the_reference_photo() {
        for (r in listOf(LEAVE_HOME, ARRIVE_HOME)) { assertTrue(r.stepsFor(Tier.FLOOR).isNotEmpty(), r.id); assertTrue(r.neverDropped) }
        assertTrue(ARRIVE_HOME.steps.any { it.proof == ProofTier.PHOTO && it.say.contains("study spot") })
        assertTrue(LEAVE_HOME.steps.any { it.say.contains("Bible") })
    }

    // ---- the coach ----
    private fun tap(day: Int, v: Int, h: Int = 10) = MoodTap(ZonedDateTime.of(2026, 11, day, h, 0, 0, 0, zone).toInstant(), v)

    @Test fun CO6_three_low_days_in_a_row_show_the_card_and_one_good_day_breaks_the_run() {
        val low = listOf(tap(16, 2), tap(17, 1), tap(18, 2), tap(18, 2, 20))
        assertTrue(lowMoodRun(low, today))
        assertFalse(lowMoodRun(listOf(tap(16, 2), tap(17, 4), tap(18, 2)), today))
        assertFalse(lowMoodRun(listOf(tap(17, 1), tap(18, 1)), today), "a day with no tap is not a low day")
    }

    @Test fun CO6_three_nights_under_four_hours_show_the_card() {
        assertTrue(shortNightRun(listOf(7.0, 3.5, 3.0, 3.9)))
        assertFalse(shortNightRun(listOf(3.5, 3.0, 5.0)))
        assertFalse(shortNightRun(listOf(3.0, 3.0)))
    }

    @Test fun CO6_the_people_list_has_the_real_routes_and_no_advice() {
        val names = PEOPLE.map { it.name }
        assertTrue(names.any { it.contains("GP") }); assertTrue(names.any { it.contains("Mental Health and Wellbeing") }); assertTrue(names.any { it.contains("Samaritans") })
        assertTrue(PEOPLE.first { it.name == "Samaritans" }.how.contains("116 123"))
        assertTrue(PEOPLE.first { it.name == "Shout" }.how.contains("85258"))
        assertTrue(PEOPLE.first { it.name == "Student Disability Service" }.how.contains("0121 414 5130"))
    }

    @Test fun CO7_a_reward_is_picked_from_your_own_menu_and_the_same_seed_gives_the_same_pick() {
        val menu = listOf("A playlist", "A coffee", "A walk", "A call", "An episode")
        assertEquals(rewardPick(menu, 7), rewardPick(menu, 7))
        assertTrue(rewardPick(menu, 3) in menu)
        val seen = (1L..60L).mapNotNull { rewardPick(menu, it) }.toSet()
        assertTrue(seen.size >= 4, "it varies: $seen")
        assertNull(rewardPick(emptyList(), 1)); assertNull(rewardPick(listOf(" ", ""), 1))
    }

    @Test fun CO9_todays_wins_are_at_most_three_true_facts_and_never_a_verdict() {
        assertEquals(listOf("Up on time.", "Bible done.", "2 study blocks proven."), todaysWins(7, 10, 2, bibleDone = true, upOnTime = true))
        assertEquals(listOf("6 of 10 steps done."), todaysWins(6, 10, 0, bibleDone = false, upOnTime = false))
        assertEquals(listOf("Nothing proven today. Next: water."), todaysWins(0, 10, 0, false, false))
        for (w in listOf(todaysWins(7, 10, 2, true, true), todaysWins(0, 4, 0, false, false), todaysWins(3, 3, 1, true, false)).flatten()) assertEquals(emptyList(), voiceProblems(w), w)
    }

    @Test fun VG2_LT3_CO8_the_banned_words_are_found_and_the_example_messages_in_the_manual_are_clean() {
        assertEquals(listOf("lazy"), voiceProblems("You were lazy today."))
        assertEquals(listOf("streak"), voiceProblems("Keep your streak!"))
        assertEquals(emptyList(), voiceProblems("Not proven. No reward. Next: study 10:10."))
        assertEquals(emptyList(), voiceProblems("6 of 10 steps done. The next one is water."))
        val manual = java.io.File("../docs/MANUAL.md").takeIf { it.exists() } ?: java.io.File("docs/MANUAL.md")
        if (manual.exists()) {
            val spoken = manual.readLines().filter { it.startsWith("| ") && it.count { c -> c == '|' } == 3 && it.contains("\"") }.map { it.substringAfterLast("| \"").substringBeforeLast("\" |") }
            assertTrue(spoken.size >= 15, "found the voice examples")
            for (s in spoken) assertEquals(emptyList(), voiceProblems(s), s)
        }
    }

    // ---- HS1: home modes ----
    private fun p(d: DayOfWeek, s: Int, e: Int, title: String, type: String) = PatternSlot(d, s, e, title, type, "", false, "")
    private val thu = DayOfWeek.THURSDAY
    private val pattern = listOf(
        p(thu, 450, 470, "Wake, shower", "routine"), p(thu, 470, 500, "Bible study", "routine"), p(thu, 540, 600, "Lecture", "lecture"),
        p(thu, 615, 735, "Notes and problems", "study-standard"), p(thu, 735, 760, "Lunch", "meal"), p(thu, 760, 775, "Walk", "travel"),
        p(thu, 900, 960, "Free time", "free"), p(thu, 1340, 1380, "Shutdown", "sleep"),
        p(DayOfWeek.FRIDAY, 450, 470, "Wake, shower", "routine"), p(DayOfWeek.FRIDAY, 540, 600, "Lecture", "lecture"),
    )
    private fun at(h: Int, m: Int = 0, d: Int = 19) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone)

    @Test fun HS1_the_mode_follows_the_moment() {
        assertEquals(HomeMode.MORNING, homeModeOf(pattern, at(7, 40)))
        assertEquals(HomeMode.BLOCK, homeModeOf(pattern, at(11, 0)))
        assertEquals(HomeMode.BETWEEN, homeModeOf(pattern, at(12, 45)))
        assertEquals(HomeMode.FREE, homeModeOf(pattern, at(15, 30)))
        assertEquals(HomeMode.WIND_DOWN, homeModeOf(pattern, at(22, 30)))
        assertEquals(HomeMode.NIGHT, homeModeOf(pattern, at(23, 55)))
    }

    @Test fun HS2_a_running_block_the_wind_down_and_the_night_hide_the_secondary_cards() {
        assertFalse(HomeMode.BLOCK.showsSecondary); assertFalse(HomeMode.WIND_DOWN.showsSecondary); assertFalse(HomeMode.NIGHT.showsSecondary)
        assertTrue(HomeMode.MORNING.showsSecondary); assertTrue(HomeMode.FREE.showsSecondary)
    }
}
