package efa

import kotlin.test.*

/** VR10: V and R done by override, and the 2.5 hour session. */
class VrOverrideTest {
    private val min = 60_000L

    @Test fun VR10_the_session_is_two_and_a_half_hours_of_N_P_and_M_with_V_and_R_by_override() {
        val c = VrConfig.SESSION
        assertEquals("Session", c.preset()); assertTrue(c.skipVR)
        val plan = vrPlan(c)
        assertEquals(listOf(0, 0, 0, 0, 65, 10, 45, 5, 25), plan)
        assertEquals(150, plan.sum(), "2.5 hours with the breaks"); assertEquals(135, vrView(null, 0L, plan).plannedStepMin)
        assertEquals("Ideal", VrConfig.IDEAL.preset(), "the ideal preset is unchanged"); assertEquals(VrConfig.IDEAL.copy(skipVR = false), VrConfig.IDEAL)
    }

    @Test fun VR10_a_cycle_started_with_the_override_begins_at_N_and_shows_V_and_R_as_overridden() {
        val run = vrStart(vrPlan(VrConfig.SESSION), 1_000L, overridden = "VR")
        val v = vrView(run, 1_000L + 5 * min)
        assertEquals(VrStatus.RUNNING, v.status); assertEquals("N Notes", v.title); assertEquals(60 * min, v.remainingMs)
        assertEquals(listOf(VrChipState.OVERRIDDEN, VrChipState.OVERRIDDEN, VrChipState.CURRENT, VrChipState.UPCOMING, VrChipState.UPCOMING), v.chips.map { it.state })
        val unstarted = vrView(null, 0L, vrPlan(VrConfig.SESSION))
        assertEquals(VrChipState.OVERRIDDEN, unstarted.chips[0].state, "the override is visible before the cycle starts too")
    }

    @Test fun VR10_the_override_is_kept_and_said_when_the_cycle_is_done() {
        var run = vrStart(vrPlan(VrConfig.SESSION), 0L, overridden = "VR"); var t = 0L
        for (p in vrPhases(run.planMinutes).drop(4)) { t += p.plannedMin * min; run = vrAdvance(run, t) }
        val v = vrView(run, t)
        assertEquals(VrStatus.DONE, v.status); assertEquals(150, v.actualMin); assertTrue(v.summary.contains("VR done by override"), v.summary)
        assertEquals(VrChipState.OVERRIDDEN, v.chips[0].state); assertEquals(VrChipState.DONE, v.chips[4].state)
        assertEquals(run, VrRun.decode(run.encode())); assertEquals("VR", VrRun.decode(run.encode())!!.overridden)
    }

    @Test fun VR10_the_setting_is_stored_and_a_setting_saved_before_it_existed_still_reads() {
        val c = VrConfig.SESSION.copy(capMin = 0)
        assertEquals(c, VrConfig.decode(c.encode()))
        assertEquals(VrConfig.IDEAL, VrConfig.decode(VrConfig.IDEAL.encode().substringBeforeLast(",")), "eleven fields, as saved before: the override is off")
        assertFalse(VrConfig.decode("10,5,45,30,15,5,10,0,4,30,0").skipVR)
    }

    @Test fun VR10_a_run_stored_before_the_override_existed_still_reads() {
        assertEquals(VrRun(45, listOf(1L), emptyList(), null, emptyList(), ""), VrRun.decode("45|1|||"))
        assertEquals(VrRun(45, listOf(1L), emptyList(), null, listOf(5L), ""), VrRun.decode("45|1|||5"))
    }

    @Test fun VR10_the_cap_and_the_override_work_together() {
        val plan = vrPlan(VrConfig.SESSION.copy(capMin = 100))
        assertEquals(0, plan[0]); assertEquals(0, plan[2])
        assertTrue(listOf(plan[4], plan[6], plan[8]).sum() <= 100 + 5, plan.toString())
    }
}
