package efa

import kotlin.test.*

/** VR9: a cycle can be paused. The time paused is not counted in the step, and it is kept and shown. */
class VrPauseTest {
    private val min = 60_000L

    @Test fun VR9_a_paused_cycle_stops_its_clock_and_a_resume_carries_on_from_where_it_was() {
        var run = vrStart(45, 0L)
        run = vrPause(run, 4 * min)
        val v = vrView(run, 30 * min)
        assertTrue(v.paused); assertEquals(6 * min, v.remainingMs, "four minutes of V gone, none lost while paused"); assertTrue(v.summary.startsWith("Paused. 6:00 left"), v.summary)
        assertFalse(v.over, "a long pause is not an overrun")
        run = vrResume(run, 30 * min)
        val v2 = vrView(run, 33 * min); assertFalse(v2.paused); assertEquals(3 * min, v2.remainingMs, "26 minutes paused, 7 of V used")
    }

    @Test fun VR9_a_step_that_overruns_after_a_pause_counts_only_the_time_not_paused() {
        var run = vrStart(45, 0L)
        run = vrResume(vrPause(run, 5 * min), 25 * min)
        val v = vrView(run, 25 * min + 8 * min)       // 5 + 8 = 13 minutes of V in a 10 minute step
        assertTrue(v.over); assertEquals(3 * min, v.overMs); assertEquals("Over by 3:00.", v.summary)
    }

    @Test fun VR9_pausing_does_nothing_unless_a_cycle_is_running_and_not_already_paused() {
        assertEquals(VrRun(), vrPause(VrRun(), 5L), "not started"); assertEquals(VrRun(), vrResume(VrRun(), 5L))
        val run = vrPause(vrStart(45, 0L), 1000L)
        assertEquals(run, vrPause(run, 2000L), "already paused"); assertEquals(listOf(1000L), run.pauses)
        var done = vrStart(45, 0L); var t = 0L
        for (p in vrPhases(45)) { t += p.plannedMin * min; done = vrAdvance(done, t) }
        assertEquals(done, vrPause(done, t + min), "a finished cycle cannot be paused")
    }

    @Test fun VR9_moving_on_while_paused_ends_the_pause_first() {
        var run = vrPause(vrStart(45, 0L), 3 * min)
        run = vrAdvance(run, 20 * min)
        assertFalse(run.paused); assertEquals(listOf(3 * min, 20 * min), run.pauses); assertEquals(listOf(0L, 20 * min), run.boundaries)
        val v = vrView(run, 21 * min); assertEquals("Break", v.title); assertEquals(4 * min, v.remainingMs, "the break is 5 minutes and one has gone")
        assertEquals(VrChipState.DONE, v.chips[0].state, "17 minutes of the 20 were paused, so V took 3")
    }

    @Test fun VR9_the_pause_is_kept_and_shown_when_the_cycle_is_done() {
        var run = vrStart(45, 0L)
        run = vrResume(vrPause(run, 2 * min), 12 * min)         // ten minutes paused during V
        var t = 12 * min
        for (p in vrPhases(45)) { t += p.plannedMin * min; run = vrAdvance(run, t) }
        val v = vrView(run, t)
        assertEquals(VrStatus.DONE, v.status); assertEquals(10, v.pausedMin); assertTrue(v.summary.contains("Paused 10 min."), v.summary)
        assertEquals(132, v.actualMin, "142 minutes on the clock, 10 of them paused")
    }

    @Test fun VR9_timed_pages_leave_out_the_time_paused() {
        var run = vrStart(45, 0L)
        for (p in vrPhases(45).take(4)) run = vrAdvance(run, run.boundaries.last() + p.plannedMin * min)
        val nStart = run.boundaries.last()
        run = vrResume(vrPause(run, nStart + 5 * min), nStart + 15 * min)      // ten minutes paused
        run = vrPage(run, nStart + 21 * min)                                    // 21 minutes since N began, 11 of them working
        assertEquals(11 * min, vrAveragePageMs(run))
    }

    @Test fun VR9_a_run_with_pauses_survives_being_stored_and_old_runs_still_read() {
        val run = VrRun(45, listOf(1, 2), listOf(3), null, listOf(10, 20, 30))
        assertEquals(run, VrRun.decode(run.encode())); assertTrue(VrRun.decode(run.encode())!!.paused)
        assertEquals(VrRun(45, listOf(1L), emptyList(), null, emptyList()), VrRun.decode("45|1||"), "a run stored before pauses had no fifth field")
        assertEquals(VrRun(45, listOf(1L), emptyList(), null, emptyList()), VrRun.decode("45|1||"))
    }
}
