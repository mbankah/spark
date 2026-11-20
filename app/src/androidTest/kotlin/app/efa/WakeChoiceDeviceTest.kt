package app.efa

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.*
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

/** CW1 on a real database: a chosen wake moves the Wake block of its day, and going back puts it where it was. */
@RunWith(AndroidJUnit4::class)
class WakeChoiceDeviceTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = EfaDb.get(ctx)

    private fun clean() { val w = db.openHelper.writableDatabase; w.execSQL("DELETE FROM modifier"); w.execSQL("DELETE FROM task WHERE source = 'modifier'"); w.execSQL("DELETE FROM day_off"); w.execSQL("DELETE FROM meta WHERE `key` = '$META_WAKE_CHOSEN'") }

    @Before fun before() { runBlocking { db.seedIfEmpty(System.currentTimeMillis(), LONDON.id) }; clean() }
    @After fun after() = clean()

    /** A Saturday a week or so ahead: no lectures to clash with, and not in the past. */
    private fun saturday(): LocalDate = generateSequence(ZonedDateTime.now(LONDON).toLocalDate().plusDays(3)) { it.plusDays(1) }.first { it.dayOfWeek == DayOfWeek.SATURDAY }

    private fun at(d: LocalDate, min: Int) = ZonedDateTime.of(d, LocalTime.of(min / 60, min % 60), LONDON)

    @Test fun CW1_a_later_wake_moves_the_Wake_block_and_going_back_puts_it_where_it_was() = runBlocking {
        val mods = ModifierRepo(db); val plan = PlanRepo(db); val sat = saturday(); val now = ZonedDateTime.now(LONDON)
        val before = plannedWakeMin(plan.slots(), sat)!!
        val chosenMin = before + 120
        val p = mods.wakeChoicePlan(at(sat, chosenMin), now)
        assertNotNull(p.inst, "a wake two hours later needs the morning to move"); assertTrue(p.problems.isEmpty(), p.problems.toString())
        mods.apply(p.inst!!, now, 0)
        val moved = plannedWakeMin(plan.slots(), sat)!!
        assertTrue(moved >= chosenMin, "the Wake block now starts at $moved, not before the chosen $chosenMin")
        assertEquals(1, mods.clearWakeChoices())
        assertEquals(before, plannedWakeMin(plan.slots(), sat), "back to the normal morning")
        assertEquals(0, mods.clearWakeChoices())
    }

    @Test fun CW1_a_wake_that_is_not_later_needs_nothing_and_choosing_again_replaces_the_old_choice() = runBlocking {
        val mods = ModifierRepo(db); val plan = PlanRepo(db); val sat = saturday(); val now = ZonedDateTime.now(LONDON)
        val before = plannedWakeMin(plan.slots(), sat)!!
        assertNull(mods.wakeChoicePlan(at(sat, before), now).inst)
        mods.apply(mods.wakeChoicePlan(at(sat, before + 90), now).inst!!, now, 0)
        mods.clearWakeChoices()                                          // what choosing again does first
        val again = mods.wakeChoicePlan(at(sat, before + 150), now)
        assertTrue(again.problems.isEmpty(), again.problems.toString())
        mods.apply(again.inst!!, now, 0)
        assertEquals(1, db.modifiers().all().count { it.state == "active" }, "one choice, not two stacked")
        assertTrue(plannedWakeMin(plan.slots(), sat)!! >= before + 150)
    }

    @Test fun CW1_a_Late_start_you_made_yourself_is_not_taken_away_by_going_back_to_the_normal_alarm() = runBlocking {
        val mods = ModifierRepo(db); val sat = saturday(); val now = ZonedDateTime.now(LONDON)
        val before = plannedWakeMin(PlanRepo(db).slots(), sat)!!
        val own = Instance(Preset.LATE_START, sat, sat, mapOf("wake" to "%02d:%02d".format((before + 90) / 60, (before + 90) % 60)))
        mods.apply(own, now, 0)
        assertEquals(0, mods.clearWakeChoices())
        assertEquals(1, db.modifiers().all().count { it.state == "active" })
    }
}
