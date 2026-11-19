package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** MC1: a meal is asked about, and the answer costs nothing. */
class MealCheckTest {
    private val mon = LocalDate.of(2026, 11, 16)
    private fun t(h: Int, m: Int = 0, d: LocalDate = mon) = at(d, LocalTime.of(h, m))
    private val slots = WeekB.slots                      // Monday: lunch 12:15 to 12:40, pre-football meal 17:10 to 17:40, post-football meal 22:25 to 22:50

    @Test fun MC1_a_meal_is_asked_about_ten_minutes_after_it_ends() {
        val next = nextMealCheck(slots, MealLog(), t(12, 0)); assertNotNull(next)
        assertEquals("Lunch", next.title); assertEquals(t(12, 50), next.at); assertEquals(mealKey(mon, 735), next.key)
        assertNull(dueMealCheck(slots, MealLog(), t(12, 49))); assertEquals("Lunch", dueMealCheck(slots, MealLog(), t(12, 50))?.title)
    }

    @Test fun MC1_ate_closes_it_and_the_next_meal_is_the_next_question() {
        val log = MealLog().answered(mealKey(mon, 735), MealAnswer.ATE, t(12, 55).toInstant().toEpochMilli())
        assertNull(dueMealCheck(slots, log, t(13, 0))); assertEquals("Pre-football meal", nextMealCheck(slots, log, t(13, 0))?.title)
    }

    @Test fun MC1_not_yet_asks_again_in_30_minutes_and_stops_after_three_asks() {
        var log = MealLog(); val key = mealKey(mon, 735)
        log = log.answered(key, MealAnswer.NOT_YET, t(12, 50).toInstant().toEpochMilli())
        assertNull(dueMealCheck(slots, log, t(13, 10))); assertEquals("Lunch", dueMealCheck(slots, log, t(13, 20))?.title)
        log = log.answered(key, MealAnswer.NOT_YET, t(13, 20).toInstant().toEpochMilli()); log = log.answered(key, MealAnswer.NOT_YET, t(13, 50).toInstant().toEpochMilli())
        assertEquals(3, log.entries.getValue(key).asks); assertNull(dueMealCheck(slots, log, t(14, 30)), "three asks is the most")
    }

    @Test fun MC1_a_question_nobody_answered_for_three_hours_is_dropped_and_not_carried_to_the_next_meal() {
        assertNull(dueMealCheck(slots, MealLog(), t(16, 0)).takeIf { it?.title == "Lunch" }, "lunch is not asked about at 16:00")
        assertEquals("Pre-football meal", dueMealCheck(slots, MealLog(), t(17, 55))?.title)
    }

    @Test fun MC1_the_bedtime_drink_is_not_a_meal_to_ask_about() {
        val tue = LocalDate.of(2026, 11, 17)
        assertTrue(mealChecks(slots, MealLog(), t(21, 0, tue), tue.let { it.atStartOfDay(java.time.ZoneId.of("Europe/London")).zone }).none { it.title.startsWith("Sleepy milk") })
    }

    @Test fun MC1_the_log_survives_being_stored_and_forgets_what_is_over_a_week_old() {
        val nowMs = t(12, 0).toInstant().toEpochMilli()
        val log = MealLog().answered("a", MealAnswer.ATE, nowMs).answered("b", MealAnswer.NOT_YET, nowMs - 8L * 24 * 3_600_000)
        val back = MealLog.decode(log.encode(nowMs))
        assertEquals(setOf("a"), back.entries.keys); assertEquals(MealAnswer.ATE, back.entries.getValue("a").answer)
        assertTrue(MealLog.decode(null).entries.isEmpty() && MealLog.decode("garbage").entries.isEmpty())
    }

    @Test fun MC1_a_meal_that_moves_is_a_new_question_and_a_day_off_still_has_its_meals() {
        val moved = slots + PatternSlot(mon.dayOfWeek, 800, 825, "Lunch (chicken-strip wrap)", "meal", "", false, "", date = mon)
        assertTrue(mealChecks(moved, MealLog(), t(12, 0)).any { it.key == mealKey(mon, 800) })
        assertNotNull(nextMealCheck(slots + dayOffMarker(mon), MealLog(), t(12, 0)))
    }
}
