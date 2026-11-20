package efa

import kotlin.test.*

/** CF1 to CF5, VR15, SR1. */
class ConfirmTest {
    private val c = Confirm("k:1", "kind", "Title\twith tab", "Body\nline two \\ slash", listOf(ConfirmOption("a", "Yes"), ConfirmOption("b", "Open it", true)), 1_000L)

    @Test fun CF1_a_question_survives_being_stored_with_tabs_newlines_and_slashes() {
        assertEquals(c, Confirm.decode(c.encode()))
        val a = c.copy(answeredMs = 5L, answer = "a")
        assertEquals(a, Confirm.decode(a.encode()))
        assertNull(Confirm.decode(""))
        assertNull(Confirm.decode("junk"))
    }

    @Test fun CF2_a_question_is_raised_once_and_never_again_even_when_answered() {
        assertEquals(c, confirmRaise(null, c))
        assertNull(confirmRaise(c, c))
        assertNull(confirmRaise(c.copy(answeredMs = 9L, answer = "a"), c))
        assertEquals(3, confirmRaise(null, c.copy(options = List(5) { ConfirmOption("o$it", "O$it") }))!!.options.size, "at most three options")
    }

    @Test fun CF3_an_answer_must_be_one_of_the_options_and_only_counts_once() {
        val a = confirmAnswer(c, "b", 7L)!!
        assertEquals("b", a.answer); assertEquals(7L, a.answeredMs); assertFalse(a.open)
        assertNull(confirmAnswer(c, "zzz", 7L))
        assertNull(confirmAnswer(a, "a", 8L), "an answered question cannot be answered again")
    }

    @Test fun CF4_a_stale_question_is_withdrawn_without_an_answer_and_an_answered_one_is_left_alone() {
        val w = confirmWithdraw(c, 4L)
        assertEquals(CONFIRM_WITHDRAWN, w.answer); assertFalse(w.open)
        val a = confirmAnswer(c, "a", 3L)!!
        assertEquals(a, confirmWithdraw(a, 4L))
    }

    @Test fun CF5_open_questions_are_oldest_first_and_answered_ones_are_not_listed() {
        val x = c.copy(id = "x", raisedMs = 30L); val y = c.copy(id = "y", raisedMs = 10L); val z = c.copy(id = "z", raisedMs = 20L, answeredMs = 25L, answer = "a")
        assertEquals(listOf("y", "x"), confirmOpen(listOf(x, y, z)).map { it.id })
    }

    // ---- VR15 ----
    private val start = 10_000_000L
    private fun running(): VrRun = vrStart(vrPlan(VrConfig.OPTIMAL), start)

    @Test fun VR15_a_cycle_is_over_its_block_only_while_it_runs_and_the_block_has_ended() {
        val r = running(); val end = start + 60 * 60_000L
        assertFalse(vrOverBlockEnd(r, end, end - 1))
        assertTrue(vrOverBlockEnd(r, end, end))
        assertFalse(vrOverBlockEnd(null, end, end + 1))
        assertFalse(vrOverBlockEnd(vrStart(vrPlan(VrConfig.OPTIMAL), end + 5_000L), end, end + 60_000L), "started after the block ended: chosen, not asked about")
        assertFalse(vrOverBlockEnd(vrStop(r, start + 1_000), end, end + 1), "a stopped cycle is not asked about")
    }

    @Test fun VR15_stopping_ends_the_cycle_now_and_does_nothing_to_one_that_is_not_running() {
        val r = running(); val at = start + 20 * 60_000L
        val s = vrStop(r, at)
        assertEquals(VrStatus.DONE, vrView(s, at).status)
        assertEquals(vrPhases(r.planMinutes).size + 1, s.boundaries.size)
        assertEquals(s, vrStop(s, at + 1), "already done")
        val paused = vrPause(r, start + 60_000L)
        assertFalse(vrStop(paused, at).paused, "an open pause is closed")
        val none = VrRun()
        assertEquals(none, vrStop(none, at))
    }

    @Test fun VR15_the_minutes_left_are_the_rest_of_the_plan_rounded_up_to_5() {
        val r = running()
        assertEquals(130, vrRemainingMin(r, start))
        assertEquals(130, vrRemainingMin(r, start + 30_000L))
        assertEquals(0, vrRemainingMin(vrStop(r, start + 1), start + 2))
        assertEquals(0, vrRemainingMin(VrRun(), start))
    }

    @Test fun VR15_the_block_end_question_has_the_three_options_and_one_id_for_one_block() {
        val q = vrBlockEndConfirm("2026-11-20|600|Study - Block", "Study - Block", "12:15", running(), start)
        assertEquals("vr.blockend:2026-11-20|600|Study - Block", q.id)
        assertEquals(listOf(VR_BLOCKEND_STOP, VR_BLOCKEND_RUN_ON, VR_BLOCKEND_MOVE), q.options.map { it.id })
        assertTrue(q.options.single { it.id == VR_BLOCKEND_MOVE }.opensApp)
        assertTrue(q.body.contains("12:15") && q.body.contains("about 130 min"), q.body)
    }

    // ---- SR1 ----
    @Test fun SR1_a_held_start_earns_two_minutes_and_at_most_five_starts_a_day_are_paid() {
        val day = 1_000_000L
        val got = startEarnings(listOf(day + 50, day + 10, day - 5, day + 30, day + 20, day + 40, day + 60, day + 70), day)
        assertEquals(5, got.size)
        assertTrue(got.all { it.minutes == START_REWARD_MIN })
        assertEquals(listOf(day + 10, day + 20, day + 30, day + 40, day + 50), got.map { it.atMs })
        assertTrue(got.sumOf { it.minutes } <= 10, "a start with no work behind it buys at most 10 minutes a day")
        assertEquals(emptyList(), startEarnings(emptyList(), day))
    }
}
