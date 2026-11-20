package efa

import kotlin.test.*

/** VR13: what to do in each step, and the reminder when a step ends or runs over. */
class VrReminderTest {
    private val min = 60_000L

    @Test fun VR13_every_step_and_a_break_says_what_to_do() {
        for (s in VrStep.entries) assertTrue(s.todo.length > 10, s.name)
        assertTrue(VrStep.M.todo.contains("C or U") && VrStep.M.todo.contains("concept, method or arithmetic"), "marking is the spec's own instruction")
        assertTrue(vrTodo(VrPhase(null, 5)).startsWith("Break"))
        val v = vrView(vrStart(45, 0L), 3 * min); assertEquals(VrStep.V.todo, v.todo, "the card shows what to do in the step in hand")
    }

    @Test fun VR13_when_a_step_ends_the_reminder_says_what_is_next_and_what_to_do_in_it() {
        val a = vrAlert(vrStart(45, 0L), 10 * min + 5_000L)!!
        assertEquals("V Video is up", a.title); assertEquals("Next: Break, 5 min. Break: stand up, drink water, phone away.", a.text)
        var run = vrAdvance(vrStart(45, 0L), 10 * min)
        val b = vrAlert(run, 15 * min + 5_000L)!!
        assertEquals("Break is up", b.title); assertTrue(b.text.startsWith("Next: R Read, 5 min. Read the section"), b.text)
    }

    @Test fun VR13_a_step_that_runs_over_is_reminded_with_how_far_over() {
        val a = vrAlert(vrStart(45, 0L), 10 * min + 5 * min + 10_000L)!!
        assertEquals("V Video is over by 5 min", a.title)
    }

    @Test fun VR13_the_last_step_says_to_finish_and_take_the_proof_photo() {
        var run = vrStart(45, 0L); var t = 0L
        for (p in vrPhases(45).take(8)) { t += p.plannedMin * min; run = vrAdvance(run, t) }
        val a = vrAlert(run, t + 15 * min + 1_000L)!!
        assertEquals("M Marking is up", a.title); assertTrue(a.text.contains("last step") && a.text.contains("proof photo"), a.text)
    }

    @Test fun VR13_a_step_done_by_override_is_skipped_when_saying_what_is_next() {
        val run = vrStart(vrPlan(VrConfig.SESSION), 0L, overridden = "VR")
        val a = vrAlert(run, 65 * min + 1_000L)!!
        assertEquals("N Notes is up", a.title); assertTrue(a.text.startsWith("Next: Break, 10 min"), a.text)
    }

    @Test fun VR13_no_reminder_when_paused_or_not_running() {
        assertNull(vrAlert(vrPause(vrStart(45, 0L), 3 * min), 30 * min)); assertNull(vrAlert(VrRun(), 5L))
    }

    @Test fun VR13_the_next_reminder_is_at_the_end_of_the_step_and_then_every_so_many_minutes_over() {
        assertEquals(6 * min, vrNextReminderMs(6 * min, 0L, 5), "at the end of the step")
        assertEquals(5 * min - 2 * min, vrNextReminderMs(0L, 2 * min, 5), "over by 2, the next is at 5 over")
        assertEquals(5 * min, vrNextReminderMs(0L, 5 * min, 5), "over by exactly 5: the one for 5 has just gone, the next is 5 later")
        assertNull(vrNextReminderMs(0L, 2 * min, 0), "0 is not again")
    }

    @Test fun VR13_the_reminder_settings_are_kept_and_an_older_setting_reads_with_the_defaults() {
        val c = VrConfig(remind = false, overEvery = 10)
        assertEquals(c, VrConfig.decode(c.encode()))
        val old = VrConfig.decode("10,5,45,30,15,5,10,0,4,30,0,0")
        assertTrue(old.remind); assertEquals(5, old.overEvery)
        assertEquals(15, VrConfig(overEvery = 99).clamped().overEvery)
    }

    @Test fun VR13_the_notification_carries_what_to_do() {
        assertEquals(VrStep.V.todo, vrNote(vrStart(45, 0L), 2 * min).todo)
    }
}
