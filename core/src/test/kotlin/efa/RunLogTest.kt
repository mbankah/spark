package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

/** ST2, ST3: the start hold, the record of what happened, late and over, and the day strip. */
class RunLogTest {
    private val zone = ZoneId.of("Europe/London")
    private val day = LocalDate.of(2026, 11, 20)
    private fun ms(h: Int, m: Int) = ZonedDateTime.of(day, java.time.LocalTime.of(h, m), zone).toInstant().toEpochMilli()
    private val ps = ms(12, 15); private val pe = ms(12, 35)

    @Test fun ST2_the_hold_grows_with_the_block_between_two_and_six_seconds() {
        assertEquals(2_000L, holdMillis(5)); assertEquals(2_000L, holdMillis(15)); assertEquals(6_000L, holdMillis(120)); assertEquals(6_000L, holdMillis(400))
        assertTrue(holdMillis(67) in 3_900L..4_100L, "about the middle"); assertTrue(holdMillis(20) in 2_000L..6_000L)
        var last = 0L; for (m in 0..200) { val h = holdMillis(m); assertTrue(h >= last, "never shorter for a longer block"); last = h }
        assertTrue(holdMillis(130) <= 6_000L, "a long study block is not a 13 second hold")
    }

    @Test fun ST2_only_the_blocks_with_no_proof_are_runnable() {
        fun p(type: String, cancelled: Boolean = false) = PatternSlot(DayOfWeek.FRIDAY, 735, 755, "X", type, "", false, "", cancelled = cancelled)
        for (t in listOf("routine", "meal", "errand", "planning")) assertTrue(isRunnable(p(t)), t)
        for (t in listOf("free", "sleep", "travel", "study", "study-standard", "code", "lecture", "chore", "gym", "match")) assertFalse(isRunnable(p(t)), t)
        assertFalse(isRunnable(p("routine", cancelled = true)))
    }

    @Test fun ST2_a_record_survives_being_stored_as_text() {
        for (r in listOf(RunRecord(), RunRecord(startMs = 5), RunRecord(5, 9), RunRecord(skippedReason = "Too tired"), RunRecord(5, null, "a|b"))) {
            val back = RunRecord.decode(r.encode()); assertNotNull(back)
            assertEquals(r.startMs, back.startMs); assertEquals(r.endMs, back.endMs); assertEquals(r.skippedReason?.replace("|", "/"), back.skippedReason)
        }
        assertNull(RunRecord.decode(null)); assertNull(RunRecord.decode("")); assertNull(RunRecord.decode("nonsense"))
    }

    @Test fun ST2_a_block_not_started_says_when_it_starts_and_then_how_late_it_is() {
        val before = runView(null, ps, pe, ms(12, 0), zone); assertEquals(RunStatus.NOT_STARTED, before.status); assertEquals("Starts at 12:15.", before.line); assertFalse(before.shifted)
        val after = runView(null, ps, pe, ms(12, 27), zone); assertEquals(RunStatus.NOT_STARTED, after.status); assertEquals(12, after.lateMin); assertTrue(after.line.contains("12 min past"))
    }

    @Test fun ST2_a_start_within_five_minutes_is_on_time_and_a_later_one_is_said_to_be_late() {
        val ok = runView(RunRecord(startMs = ms(12, 19)), ps, pe, ms(12, 25), zone); assertEquals(RunStatus.RUNNING, ok.status); assertEquals(4, ok.lateMin); assertFalse(ok.shifted); assertFalse(ok.line.contains("late"))
        val late = runView(RunRecord(startMs = ms(12, 27)), ps, pe, ms(12, 30), zone); assertEquals(12, late.lateMin); assertTrue(late.shifted); assertTrue(late.line.contains("12 min late"), late.line)
        val early = runView(RunRecord(startMs = ms(12, 5)), ps, pe, ms(12, 10), zone); assertEquals(0, early.lateMin)
    }

    @Test fun ST2_a_block_running_past_its_end_says_how_far_over_and_a_finished_one_keeps_its_times() {
        val over = runView(RunRecord(startMs = ps), ps, pe, ms(12, 50), zone); assertEquals(RunStatus.RUNNING, over.status); assertEquals(15, over.overMin); assertTrue(over.shifted); assertTrue(over.line.contains("Over by 15"))
        val done = runView(RunRecord(ps, ms(12, 52)), ps, pe, ms(13, 0), zone)
        assertEquals(RunStatus.DONE, done.status); assertEquals(17, done.overMin); assertTrue(done.line.startsWith("Done 12:15 to 12:52"), done.line); assertTrue(done.line.contains("17 min over"))
        assertFalse(runView(RunRecord(ps, ms(12, 38)), ps, pe, ms(13, 0), zone).shifted, "3 minutes over is on time")
    }

    @Test fun ST2_a_block_that_did_not_happen_says_so_with_its_reason() {
        val v = runView(RunRecord(skippedReason = "Too tired"), ps, pe, ms(13, 0), zone)
        assertEquals(RunStatus.SKIPPED, v.status); assertEquals("Did not happen: Too tired.", v.line); assertFalse(v.shifted)
    }

    @Test fun ST2_the_shift_is_rounded_up_to_the_steps_Running_over_takes() {
        assertEquals(5, shiftMinutes(1)); assertEquals(5, shiftMinutes(5)); assertEquals(10, shiftMinutes(6)); assertEquals(15, shiftMinutes(12)); assertEquals(180, shiftMinutes(500))
    }

    @Test fun ST3_the_strip_draws_each_block_by_what_happened() {
        val slots = listOf(
            PatternSlot(DayOfWeek.FRIDAY, 735, 755, "Wake, shower", "routine", "", false, ""),
            PatternSlot(DayOfWeek.FRIDAY, 755, 785, "Bible study", "routine", "", false, ""),
            PatternSlot(DayOfWeek.FRIDAY, 785, 815, "Lunch", "meal", "", false, ""),
            PatternSlot(DayOfWeek.FRIDAY, 815, 900, "Free time", "free", "", false, ""),
            PatternSlot(DayOfWeek.FRIDAY, 900, 1000, "Notes", "study-standard", "", false, ""),
            PatternSlot(DayOfWeek.FRIDAY, 1000, 1040, "Dinner", "meal", "", false, ""),
        )
        val d = slotsFor(slots, day, zone)
        val wake = d.first { it.slot.title == "Wake, shower" }; val bible = d.first { it.slot.title == "Bible study" }
        val records = mapOf(blockKey(wake) to RunRecord(ms(12, 16), ms(12, 36)), blockKey(bible) to RunRecord(ms(12, 45), null))
        val strip = dayStrip(d, records, ZonedDateTime.of(day, java.time.LocalTime.of(13, 40), zone))
        assertEquals(listOf("Wake, shower", "Bible study", "Lunch", "Notes", "Dinner"), strip.map { it.title }, "free time is left out")
        val by = strip.associateBy { it.title }
        assertEquals(StripState.ON_TIME, by.getValue("Wake, shower").state)
        assertEquals(StripState.RUNNING, by.getValue("Bible study").state); assertEquals(765, by.getValue("Bible study").actualStart)
        assertEquals(StripState.NOT_STARTED, by.getValue("Lunch").state, "its time has come and nothing was recorded")
        assertEquals(StripState.PLANNED, by.getValue("Notes").state); assertFalse(by.getValue("Notes").runnable)
        assertEquals(StripState.PLANNED, by.getValue("Dinner").state, "later today")
    }

    @Test fun ST3_a_block_finished_late_or_skipped_is_drawn_that_way() {
        val slots = listOf(PatternSlot(DayOfWeek.FRIDAY, 735, 755, "Wake, shower", "routine", "", false, ""), PatternSlot(DayOfWeek.FRIDAY, 755, 785, "Bible study", "routine", "", false, ""))
        val d = slotsFor(slots, day, zone)
        val records = mapOf(blockKey(d[0]) to RunRecord(ms(12, 30), ms(12, 50)), blockKey(d[1]) to RunRecord(skippedReason = "Too tired"))
        val strip = dayStrip(d, records, ZonedDateTime.of(day, java.time.LocalTime.of(14, 0), zone))
        assertEquals(StripState.SHIFTED, strip[0].state); assertEquals(StripState.SKIPPED, strip[1].state)
    }
}
