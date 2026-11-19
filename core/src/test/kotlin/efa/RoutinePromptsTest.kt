package efa

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

/** CO1, CO2, CO4, RT: routines are prompted at their time. */
class RoutinePromptsTest {
    private val zone = ZoneId.of("Europe/London")
    private fun p(title: String, type: String) = PatternSlot(DayOfWeek.THURSDAY, 470, 500, title, type, "", false, "")
    private val start = ZonedDateTime.of(2026, 11, 19, 7, 50, 0, 0, zone)

    @Test fun CO1_the_slots_of_a_day_map_to_the_routines_they_stand_for() {
        assertEquals(BIBLE, routineForSlot(p("Bible study", "routine")))
        assertEquals(GETTING_READY, routineForSlot(p("Wake, shower", "routine"))); assertEquals(GETTING_READY, routineForSlot(p("Breakfast, dress, bag", "routine")))
        assertEquals(WIND_DOWN, routineForSlot(p("Shutdown", "sleep"))); assertEquals(WIND_DOWN, routineForSlot(p("Evening prayer", "routine")))
        assertEquals(STUDY_BLOCK, routineForSlot(p("Notes and problems: Randomness", "study-standard"))); assertEquals(STUDY_BLOCK, routineForSlot(p("CS Project", "code")))
        assertNull(routineForSlot(p("Lunch", "meal"))); assertNull(routineForSlot(p("Lecture", "lecture"))); assertNull(routineForSlot(p("Bible study", "routine").copy(cancelled = true)))
    }

    @Test fun CO2_a_prompt_goes_feel_then_spoken_then_notification_then_the_gate_and_then_stops() {
        assertEquals(PromptLevel.HAPTIC, promptLevelAt(0, false)); assertEquals(PromptLevel.HAPTIC, promptLevelAt(2, false))
        assertEquals(PromptLevel.SPOKEN, promptLevelAt(3, false)); assertEquals(PromptLevel.NOTIFICATION, promptLevelAt(6, false)); assertEquals(PromptLevel.GATE, promptLevelAt(10, false))
        assertNull(promptLevelAt(30, false), "after half an hour it stops")
        assertNull(promptLevelAt(1, true), "a started routine is not prompted"); assertNull(promptLevelAt(-1, false), "before the block it is not prompted")
    }

    @Test fun CO1_one_voice_a_prompt_within_twenty_seconds_of_the_last_is_dropped() {
        val t = Instant.parse("2026-11-19T07:50:00Z")
        assertTrue(mayPrompt(null, t)); assertFalse(mayPrompt(t, t.plusSeconds(10))); assertTrue(mayPrompt(t, t.plusSeconds(20)))
    }

    @Test fun CO4_every_prompt_line_is_short_has_the_routine_and_the_verb_first_and_uses_no_banned_word() {
        for (r in listOf(BIBLE, GETTING_READY, WIND_DOWN, STUDY_BLOCK)) for (t in Tier.entries) for (l in PromptLevel.entries) {
            val s = promptLine(r, t, l)
            assertTrue(s.length <= 120, s); assertEquals(emptyList(), voiceProblems(s), s)
        }
        assertEquals("Sit in the seat.", promptLine(BIBLE, Tier.FULL, PromptLevel.HAPTIC))
        assertEquals("Bible. Sit in the seat.", promptLine(BIBLE, Tier.FULL, PromptLevel.SPOKEN))
        assertTrue(promptLine(STUDY_BLOCK, Tier.FULL, PromptLevel.GATE).contains("holds your phone"))
    }

    @Test fun RT_the_routine_counts_as_started_by_any_of_its_steps_in_the_block() {
        val before = Fact(FactType.TAP, start.toInstant().minusSeconds(600), "tap", 1.0, "step:bible.sit")
        val inBlock = Fact(FactType.TAP, start.toInstant().plusSeconds(60), "tap", 1.0, "step:bible.open")
        assertFalse(routineStarted(BIBLE, listOf(before), start)); assertTrue(routineStarted(BIBLE, listOf(before, inBlock), start))
        assertFalse(routineStarted(STUDY_BLOCK, listOf(inBlock), start), "a step of another routine does not count")
        assertTrue(routineStarted(BIBLE, listOf(Fact(FactType.TAP, start.toInstant().plusSeconds(1), "routine", 1.0, "routine:bible")), start))
    }

    @Test fun RT5_the_time_available_is_to_the_next_block_or_an_hour() {
        assertEquals(30, availableMinutes(start, start.plusMinutes(30))); assertEquals(60, availableMinutes(start, null)); assertEquals(0, availableMinutes(start, start.minusMinutes(5)))
    }
}
