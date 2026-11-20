package efa

import kotlin.test.*

/** VR11: the ring that the home screen draws for a running cycle. */
class VrRingTest {
    private val min = 60_000L

    @Test fun VR11_the_ring_is_one_full_turn_with_each_phase_as_wide_as_its_time() {
        val ring = vrRing(null, 0L)
        assertEquals(9, ring.size); assertEquals(1f, ring.sumOf { it.sweep.toDouble() }.toFloat(), 0.0001f)
        assertEquals(listOf('V', null, 'R', null, 'N', null, 'P', null, 'M'), ring.map { it.letter })
        assertEquals(45f / 130f, ring[4].sweep, 0.0001f, "notes are 45 of the 130 minutes")
        assertTrue(ring.all { it.fill == 0f }); assertEquals(0f, ring[0].start)
    }

    @Test fun VR11_finished_phases_are_full_the_running_one_is_part_full_and_the_rest_are_empty() {
        var run = vrStart(45, 0L)
        run = vrAdvance(run, 10 * min)                      // V done, the first break begins
        run = vrAdvance(run, 15 * min); run = vrAdvance(run, 20 * min); run = vrAdvance(run, 25 * min)   // break, R, break done; N begins at 25
        val ring = vrRing(run, 25 * min + 18 * min + 45_000L)       // 18:45 into N
        assertEquals(listOf(1f, 1f, 1f, 1f), ring.take(4).map { it.fill })
        assertEquals(18.75f / 45f, ring[4].fill, 0.001f); assertFalse(ring[4].over)
        assertTrue(ring.drop(5).all { it.fill == 0f })
    }

    @Test fun VR11_a_step_past_its_time_is_full_and_marked_over_and_a_pause_does_not_count() {
        var run = vrStart(45, 0L)
        assertTrue(vrRing(run, 13 * min)[0].over); assertEquals(1f, vrRing(run, 13 * min)[0].fill)
        run = vrResume(vrPause(run, 5 * min), 25 * min)     // 20 minutes paused
        val r = vrRing(run, 28 * min)[0]                    // 5 + 3 = 8 minutes of V
        assertFalse(r.over); assertEquals(0.8f, r.fill, 0.001f)
    }

    @Test fun VR11_a_finished_cycle_is_a_full_ring_and_a_step_done_by_override_has_no_width() {
        var run = vrStart(vrPlan(VrConfig.SESSION), 0L, overridden = "VR"); var t = 0L
        val ringStart = vrRing(run, 1L)
        assertEquals(listOf('N', null, 'P', null, 'M'), ringStart.map { it.letter }, "V and R are not on the ring")
        assertEquals(1f, ringStart.sumOf { it.sweep.toDouble() }.toFloat(), 0.0001f)
        for (p in vrPhases(run.planMinutes).drop(4)) { t += p.plannedMin * min; run = vrAdvance(run, t) }
        assertTrue(vrRing(run, t).all { it.fill == 1f })
    }
}
