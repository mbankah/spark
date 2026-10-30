package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.efa.data.*
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

private fun t(m: Int, d: Int, h: Int = 9, min: Int = 0) = at(LocalDate.of(2026, m, d), LocalTime.of(h, min))

/** Planned days off on a real database: C1, C4, C5, H1 to H6, X3. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class DaysOffStorageTest {
    private lateinit var db: EfaDb
    private lateinit var plan: PlanRepo
    private val firstUse = t(10, 21).toInstant().toEpochMilli()
    private val now = t(11, 2)
    private val tuesday = LocalDate.of(2026, 11, 3)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
        plan = PlanRepo(db)
        runBlocking { db.seedIfEmpty(firstUse, LONDON.id) }
    }
    @After fun close() = db.close()

    @Test fun C5_a_day_off_removes_the_days_work_and_keeps_sleep_meals_and_the_rest() = runBlocking {
        val before = slotsFor(plan.slots(), tuesday)
        assertTrue(before.any { it.slot.type in DAY_OFF_REMOVES })
        plan.addDaysOff(db, listOf(tuesday), "planned", 100, now, sameDay = false)
        val after = slotsFor(plan.slots(), tuesday)
        assertTrue(after.none { it.slot.type in DAY_OFF_REMOVES || it.slot.type == DAY_OFF_TYPE })
        assertTrue(after.any { it.slot.type == "sleep" || it.slot.type == "meal" || it.slot.type == "free" })
        assertTrue(isDayOff(plan.slots(), tuesday))
    }

    @Test fun C4_the_days_lectures_and_cycles_come_back_as_work_due_after_the_day_and_never_twice() = runBlocking {
        plan.addDaysOff(db, listOf(tuesday), "planned", 100, now, sameDay = false)
        val back = db.tasks().open().filter { it.source == "dayoff" }
        assertTrue(back.isNotEmpty())
        assertTrue(back.all { it.dueMs > tuesday.plusDays(1).atStartOfDay(LONDON).toInstant().toEpochMilli() })
        assertEquals(0, plan.addDaysOff(db, listOf(tuesday), "planned", 100, now, sameDay = false))      // already a day off: nothing is added again
        assertEquals(back.size, db.tasks().open().count { it.source == "dayoff" })
    }

    @Test fun H5_a_weekend_is_one_use_and_the_next_wall_is_longer_by_one_use() = runBlocking {
        val sat = LocalDate.of(2026, 11, 7)
        plan.addDaysOff(db, listOf(sat, sat.plusDays(1)), "weekend", 100, now, sameDay = false)
        assertEquals(2, db.daysOff().all().size)
        assertEquals(1, usesInLast30Days(plan.uses(db), now))
        assertEquals(WallLength.dayOff(1, false), dayOffWall(plan.uses(db), now, false))
        assertTrue(dayOffWall(plan.uses(db), now, false) > WallLength.dayOff(0, false))
    }

    @Test fun H3_X3_a_day_asked_for_the_same_day_and_an_illness_cost_the_extra_forty() {
        assertEquals(WallLength.dayOff(0, false) + 40, dayOffWall(emptyList(), now, true))
    }

    private suspend fun night(sleepMin: Int, gaps: Int) = db.nights().upsert(NightSummaryRow(date = "2026-11-01", startMs = now.minusHours(8).toInstant().toEpochMilli(),
        endMs = now.minusHours(1).toInstant().toEpochMilli(), sleepMin = sleepMin, checkCount = 0, checkMin = 0, chargingPct = 100, motionEvents = 0, darkPct = 100, gapCount = gaps, derivedAtMs = 1))

    @Test fun H4_three_hours_of_sleep_or_less_makes_today_a_day_off_with_no_typing_and_it_counts() = runBlocking {
        night(150, 0); plan.autoDayOff(now)
        val row = db.daysOff().get(now.toLocalDate().toString())
        assertNotNull(row); assertEquals("auto_sleep", row.source)
        assertEquals(1, usesInLast30Days(plan.uses(db), now))
        plan.autoDayOff(now); assertEquals(1, db.daysOff().all().size)                                 // a second look adds nothing
    }

    @Test fun H4_a_gap_in_the_logger_or_a_longer_night_does_not_grant_a_day_off() = runBlocking {
        night(150, 1); plan.autoDayOff(now); assertNull(db.daysOff().get(now.toLocalDate().toString()))
        night(240, 0); plan.autoDayOff(now); assertNull(db.daysOff().get(now.toLocalDate().toString()))
    }

    private suspend fun missedTask(date: String, dueMs: Long, doneAtMs: Long?) =
        db.tasks().insert(TaskRow(title = "Catch-up", dueMs = dueMs, dueZone = LONDON.id, heaviness = 4, hoursEstimate = 1.0, createdMs = 1, doneAtMs = doneAtMs, source = "missed", sourceKey = "missed:$date|x"))

    @Test fun H6_a_claim_waits_holds_when_the_work_is_done_by_its_due_time_and_fails_when_late() = runBlocking {
        val day = LocalDate.of(2026, 10, 28)
        plan.addDaysOff(db, listOf(day), "claim", 140, now, sameDay = true)
        assertEquals("pending", db.daysOff().get(day.toString())!!.state)
        missedTask(day.toString(), now.plusDays(2).toInstant().toEpochMilli(), null)
        plan.resolveClaims(now.toInstant().toEpochMilli()); assertEquals("pending", db.daysOff().get(day.toString())!!.state)
        db.tasks().close(db.tasks().open().single().id, now.toInstant().toEpochMilli())
        plan.resolveClaims(now.toInstant().toEpochMilli()); assertEquals("held", db.daysOff().get(day.toString())!!.state)
        assertTrue(isDayOff(plan.slots(), day))
    }

    @Test fun H6_a_claim_fails_when_the_work_is_overdue_and_then_the_day_is_not_a_day_off() = runBlocking {
        val day = LocalDate.of(2026, 10, 28)
        plan.addDaysOff(db, listOf(day), "claim", 140, now, sameDay = true)
        missedTask(day.toString(), now.minusHours(1).toInstant().toEpochMilli(), null)
        plan.resolveClaims(now.toInstant().toEpochMilli())
        assertEquals("failed", db.daysOff().get(day.toString())!!.state)
        assertFalse(isDayOff(plan.slots(), day))
        assertEquals(1, usesInLast30Days(plan.uses(db), now))                                          // the wall was spent either way
    }

    @Test fun H6_a_week_with_a_claim_waiting_is_not_frozen_until_the_claim_is_settled() = runBlocking {
        val rewards = RewardsRepo(db)
        val day = LocalDate.of(2026, 11, 3)
        val later = t(11, 16)
        plan.addDaysOff(db, listOf(day), "claim", 140, now, sameDay = true)
        missedTask(day.toString(), later.plusDays(2).toInstant().toEpochMilli(), null)
        rewards.closeWeeks(plan.slots(), firstUse, later)
        val closed = db.weekMoney().all().map { it.monday }
        assertTrue("2026-10-26" in closed); assertFalse("2026-11-02" in closed)
        db.daysOff().setState(db.daysOff().get(day.toString())!!.id, "held")
        rewards.closeWeeks(plan.slots(), firstUse, later)
        assertTrue("2026-11-02" in db.weekMoney().all().map { it.monday })
    }

    @Test fun G2_no_alarm_is_planned_for_a_day_off() = runBlocking {
        val next = planAlarm(t(11, 2, 22), null, plan.slots())
        assertNotNull(next)
        plan.addDaysOff(db, listOf(next.at.toLocalDate()), "planned", 100, t(11, 2, 22), sameDay = false)
        assertNull(planAlarmWithDaysOff(t(11, 2, 22), null, plan.slots()))
    }
}

@RunWith(AndroidJUnit4::class)
class DaysOffMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_10_to_11_keeps_data_and_adds_the_day_off_table_with_one_row_per_date() {
        helper.createDatabase("mig1011.db", 10).apply {
            execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('Essay',1,'Europe/London',5,6.25,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig1011.db", 11, true, MIGRATION_10_11)
        db.query("SELECT COUNT(*) FROM task").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO day_off (date,groupId,usedAtMs,sameDay,source,wallLength,state) VALUES ('2026-11-03',1,1,0,'planned',100,'active')")
        db.execSQL("INSERT OR IGNORE INTO day_off (date,groupId,usedAtMs,sameDay,source,wallLength,state) VALUES ('2026-11-03',2,2,0,'planned',100,'active')")
        db.query("SELECT COUNT(*) FROM day_off").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
}

/** The days off screen. */
@RunWith(AndroidJUnit4::class)
class DaysOffScreenTest {
    @get:Rule val rule = createComposeRule()
    private val now = t(11, 2)
    private val today = now.toLocalDate()

    private fun ui(preview: DayOffPreview?, claimable: List<LocalDate> = emptyList(), selected: List<LocalDate> = emptyList()) =
        DayOffUi(today, selected, preview, 0, claimable, listOf("Tue 3 Nov: planned"))

    private fun preview(dates: List<LocalDate>) = dayOffPreview(WeekB.slots, emptyList(), now, dates, emptyList(), sameDay = false)

    @Test fun C4_H2_the_screen_shows_the_cost_before_anything_is_typed_and_the_length_of_the_string() {
        val tue = today.plusDays(1); val p = preview(listOf(tue))
        rule.setContent { EfaTheme { DayOffScreen(ui(p, selected = listOf(tue)), {}, {}, {}, {}, {}) } }
        rule.onNodeWithText(p.lines.first()).assertExists()
        rule.onNodeWithText("Plan it: type ${p.wall} characters").performScrollTo().assertExists()
    }

    @Test fun H1_picking_a_day_and_planning_it_report_back_to_the_caller() {
        var picked: List<LocalDate>? = null; var planned = false
        val tue = today.plusDays(1); val p = preview(listOf(tue))
        rule.setContent { EfaTheme { DayOffScreen(ui(p, selected = listOf(tue)), { picked = it }, {}, {}, { planned = true }, {}) } }
        rule.onNodeWithContentDescription("Day off Tomorrow").performClick(); assertEquals(listOf(tue), picked)
        rule.onNodeWithText("Plan it: type ${p.wall} characters").performScrollTo().performClick(); assertTrue(planned)
    }

    @Test fun H5_a_saturday_offers_the_whole_weekend_as_one_use() {
        var picked: List<LocalDate>? = null
        rule.setContent { EfaTheme { DayOffScreen(ui(null), { picked = it }, {}, {}, {}, {}) } }
        val sat = (0..13).map { today.plusDays(it.toLong()) }.first { it.dayOfWeek == java.time.DayOfWeek.SATURDAY }
        rule.onNodeWithContentDescription("Weekend off from ${java.time.format.DateTimeFormatter.ofPattern("EEE d MMM").format(sat)}, one use").performScrollTo().performClick()
        assertEquals(listOf(sat, sat.plusDays(1)), picked)
    }

    @Test fun X3_the_ill_button_asks_for_today() {
        var ill = false
        rule.setContent { EfaTheme { DayOffScreen(ui(null), {}, { ill = true }, {}, {}, {}) } }
        rule.onNodeWithContentDescription("I am ill today. Today becomes a day off and costs the same string with the 40 extra.").performScrollTo().performClick(); assertTrue(ill)
    }

    @Test fun H6_a_day_that_has_passed_can_be_claimed_from_the_screen() {
        var claimed: LocalDate? = null
        val d = today.minusDays(2)
        rule.setContent { EfaTheme { DayOffScreen(ui(null, claimable = listOf(d)), {}, {}, { claimed = it }, {}, {}) } }
        rule.onNodeWithText("FORGOT TO ASK?").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Claim a day off for ${java.time.format.DateTimeFormatter.ofPattern("EEE d MMM").format(d)}").performScrollTo().performClick()
        assertEquals(d, claimed)
    }
}
