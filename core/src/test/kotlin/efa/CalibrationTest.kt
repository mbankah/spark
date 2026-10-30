package efa

import kotlin.test.*

class CalibrationTest {
    private fun samples(h: Int, n: Int, actualOfEstimate: Double) = List(n) { CalSample(h, quickAddHours(h), quickAddHours(h) * actualOfEstimate) }

    @Test fun B5_with_no_finished_tasks_the_estimate_is_the_first_guess() {
        val c = Calibration()
        assertEquals(quickAddHours(5), c.hours(5), 1e-9)
        assertFalse(c.correction(5).applied)
    }

    @Test fun G6_E9_fewer_than_three_samples_leave_the_guess_alone_and_say_how_many_there_are() {
        val c = Calibration(samples(5, 2, 2.0))
        assertEquals(quickAddHours(5), c.hours(5), 1e-9)
        assertTrue(c.note(5).contains("2 of 3 finished"))
    }

    @Test fun B5_tasks_that_run_long_raise_the_estimate_for_their_band() {
        val c = Calibration(samples(5, 6, 1.5))
        assertTrue(c.hours(5) > quickAddHours(5))
        assertTrue(c.correction(5).applied); assertEquals(6, c.correction(5).samples)
    }

    @Test fun B5_tasks_that_run_short_lower_it() {
        val c = Calibration(samples(8, 6, 0.5))
        assertTrue(c.hours(8) < quickAddHours(8))
    }

    @Test fun R10_inflating_heaviness_gains_nothing_because_the_estimate_comes_back_to_the_real_time() {
        val real = 3.0
        val inflated = (1..12).map { CalSample(10, quickAddHours(10), real) }       // heaviness 10 asks for 12.5h and the work took 3h
        val c = Calibration(inflated)
        assertTrue(c.hours(10) < quickAddHours(10) * 0.7)
        assertTrue(c.hours(10) - real < quickAddHours(10) - real)                   // closer to the real time than the plain guess was
        val more = Calibration((1..60).map { CalSample(10, quickAddHours(10), real) })
        assertTrue(more.hours(10) < c.hours(10))                                    // and closer still with more evidence
    }

    @Test fun B5_the_correction_is_kept_apart_for_each_band() {
        val c = Calibration(samples(9, 6, 0.5))
        assertTrue(c.correction(8).applied)
        assertFalse(c.correction(2).applied); assertEquals(quickAddHours(2), c.hours(2), 1e-9)
    }

    @Test fun B5_one_wild_task_cannot_swing_the_estimate_and_the_factor_is_bounded() {
        val c = Calibration(samples(5, 3, 1.0) + CalSample(5, 6.25, 62.5))
        assertTrue(c.correction(5).factor <= CAL_MAX_FACTOR)
        val low = Calibration(samples(5, 5, 0.01))
        assertTrue(low.correction(5).factor >= CAL_MIN_FACTOR)
    }

    @Test fun B5_a_task_with_no_real_time_is_not_a_sample() {
        assertFalse(Calibration(List(5) { CalSample(5, 6.25, 0.0) }).correction(5).applied)
    }

    @Test fun B5_reports_make_the_samples_they_say_and_nothing_else_is_accepted() {
        assertEquals(CalSample(5, 6.25, 6.25), sampleFrom(5, 6.25, TookReport.AsPlanned))
        assertEquals(7.25, sampleFrom(5, 6.25, TookReport.MoreMinutes(60))!!.actualHours, 1e-9)
        assertEquals(3.125, sampleFrom(5, 6.25, TookReport.LessFraction(0.5))!!.actualHours, 1e-9)
        assertNull(sampleFrom(5, 6.25, TookReport.MoreMinutes(7)))
        assertNull(sampleFrom(5, 6.25, TookReport.LessFraction(0.1)))
    }

    @Test fun E9_the_note_names_the_correction_once_it_applies() {
        val c = Calibration(samples(5, 4, 1.2))
        assertTrue(c.note(5).contains("corrected x"))
        assertTrue(c.note(5).contains("from 4 finished medium (4 to 6) tasks"))
    }
}
