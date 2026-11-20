package efa

import kotlin.test.*

/** VR12: the words and the clock of the lock screen notification. */
class VrNoteTest {
    private val min = 60_000L

    @Test fun VR12_a_running_step_counts_down_to_its_end() {
        val run = vrStart(45, 0L)
        val n = vrNote(run, 4 * min)
        assertEquals("V Video", n.title); assertEquals(VrNoteMode.COUNTDOWN, n.mode); assertEquals(10 * min, n.whenMs, "the clock counts to the end of V")
        assertEquals("V ●  R ○  N ○  P ○  M ○", n.chips)
    }

    @Test fun VR12_a_step_past_its_time_says_OVER_and_counts_up_from_when_it_ended() {
        val n = vrNote(vrStart(45, 0L), 13 * min)
        assertEquals(VrNoteMode.OVER, n.mode); assertEquals("OVER", n.text); assertEquals(10 * min, n.whenMs, "it counts up from the moment V should have ended")
        assertEquals("V ●+  R ○  N ○  P ○  M ○", n.chips)
    }

    @Test fun VR12_a_paused_cycle_stops_the_clock_and_says_how_long_is_left() {
        val run = vrPause(vrStart(45, 0L), 4 * min)
        val n = vrNote(run, 30 * min)
        assertEquals(VrNoteMode.PAUSED, n.mode); assertEquals("Paused, 6:00 left", n.text); assertEquals(0L, n.whenMs)
    }

    @Test fun VR12_done_steps_and_steps_done_by_override_are_marked() {
        var run = vrStart(vrPlan(VrConfig.SESSION), 0L, overridden = "VR")
        run = vrAdvance(run, 65 * min)                              // N done, the break begins
        val n = vrNote(run, 66 * min)
        assertEquals("V *  R *  N ✓  P ○  M ○", n.chips); assertEquals("Break", n.title)
    }
}
