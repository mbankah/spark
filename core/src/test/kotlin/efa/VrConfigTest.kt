package efa

import kotlin.test.*

/** VR7, VR8: the cycle's settings, the longest-cycle cap, and the Red Engine switch for the remedial. */
class VrConfigTest {
    private val min = 60_000L

    @Test fun VR7_the_default_is_the_optimal_cycle_of_the_spec_and_the_ideal_cycle_is_the_other_preset() {
        assertEquals(listOf(10, 5, 5, 5, 45, 10, 30, 5, 15), vrPlan(VrConfig.OPTIMAL).let { listOf(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8]) })
        assertEquals(105, vrSteps(VrConfig.OPTIMAL).sum()); assertEquals("Optimal", VrConfig.OPTIMAL.preset())
        assertEquals(listOf(15, 15, 65, 45, 25), vrSteps(VrConfig.IDEAL)); assertEquals(165, vrSteps(VrConfig.IDEAL).sum()); assertEquals("Ideal", VrConfig.IDEAL.preset())
        assertEquals(10, VrConfig.IDEAL.remedialMin, "the ideal cycle books 10 minutes of remedial, the optimal 30")
        assertNull(VrConfig.OPTIMAL.copy(n = 50).preset(), "your own times are not a preset")
    }

    @Test fun VR7_each_step_and_break_can_be_set_and_the_plan_follows() {
        val c = VrConfig(v = 20, r = 10, n = 60, p = 25, m = 10, breakBetween = 3, breakAfterN = 15)
        assertEquals(listOf(20, 3, 10, 3, 60, 15, 25, 3, 10), vrPlan(c))
        assertEquals(125, vrPhases(vrPlan(c)).filter { !it.isBreak }.sumOf { it.plannedMin })
        assertEquals(125, vrSteps(c).sum())
        assertEquals(listOf(10, 5, 5, 5, 60, 10, 30, 5, 15), vrPlan(VrConfig.OPTIMAL, nMin = 60), "a timed-pages N replaces the N time")
    }

    @Test fun VR7_the_longest_cycle_shrinks_every_step_in_proportion_to_fit() {
        val capped = VrConfig.IDEAL.copy(capMin = 105)
        val steps = vrSteps(capped)
        assertTrue(steps.sum() <= 105, steps.toString()); assertTrue(steps.all { it >= 5 && it % 5 == 0 }, steps.toString())
        assertTrue(steps[2] > steps[0], "notes stay the longest step"); assertTrue(steps.sum() >= 95, "it does not shrink more than it must: $steps")
        assertEquals(vrSteps(VrConfig.OPTIMAL), vrSteps(VrConfig.OPTIMAL.copy(capMin = 150)), "a cap above the cycle changes nothing")
        assertEquals(165, vrSteps(VrConfig.IDEAL.copy(capMin = 0)).sum(), "0 is no limit")
        val tiny = VrConfig(v = 5, r = 5, n = 5, p = 5, m = 5, capMin = 45)
        assertEquals(listOf(5, 5, 5, 5, 5), vrSteps(tiny), "a step is never under 5")
    }

    @Test fun VR7_a_cap_is_applied_after_the_timed_N_time() {
        val plan = vrPlan(VrConfig.OPTIMAL.copy(capMin = 90), nMin = 60)
        val steps = listOf(plan[0], plan[2], plan[4], plan[6], plan[8])
        assertTrue(steps.sum() <= 90, steps.toString()); assertEquals(steps.max(), steps[2], "N is still the largest after the shrink: $steps")
    }

    @Test fun VR7_the_settings_survive_being_stored_and_bad_text_gives_the_default() {
        val c = VrConfig(15, 10, 50, 35, 20, 3, 12, 120, 5, 20, true)
        assertEquals(c, VrConfig.decode(c.encode()))
        for (bad in listOf(null, "", "1,2,3", "a,b,c,d,e,f,g,h,i,j,k")) assertEquals(VrConfig(), VrConfig.decode(bad))
        assertEquals(VrConfig(v = 120, r = 5), VrConfig.decode(VrConfig(v = 500, r = -4).clamped().encode()).copy(n = 45, p = 30, m = 15), "out of range values are brought in")
    }

    @Test fun VR7_a_cycle_keeps_the_plan_it_started_with_when_the_settings_change() {
        val plan = vrPlan(VrConfig.IDEAL)
        var run = vrStart(plan, 0L)
        assertEquals(plan, run.planMinutes); assertEquals(plan, VrRun.decode(run.encode())!!.planMinutes)
        assertEquals(1L, vrView(run, 14 * min).remainingMs / min, "15 minutes of V in the ideal plan, 14 gone")
        assertEquals("1:00 left.", vrView(run, 14 * min).summary)
        run = vrAdvance(run, 15 * min)
        assertEquals("Break", vrView(run, 15 * min).title)
        assertEquals(VrRun(45, listOf(0L), emptyList()).planMinutes, vrPlan(VrConfig.OPTIMAL), "a run stored before settings existed uses the optimal plan")
        assertEquals(105, vrView(null, 0L).plannedStepMin); assertEquals(165, vrView(null, 0L, plan).plannedStepMin, "an unstarted cycle shows the plan it would use")
    }

    @Test fun VR8_the_Red_Engine_or_a_remedial_time_of_zero_books_no_remedial() {
        assertTrue(remedialBooked(VrConfig()))
        assertFalse(remedialBooked(VrConfig(redEngine = true)))
        assertFalse(remedialBooked(VrConfig(remedialMin = 0)))
        assertEquals(listOf(10), remedialSessions(1, 10)); assertEquals(listOf(90, 90, 10), remedialSessions(19, 10))
    }
}
