package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

/** VR1 to VR6: the VRNPM timer, the page timer, and the weekly remedial. */
class VrnpmTest {
    private val min = 60_000L

    @Test fun VR1_the_cycle_is_V_R_N_P_M_with_the_specs_times_and_breaks() {
        val p = vrPhases()
        assertEquals(listOf("V Video", "Break", "R Read", "Break", "N Notes", "Break", "P Practice", "Break", "M Marking"), p.map { it.title })
        assertEquals(105, p.filter { !it.isBreak }.sumOf { it.plannedMin }, "the optimal cycle is 105 minutes in the steps")
        assertEquals(listOf(5, 5, 10, 5), p.filter { it.isBreak }.map { it.plannedMin }, "5 between the steps and 10 after N")
        assertEquals(130, p.sumOf { it.plannedMin }, "130 minutes with the breaks")
        assertEquals(60, vrPhases(60).filter { it.step == VrStep.N }.single().plannedMin)
    }

    @Test fun VR2_a_cycle_runs_phase_by_phase_and_shows_time_left() {
        val v0 = vrView(null, 0); assertEquals(VrStatus.NOT_STARTED, v0.status); assertTrue(v0.chips.all { it.state == VrChipState.UPCOMING })
        var run = vrStart(45, 0L)
        val v1 = vrView(run, 4 * min); assertEquals(VrStatus.RUNNING, v1.status); assertEquals("V Video", v1.title); assertEquals(6 * min, v1.remainingMs); assertFalse(v1.over); assertEquals("6:00 left.", v1.summary)
        assertEquals(VrChipState.CURRENT, v1.chips[0].state); assertEquals(VrChipState.UPCOMING, v1.chips[1].state)
        run = vrAdvance(run, 10 * min)
        val v2 = vrView(run, 11 * min); assertTrue(v2.isBreak); assertEquals("Break", v2.title); assertEquals(VrChipState.DONE, v2.chips[0].state)
    }

    @Test fun VR3_a_step_may_overrun_and_the_overrun_is_always_shown_and_kept() {
        var run = vrStart(45, 0L)
        val at = 10 * min + 3 * min + 12_000L
        val v = vrView(run, at)
        assertTrue(v.over); assertEquals(3 * min + 12_000L, v.overMs); assertEquals(0L, v.remainingMs); assertEquals("Over by 3:12.", v.summary); assertEquals(VrChipState.OVER_NOW, v.chips[0].state)
        run = vrAdvance(run, at)
        assertEquals(VrChipState.DONE_OVER, vrView(run, at + 1000).chips[0].state, "the overrun stays on its step after it is finished")
        var t = at
        for (i in 1..8) { t += 10 * min; run = vrAdvance(run, t) }
        val done = vrView(run, t + min)
        assertEquals(VrStatus.DONE, done.status); assertTrue(done.summary.contains("Over: V +3 min"), done.summary)
    }

    @Test fun VR3_half_a_minute_either_way_is_on_time() {
        val run = vrStart(45, 0L)
        assertEquals(VrChipState.CURRENT, vrView(run, 10 * min + 20_000L).chips[0].state)
        assertEquals(VrChipState.OVER_NOW, vrView(run, 10 * min + 40_000L).chips[0].state)
    }

    @Test fun VR1_a_cycle_ends_after_the_last_step_and_does_nothing_more() {
        var run = vrStart(45, 0L); var t = 0L
        for (p in vrPhases(45)) { t += p.plannedMin * min; run = vrAdvance(run, t) }
        val v = vrView(run, t); assertEquals(VrStatus.DONE, v.status); assertEquals(130, v.actualMin); assertTrue(v.chips.all { it.state == VrChipState.DONE })
        assertEquals(run, vrAdvance(run, t + min), "a finished cycle is not advanced")
        assertEquals(VrRun(), vrAdvance(VrRun(), 5L), "an unstarted run stays unstarted")
    }

    @Test fun VR4_pages_are_timed_only_during_notes_and_the_average_comes_from_them() {
        var run = vrStart(45, 0L)
        assertEquals(run, vrPage(run, 5 * min), "no page is counted outside N")
        for (p in vrPhases(45).take(4)) run = vrAdvance(run, run.boundaries.last() + p.plannedMin * min)
        assertEquals("N Notes", vrView(run, run.boundaries.last()).title)
        val nStart = run.boundaries.last()
        run = vrPage(run, nStart + 11 * min); run = vrPage(run, nStart + 22 * min); run = vrPage(run, nStart + 33 * min)
        assertEquals(3, run.pages.size); assertEquals(11 * min, vrAveragePageMs(run))
        assertEquals(45, suggestedN(4, 11 * min)); assertEquals(30, suggestedN(3, 10 * min)); assertEquals(20, suggestedN(1, 2 * min)); assertEquals(90, suggestedN(10, 20 * min))
    }

    @Test fun VR1_a_run_survives_being_stored_as_text() {
        val run = VrRun(50, listOf(1, 2, 3), listOf(9, 10))
        assertEquals(run, VrRun.decode(run.encode())); assertEquals(VrRun(45), VrRun.decode(VrRun(45).encode())); assertNull(VrRun.decode("")); assertNull(VrRun.decode("junk"))
    }

    @Test fun VR6_a_cycle_earns_30_minutes_of_remedial_due_on_sunday_evening_and_after_a_catch_up_span() {
        val z = ZoneId.of("Europe/London")
        val fri = LocalDate.of(2026, 11, 20)
        assertEquals(ZonedDateTime.of(2026, 11, 22, 21, 0, 0, 0, z), remedialDue(fri, null, z), "ordinarily the Sunday of its week")
        assertEquals(ZonedDateTime.of(2026, 12, 6, 21, 0, 0, 0, z), remedialDue(fri, LocalDate.of(2026, 11, 29), z), "a cycle in the catch-up span waits until after it")
        assertEquals(ZonedDateTime.of(2026, 12, 6, 21, 0, 0, 0, z), remedialDue(LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 29), z))
        assertEquals(ZonedDateTime.of(2026, 12, 6, 21, 0, 0, 0, z), remedialDue(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 29), z), "a cycle after the span is its own week's")
    }

    @Test fun VR6_the_weeks_remedial_is_split_into_sessions_of_at_most_90_minutes() {
        assertEquals(emptyList(), remedialSessions(0)); assertEquals(listOf(30), remedialSessions(1)); assertEquals(listOf(90), remedialSessions(3)); assertEquals(listOf(90, 30), remedialSessions(4))
        assertEquals(570, remedialSessions(19).sum()); assertTrue(remedialSessions(19).all { it <= 90 })
    }

    @Test fun ST3_the_strip_also_tracks_blocks_with_a_proof() {
        val zone = ZoneId.of("Europe/London"); val day = LocalDate.of(2026, 11, 20)
        val slots = listOf(
            PatternSlot(DayOfWeek.FRIDAY, 600, 660, "Notes: Randomness", "study-standard", "", false, ""),
            PatternSlot(DayOfWeek.FRIDAY, 660, 720, "Notes: Game Theory", "study-light", "", false, ""),
            PatternSlot(DayOfWeek.FRIDAY, 720, 780, "Notes: NC", "study-standard", "", false, ""),
            PatternSlot(DayOfWeek.FRIDAY, 780, 840, "Walk", "travel", "", false, ""),
        )
        val d = slotsFor(slots, day, zone)
        val strip = dayStrip(d, emptyMap(), ZonedDateTime.of(day, java.time.LocalTime.of(12, 30), zone), verified = setOf(blockKey(d[0])))
        assertEquals(StripState.ON_TIME, strip[0].state); assertTrue(strip[0].tracked); assertFalse(strip[0].runnable)
        assertEquals(StripState.NOT_STARTED, strip[1].state, "its time went by with no proof")
        assertEquals(StripState.RUNNING, strip[2].state)
        assertEquals(StripState.PLANNED, strip[3].state); assertFalse(strip[3].tracked, "a walk has no proof to track")
    }
}
