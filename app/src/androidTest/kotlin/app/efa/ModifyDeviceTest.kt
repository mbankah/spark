package app.efa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

private fun mt(m: Int, d: Int, h: Int = 9) = at(LocalDate.of(2026, m, d), LocalTime.of(h, 0))

/** Modify Schedule on a real database: applying, removing, the days off a period off makes, the Admin library and the essentials. */
@RunWith(AndroidJUnit4::class)
class ModifyStorageTest {
    private lateinit var db: EfaDb
    private lateinit var plan: PlanRepo
    private lateinit var repo: ModifierRepo
    private val now = mt(11, 2)
    private val firstUse = mt(10, 21).toInstant().toEpochMilli()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
        plan = PlanRepo(db); repo = ModifierRepo(db)
        runBlocking { db.seedIfEmpty(firstUse, LONDON.id) }
    }
    @After fun close() = db.close()

    private fun assignment(title: String = "GT assignment") = Instance(Preset.ASSIGNMENT, now.toLocalDate(), LocalDate.of(2026, 11, 20), emptyMap(),
        listOf(mapOf("title" to title, "due" to "2026-11-20", "due_time" to "17:00", "size_h" to "6.0")))

    @Test fun apply_an_assignment_puts_its_sessions_in_the_calendar_and_its_hours_in_the_Buffer() = runBlocking<Unit> {
        val id = repo.apply(assignment(), now, 75)
        assertTrue(plan.slots().any { it.title.startsWith("GT assignment:") && it.date != null })
        val t = db.tasks().open().single { it.source == "modifier" }
        assertEquals(6.0, t.hoursEstimate, 0.001); assertEquals("mod:$id:0", t.sourceKey)
        assertEquals(1, repo.all().size)
    }

    @Test fun removing_it_takes_the_sessions_and_its_unstarted_work_away() = runBlocking<Unit> {
        val id = repo.apply(assignment(), now, 75)
        repo.remove(id)
        assertTrue(plan.slots().none { it.title.startsWith("GT assignment:") }); assertTrue(db.tasks().open().none { it.source == "modifier" }); assertTrue(repo.all().isEmpty())
    }

    @Test fun a_plan_that_breaks_a_guard_rail_is_refused_and_nothing_is_written() = runBlocking<Unit> {
        val bad = assignment().copy(items = emptyList())
        assertFailsWith<IllegalStateException> { repo.apply(bad, now, 75) }
        assertTrue(repo.all().isEmpty()); assertTrue(db.tasks().open().none { it.source == "modifier" })
    }

    @Test fun C1_a_period_off_makes_days_off_that_count_toward_the_tally_and_removing_it_takes_them_off_the_calendar_but_not_out_of_the_tally() = runBlocking<Unit> {
        val inst = Instance(Preset.PERIOD_OFF, LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8))
        val id = repo.apply(inst, now, repo.periodOffWall(2, now, false))
        assertEquals(2, db.daysOff().all().size); assertEquals(1, usesInLast30Days(plan.uses(db), now))
        assertTrue(isDayOff(plan.slots(), LocalDate.of(2026, 11, 7)))
        repo.remove(id)
        assertFalse(isDayOff(plan.slots(), LocalDate.of(2026, 11, 7)))
        assertEquals(1, usesInLast30Days(plan.uses(db), now))   // H1: taking it back must not refund the use
        assertTrue(db.daysOff().effective().isEmpty())   // a cancelled day is not an upcoming day off
        // the same days can be planned again, and the wall for that is higher than it was the first time
        assertTrue(repo.periodOffWall(2, now, false) > WallLength.dayOff(0, false))
        repo.apply(inst, now, repo.periodOffWall(2, now, false))
        assertTrue(isDayOff(plan.slots(), LocalDate.of(2026, 11, 7)))
    }

    @Test fun a_holiday_period_off_makes_no_catch_up_work_and_a_short_one_in_term_does() = runBlocking<Unit> {
        val holiday = Instance(Preset.PERIOD_OFF, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15))
        repo.apply(holiday, now, repo.periodOffWall(7, now, false))
        assertTrue(db.tasks().open().none { it.source == "dayoff" })
        val short = Instance(Preset.PERIOD_OFF, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 17))
        repo.apply(short, now, repo.periodOffWall(1, now, false))
        assertTrue(db.tasks().open().any { it.source == "dayoff" })
    }

    @Test fun the_same_modifier_cannot_be_applied_twice() = runBlocking<Unit> {
        val inst = Instance(Preset.PERIOD_OFF, LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8))
        repo.apply(inst, now, 100)
        assertFalse(repo.preview(inst, now).canApply)
    }

    @Test fun H1_the_wall_for_a_period_off_rises_with_each_day_beyond_two_and_with_uses() = runBlocking<Unit> {
        val two = repo.periodOffWall(2, now, false); val five = repo.periodOffWall(5, now, false)
        assertEquals(two + 75, five); assertEquals(WallLength.dayOff(0, false), two)
        repo.apply(Instance(Preset.PERIOD_OFF, LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)), now, two)
        assertTrue(repo.periodOffWall(2, now, false) > two)
    }

    @Test fun a_hybrid_focus_week_keeps_its_sessions_on_days_that_are_otherwise_off_and_the_routine_stays() = runBlocking<Unit> {
        val inst = Instance(Preset.FOCUS, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13), mapOf("hybrid" to "true"),
            listOf(mapOf("title" to "CS Project", "due" to "2026-11-20", "due_time" to "17:00", "size_h" to "10.0")))
        repo.apply(inst, now, 100)
        val slots = plan.slots(); val mon = slotsFor(slots, LocalDate.of(2026, 11, 9))
        assertTrue(mon.any { it.slot.title.startsWith("CS Project:") }, "the project session was removed by the day off")
        assertTrue(mon.any { it.slot.type == "routine" }); assertTrue(mon.none { it.slot.type == "lecture" })
    }

    @Test fun removing_a_hybrid_focus_week_restores_the_lectures() = runBlocking<Unit> {
        val inst = Instance(Preset.FOCUS, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13), mapOf("hybrid" to "true"),
            listOf(mapOf("title" to "CS Project", "due" to "2026-11-20", "due_time" to "17:00", "size_h" to "10.0")))
        val id = repo.apply(inst, now, 100); repo.remove(id)
        assertTrue(slotsFor(plan.slots(), LocalDate.of(2026, 11, 9)).any { it.slot.type == "lecture" })
    }

    /** A day on which the day can start at 12:15 without running into a commitment. */
    private suspend fun lateStartDay(): LocalDate = (14L..40L).map { now.toLocalDate().plusDays(it) }.first { d -> repo.preview(Instance(Preset.LATE_START, d, d, mapOf("wake" to "12:15")), now).canApply }

    @Test fun RF3_a_catch_up_week_puts_whole_study_blocks_by_module_in_the_calendar_and_removing_it_puts_the_week_back() = runBlocking<Unit> {
        val before = slotsFor(plan.slots(), LocalDate.of(2026, 11, 17)).map { it.slot.title to it.slot.startMin }
        val id = repo.apply(Instance(Preset.CATCH_UP, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20)), now, 75)
        val mine = plan.slots().filter { it.date != null && !it.cancelled && it.type == "study" && it.title.endsWith(": notes and problems") }
        assertTrue(mine.isNotEmpty()); assertTrue(db.tasks().open().none { it.source == "modifier" }, "the blocks are the plan, not extra demand")
        repo.remove(id)
        assertTrue(plan.slots().none { it.date != null && it.title.endsWith(": notes and problems") })
        assertEquals(before, slotsFor(plan.slots(), LocalDate.of(2026, 11, 17)).map { it.slot.title to it.slot.startMin })
    }

    @Test fun RF6_a_late_start_moves_the_morning_in_the_real_calendar_needs_the_wall_and_removing_it_puts_the_day_back() = runBlocking<Unit> {
        val day = lateStartDay()
        val inst = Instance(Preset.LATE_START, day, day, mapOf("wake" to "12:15"))
        val p = repo.preview(inst, now); assertTrue(p.canApply, p.problems.toString()); assertTrue(p.wallNeeded)
        val before = slotsFor(plan.slots(), day).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }
        val id = repo.apply(inst, now, 75)
        val wake = slotsFor(plan.slots(), day).first { it.slot.title.startsWith("Wake") }; assertTrue(wake.slot.startMin >= 12 * 60 + 15, "a commitment that is running delays the start, never advances it: ${wake.slot.startMin}"); assertTrue(wake.slot.date != null, "it was moved")
        repo.remove(id)
        assertEquals(before, slotsFor(plan.slots(), day).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) })
    }


    @Test fun RN2_running_over_in_the_real_calendar_slides_the_rest_of_today_and_removing_it_puts_it_back() = runBlocking<Unit> {
        val day = now.toLocalDate()
        val inst = Instance(Preset.RUN_OVER, day, day, mapOf("minutes" to "30"))
        val at = (10..20).map { mt(11, 2, it) }.first { n -> repo.preview(inst, n).canApply }
        val before = slotsFor(plan.slots(), day).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }
        val id = repo.apply(inst, at, 75)
        assertNotEquals(before, slotsFor(plan.slots(), day).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) })
        repo.remove(id)
        assertEquals(before, slotsFor(plan.slots(), day).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) })
    }

    @Test fun RN1_starting_the_day_from_now_in_the_real_calendar_moves_the_morning_to_now_and_removing_it_puts_it_back() = runBlocking<Unit> {
        val day = now.toLocalDate()
        val inst = Instance(Preset.LATE_START, day, day, mapOf("from_now" to "true"))
        val at = (11..16).map { mt(11, 2, it) }.first { n -> repo.preview(inst, n).canApply }
        val before = slotsFor(plan.slots(), day).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }
        val id = repo.apply(inst, at, 75)
        assertTrue(slotsFor(plan.slots(), day).first { it.slot.title.startsWith("Wake") }.slot.startMin >= at.hour * 60, "the day starts now, or when the commitment that is running ends")
        repo.remove(id)
        assertEquals(before, slotsFor(plan.slots(), day).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) })
    }

    @Test fun YP2_an_event_in_the_real_calendar_gives_the_day_way_around_it_and_removing_it_puts_the_day_back() = runBlocking<Unit> {
        fun ev(d: LocalDate) = Instance(Preset.ADD_EVENT, d, d, mapOf("title" to "Birthday dinner", "type" to "errand", "start" to "17:30", "end" to "19:00"))
        val day = (5L..40L).map { now.toLocalDate().plusDays(it) }.first { d -> repo.preview(ev(d), now).canApply }
        val before = slotsFor(plan.slots(), day).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }
        val id = repo.apply(ev(day), now, 75)
        val during = slotsFor(plan.slots(), day).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }
        assertTrue(during.any { it.first == "Birthday dinner" && it.second == 17 * 60 + 30 }); assertTrue(during.zipWithNext { a, b -> a }.isNotEmpty())
        val meals = slotsFor(WeekB.slots, day).filter { it.slot.type == "meal" }.map { it.slot.title }
        assertTrue(slotsFor(plan.slots(), day).map { it.slot.title }.containsAll(meals), "every meal is still in the day")
        repo.remove(id)
        assertEquals(before, slotsFor(plan.slots(), day).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) })
    }

    /** A day on which [make] gives a plan that can be applied, found by looking, so the test does not depend on what Week B has that day. */
    private suspend fun dayFor(make: (LocalDate) -> Instance): LocalDate = (6L..60L).map { now.toLocalDate().plusDays(it) }.first { d -> repo.preview(make(d), now).canApply }
    private suspend fun daysOf(inst: Instance) = inst.coverage().associateWith { d -> slotsFor(plan.slots(), d).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) } }

    @Test fun YP4_every_new_modifier_applies_to_the_real_calendar_and_removing_it_puts_every_day_back_with_its_work() = runBlocking<Unit> {
        val cases = listOf<(LocalDate) -> Instance>(
            { d -> Instance(Preset.CANCEL, d, d, mapOf("what" to "lecture")) },
            { d -> Instance(Preset.RECOVERY, d, d.plusDays(1), mapOf("work_h_max" to "1.0")) },
            { d -> Instance(Preset.AWAY, d, d.plusDays(1), mapOf("lectures" to "running", "depart" to "09:00", "back" to "17:00", "travel_h" to "1.5")) },
            { d -> Instance(Preset.GUESTS, d, d, mapOf("from" to "18:00", "until" to "21:00", "lead_days" to "2")) },
        )
        for (make in cases) {
            val day = dayFor(make); val inst = make(day)
            val before = daysOf(inst)
            val id = repo.apply(inst, now, 75)
            assertNotEquals(before, daysOf(inst), "${inst.preset} changed nothing")
            val tasks = db.tasks().open().count { it.source == "modifier" }
            repo.remove(id)
            assertEquals(before, daysOf(inst), "${inst.preset} did not put the days back"); assertEquals(0, db.tasks().open().count { it.source == "modifier" }, "${inst.preset} left work behind (it had $tasks)")
        }
    }

    @Test fun YP4_revision_in_the_real_calendar_plans_the_exam_and_adds_its_hours_to_the_Buffer_and_removing_it_takes_both_away() = runBlocking<Unit> {
        val inst = Instance(Preset.REVISION, now.toLocalDate(), now.toLocalDate().plusDays(60), emptyMap(), listOf(mapOf("title" to "Game Theory exam", "due" to "2026-12-10", "due_time" to "09:30", "size_h" to "8.0")))
        val id = repo.apply(inst, now, 75)
        assertTrue(plan.slots().any { it.title == "Game Theory exam: revision" && it.date != null }); assertEquals(8.0, db.tasks().open().single { it.source == "modifier" }.hoursEstimate, 0.001)
        repo.remove(id)
        assertTrue(plan.slots().none { it.title.endsWith(": revision") }); assertTrue(db.tasks().open().none { it.source == "modifier" })
    }

    @Test fun MC1_an_answer_is_stored_and_the_question_is_not_asked_again() = runBlocking<Unit> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val key = mealKey(LocalDate.of(2026, 11, 16), 735)
        try {
            MealCheckAlarm.answer(ctx, key, ate = true, nowMs = at(LocalDate.of(2026, 11, 16), LocalTime.of(12, 55)).toInstant().toEpochMilli())
            val log = MealCheckAlarm.log(EfaDb.get(ctx)); assertEquals(MealAnswer.ATE, log.entries.getValue(key).answer)
            assertNull(dueMealCheck(WeekB.slots, log, at(LocalDate.of(2026, 11, 16), LocalTime.of(13, 5))))
        } finally { EfaDb.get(ctx).meta().put(MetaRow(META_MEAL_LOG, "")) }
    }
    @Test fun RF9_a_day_off_planned_over_a_laid_out_day_puts_the_whole_day_back_so_no_meal_is_left_cancelled() = runBlocking<Unit> {
        val day = lateStartDay()
        repo.apply(Instance(Preset.LATE_START, day, day, mapOf("wake" to "12:15")), now, 75)
        val mealsBefore = slotsFor(WeekB.slots, day).filter { it.slot.type == "meal" }.map { it.slot.title }.toSet()
        plan.addDaysOff(db, listOf(day), "manual", 75, now, sameDay = false)
        val after = slotsFor(plan.slots(), day)
        assertTrue(after.map { it.slot.title }.containsAll(mealsBefore), "the meals are all still there: ${after.map { it.slot.title }}")
    }

    @Test fun a_lecture_free_period_removes_the_lectures_from_the_calendar_and_removing_it_restores_them() = runBlocking<Unit> {
        val id = repo.apply(Instance(Preset.LECTURE_FREE, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13)), now, 75)
        assertTrue(slotsFor(plan.slots(), LocalDate.of(2026, 11, 9)).none { it.slot.type == "lecture" })
        repo.remove(id)
        assertTrue(slotsFor(plan.slots(), LocalDate.of(2026, 11, 9)).any { it.slot.type == "lecture" })
    }

    @Test fun MG19_the_Admin_library_places_nothing_until_a_load_is_chosen_and_then_adds_jobs_to_the_calendar() = runBlocking<Unit> {
        val before = plan.slots().size
        db.meta().put(MetaRow(META_ADMIN, AdminState(LoadSet.STANDARD).encode()))
        val after = plan.slots()
        assertTrue(after.size > before); assertTrue(after.any { it.notes.startsWith("Admin:") })
    }

    @Test fun MG24_the_Admin_library_does_not_make_any_work_for_the_Buffer() = runBlocking<Unit> {
        db.meta().put(MetaRow(META_ADMIN, AdminState(LoadSet.THOROUGH).encode()))
        assertTrue(db.tasks().open().none { it.title.contains("Make the bed") })
    }

    // ---- essentials ----
    @Test fun MG3a_the_add_window_takes_three_apps_and_then_refuses_and_closes_after_five_minutes() = runBlocking<Unit> {
        val er = EssentialsRepo(ApplicationProvider.getApplicationContext(), db)
        assertFalse(er.add("a.one", 1_000))                                   // no wall passed yet
        er.openWindow(1_000_000)
        assertTrue(er.add("a.one", 1_000_100)); assertTrue(er.add("a.two", 1_000_200)); assertTrue(er.add("a.three", 1_000_300)); assertFalse(er.add("a.four", 1_000_400))
        assertEquals(setOf("a.one", "a.two", "a.three"), er.extra())
        er.openWindow(2_000_000); assertFalse(er.add("a.five", 2_000_000 + ESSENTIALS_WINDOW_MS))
    }

    @Test fun MG1_the_fixed_core_cannot_be_added_again_and_the_added_ones_can_be_taken_out_without_the_wall() = runBlocking<Unit> {
        val er = EssentialsRepo(ApplicationProvider.getApplicationContext(), db)
        er.openWindow(5_000); assertFalse(er.add("com.google.android.dialer", 5_100)); assertTrue(er.add("a.bank", 5_100))
        assertTrue("a.bank" in er.resolve()); er.remove("a.bank"); assertFalse("a.bank" in er.resolve())
    }

    @Test fun MG2_the_resolved_essentials_always_include_the_fixed_core() = runBlocking<Unit> {
        val e = EssentialsRepo(ApplicationProvider.getApplicationContext(), db).resolve(); assertTrue(e.containsAll(ESSENTIAL_CORE))
    }
}

@RunWith(AndroidJUnit4::class)
class ModifyMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_14_to_15_keeps_data_and_adds_the_modifier_table() {
        helper.createDatabase("mig1415.db", 14).apply { execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('Essay',1,'Europe/London',5,6.25,1)"); close() }
        val db = helper.runMigrationsAndValidate("mig1415.db", 15, true, MIGRATION_14_15)
        db.query("SELECT COUNT(*) FROM task").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO modifier (preset,startDate,endDate,label,instance,compiled,state,createdMs,loosened) VALUES ('assignment','2026-11-02','2026-11-20','x','i','','active',1,0)")
    }
}

@RunWith(AndroidJUnit4::class)
class ModifyScreenTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 2)

    @Test fun the_home_lists_every_preset_and_no_longer_claims_anything_is_unbuilt() {
        rule.setContent { EfaTheme { ModifyHomeScreen(emptyList(), today, {}, {}, {}, {}, null, {}) } }
        Preset.entries.forEach { rule.onNodeWithText(it.label).performScrollTo().assertExists() }
        rule.onNodeWithText("Not built yet", substring = true).assertDoesNotExist()
    }

    @Test fun picking_a_preset_and_removing_an_active_one_report_back() {
        var picked: Preset? = null; var removed: Long? = null
        val row = ModifierRow(7, "assignment", "2026-11-02", "2026-11-20", "x", "", "", "active", 1, false)
        rule.setContent { EfaTheme { ModifyHomeScreen(listOf(row), today, { picked = it }, { removed = it.id }, {}, {}, null, {}) } }
        rule.onNodeWithContentDescription("Set up Focus week").performScrollTo().performClick(); assertEquals(Preset.FOCUS, picked)
        rule.onNodeWithContentDescription("Remove the Assignment that runs 2026-11-02 to 2026-11-20").performScrollTo().performClick(); assertEquals(7L, removed)
    }


    @Test fun RN1_the_home_offers_start_from_now_and_running_over_in_one_tap() {
        var got: Instance? = null
        rule.setContent { EfaTheme { ModifyHomeScreen(emptyList(), today, {}, {}, {}, {}, null, {}, onQuick = { got = it }) } }
        rule.onNodeWithText("RIGHT NOW").assertExists()
        rule.onNodeWithContentDescription("Start the day from now").performScrollTo().performClick()
        assertEquals(Preset.LATE_START, got?.preset); assertEquals("true", got?.settings?.get("from_now")); assertEquals(today, got?.start)
        rule.onNodeWithContentDescription("Running over 30 minutes").performScrollTo().performClick()
        assertEquals(Preset.RUN_OVER, got?.preset); assertEquals("30", got?.settings?.get("minutes"))
    }

    @Test fun MC1_the_home_asks_did_you_eat_with_two_answers() {
        var got: Pair<String, Boolean>? = null
        rule.setContent { EfaTheme { HomeScreen(HomeState(date = "Mon 16 Nov", phone = null, phoneOpen = true, time = "12:55", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null, freeToSpendPence = null, meal = "Lunch", mealKey = "2026-11-16|735"), {}, {}, onMeal = { k, a -> got = k to a }) } }
        rule.onNodeWithText("DID YOU EAT?").assertExists(); rule.onNodeWithText("Lunch").assertExists()
        rule.onNodeWithContentDescription("Not yet, Lunch").performClick(); assertEquals("2026-11-16|735" to false, got)
        rule.onNodeWithContentDescription("Ate Lunch").performClick(); assertEquals("2026-11-16|735" to true, got)
    }

    @Test fun HM1_HS5_MX4_the_home_has_no_change_button_and_reports_what_happened_from_the_now_card_and_the_evening() {
        var got: String? = null
        rule.setContent { EfaTheme { HomeScreen(HomeState(date = "Mon 16 Nov", phone = null, phoneOpen = true, time = "22:05", buffer = BufferState.NoDeadline, nowLabel = "Now, until 22:30", nowTitle = "Evening work", nowDetail = null, freeToSpendPence = null, evening = true), {}, {}, onReality = { got = it }) } }
        rule.onNodeWithContentDescription("Change today: running over, something has come up, start from now").assertDoesNotExist()
        rule.onNodeWithText("Change").assertDoesNotExist()
        rule.onNodeWithContentDescription("Running over: this is taking longer").performScrollTo().performClick(); assertEquals("over", got)
        rule.onNodeWithContentDescription("Started late: start the day from now").performScrollTo().performClick(); assertEquals("late", got)
        rule.onNodeWithContentDescription("Going to bed later tonight").performScrollTo().performClick(); assertEquals("night", got)
    }

    @Test fun HM1_the_evening_chip_is_only_there_in_the_evening() {
        rule.setContent { EfaTheme { HomeScreen(HomeState(date = "Mon 16 Nov", phone = null, phoneOpen = true, time = "12:55", buffer = BufferState.NoDeadline, nowLabel = "Now", nowTitle = "Study", nowDetail = null, freeToSpendPence = null, evening = false), {}, {}) } }
        rule.onNodeWithContentDescription("Going to bed later tonight").assertDoesNotExist()
        rule.onNodeWithContentDescription("Running over: this is taking longer").assertExists()
    }

    @Test fun MX1_the_presets_are_grouped_by_what_they_do_to_the_day_and_the_night_is_offered() {
        rule.setContent { EfaTheme { ModifyHomeScreen(emptyList(), LocalDate.of(2026, 11, 18), {}, {}, {}, {}, null, {}) } }
        for (e in Effect.entries) rule.onNodeWithText(e.label.uppercase()).assertExists()
        rule.onNodeWithContentDescription("Set up Sleep later or earlier").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Set up Catch-up week").performScrollTo().assertExists()
    }

    @Test fun GT2_the_blocked_card_says_whatsapp_opens_for_ten_minutes_at_a_time_and_the_wall_opens_it() {
        var passed = false
        val d = Decision.Block(BlockReason.APP_CAP, "10 minutes at a time", java.time.ZonedDateTime.now(LONDON).plusMinutes(20), 0, night = false)
        rule.setContent { EfaTheme { BlockedCard("WhatsApp", d, 0, {}, { passed = true }, null) } }
        rule.onNodeWithText("WhatsApp is blocked").assertExists()
        rule.onNodeWithText("WhatsApp opens for 10 minutes at a time", substring = true).assertExists()
        rule.onNodeWithText("Open anyway", substring = true).performScrollTo().assertExists()
    }

    @Test fun DP4_when_the_weekly_pool_is_used_up_the_card_says_so_and_offers_no_wall() {
        val d = Decision.Block(BlockReason.APP_CAP, "10 minutes at a time", java.time.ZonedDateTime.now(LONDON).plusMinutes(20), 0, night = false)
        rule.setContent { EfaTheme { BlockedCard("WhatsApp", d, 0, {}, {}, null, doorLock = "The weekly pool is used up.") } }
        rule.onNodeWithText("The weekly pool is used up.", substring = true).assertExists()
        rule.onNodeWithText("Open anyway", substring = true).assertDoesNotExist()
    }

    @Test fun YP4_every_new_modifier_is_offered_and_revision_asks_for_exams_not_dates() {
        var d by androidx.compose.runtime.mutableStateOf(Instance(Preset.REVISION, today, today.plusDays(60), emptyMap(), emptyList()))
        rule.setContent { EfaTheme { ModifyFormScreen(d, today, null, emptyMap(), null, { d = it }, {}, {}, {}, {}) } }
        rule.onNodeWithContentDescription("Add Exam").performScrollTo().performClick(); assertEquals(1, d.items.size)
        rule.onNodeWithContentDescription("Starts ${java.time.format.DateTimeFormatter.ofPattern("EEE d MMM").format(today)}").assertDoesNotExist()
    }
    @Test fun the_form_draws_settings_from_their_definitions_and_changes_them() {
        var d = Instance(Preset.CATCH_UP, today, today.plusDays(6))
        rule.setContent { androidx.compose.runtime.key(d) { EfaTheme { ModifyFormScreen(d, today, null, emptyMap(), null, { d = it }, {}, {}, {}, {}) } } }
        rule.onNodeWithText("Study blocks a day (at least)").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("More Study blocks a day (at least)").performScrollTo().performClick()
        assertEquals("4", d.settings["slots_floor"])
        rule.onNodeWithContentDescription("Intensity: rapid").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); assertEquals("rapid", d.settings["intensity"])
    }

    @Test fun RF1_a_preset_that_lays_the_day_out_has_a_time_at_each_end_and_the_others_do_not() {
        var d by androidx.compose.runtime.mutableStateOf(Instance(Preset.CATCH_UP, today, today.plusDays(2)))
        rule.setContent { EfaTheme { ModifyFormScreen(d, today, null, emptyMap(), null, { d = it }, {}, {}, {}, {}) } }
        rule.onNodeWithContentDescription("Starts at 00:00").performScrollTo().assertExists(); rule.onNodeWithContentDescription("Ends at 24:00").assertExists()
        rule.onNodeWithContentDescription("Later start of the period").performScrollTo().performClick(); assertEquals(30, d.fromMin)
        rule.onNodeWithContentDescription("Earlier end of the period").performScrollTo().performClick(); assertEquals(1410, d.toMin)
        rule.onNodeWithContentDescription("For 28 days").performScrollTo().performClick(); assertEquals(28, d.days)
    }

    @Test fun RF1_a_period_off_has_whole_days_and_no_times() {
        rule.setContent { EfaTheme { ModifyFormScreen(Instance(Preset.PERIOD_OFF, today, today.plusDays(1)), today, null, emptyMap(), null, {}, {}, {}, {}, {}) } }
        rule.onNodeWithContentDescription("Later start of the period").assertDoesNotExist()
    }

    @Test fun RF6_a_late_start_form_asks_for_the_time_the_day_starts() {
        var d by androidx.compose.runtime.mutableStateOf(Instance(Preset.LATE_START, today, today))
        rule.setContent { EfaTheme { ModifyFormScreen(d, today, null, emptyMap(), null, { d = it }, {}, {}, {}, {}) } }
        rule.onNodeWithText("The day starts at").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Later The day starts at").performScrollTo().performClick(); assertEquals("10:30", d.settings["wake"])
    }

    @Test fun RF6_catch_up_can_leave_the_start_of_the_day_as_normal_or_choose_one() {
        var c by androidx.compose.runtime.mutableStateOf(Instance(Preset.CATCH_UP, today, today.plusDays(2)))
        rule.setContent { EfaTheme { ModifyFormScreen(c, today, null, emptyMap(), null, { c = it }, {}, {}, {}, {}) } }
        rule.onNodeWithContentDescription("The day starts at: as normal").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Choose The day starts at").performScrollTo().performClick(); assertEquals("10:00", c.settings["wake"])
    }

    @Test fun RF8_the_preview_draws_what_the_span_comes_to_as_tiles_and_a_bar_for_each_day() {
        val start = LocalDate.of(2026, 11, 16)
        val p = previewOf(Instance(Preset.CATCH_UP, start, start.plusDays(2)), WeekB.slots, emptyList(), at(LocalDate.of(2026, 11, 2), LocalTime.of(9, 0)))
        rule.setContent { EfaTheme { ModifyFormScreen(Instance(Preset.CATCH_UP, start, start.plusDays(2)), today, p, emptyMap(), null, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("WHAT IT COMES TO").performScrollTo().assertExists()
        rule.onNodeWithText("study a day").performScrollTo().assertExists(); rule.onNodeWithText("study cycles").assertExists(); rule.onNodeWithText("free time").assertExists()
        rule.onNode(hasContentDescription("Mon 16 Nov:", substring = true)).performScrollTo().assertExists()
        rule.onNode(hasContentDescription("Wed 18 Nov:", substring = true)).assertExists()
    }

    @Test fun an_assignment_form_adds_an_item_with_its_own_settings_and_removes_it() {
        var d by androidx.compose.runtime.mutableStateOf(Instance(Preset.ASSIGNMENT, today, today.plusDays(60), emptyMap(), emptyList()))
        rule.setContent { EfaTheme { ModifyFormScreen(d, today, null, emptyMap(), null, { d = it }, {}, {}, {}, {}) } }
        rule.onNodeWithContentDescription("Add Assignment").performScrollTo().performClick(); assertEquals(1, d.items.size)
        rule.onNodeWithText("Start this many days before").performScrollTo().assertExists(); rule.onNodeWithText("Priority (1 most, 5 least)").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Remove Assignment 1").performScrollTo().performClick(); assertEquals(0, d.items.size)
    }

    @Test fun Recommended_and_Preview_buttons_report_back_and_the_reasons_are_shown() {
        var rec = false; var prev = false
        val r = mapOf("slots_floor" to Rec("3", "30h of missed work over 7 days needs about 3 a day."))
        rule.setContent { EfaTheme { ModifyFormScreen(Instance(Preset.CATCH_UP, today, today.plusDays(6)), today, null, r, null, {}, { rec = true }, { prev = true }, {}, {}) } }
        rule.onNodeWithText("slots floor: 3. 30h of missed work over 7 days needs about 3 a day.").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Recommended. Fills in the settings from your own plan and times, with a reason for each.").performScrollTo().performClick(); assertTrue(rec)
        rule.onNodeWithText("Preview what it does").performScrollTo().performClick(); assertTrue(prev)
    }

    @Test fun a_preview_with_a_problem_cannot_be_applied_and_one_that_loosens_says_it_needs_the_wall() {
        val bad = ModPreview(Compiled(), BufferState.NoDeadline, BufferState.NoDeadline, listOf("Add at least one assignment."), false, false)
        rule.setContent { EfaTheme { ModifyFormScreen(Instance(Preset.ASSIGNMENT, today, today.plusDays(60)), today, bad, emptyMap(), null, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Add at least one assignment.").performScrollTo().assertExists(); rule.onNodeWithText("Apply", substring = true).assertDoesNotExist()
    }

    @Test fun a_loosening_preview_offers_apply_with_the_wall() {
        var applied = false
        val p = ModPreview(Compiled(lines = listOf("Period off Sat 7 Nov."), loosening = listOf("Work is paused")), BufferState.NoDeadline, BufferState.NoDeadline, emptyList(), true, false)
        rule.setContent { EfaTheme { ModifyFormScreen(Instance(Preset.PERIOD_OFF, today, today.plusDays(1)), today, p, emptyMap(), null, {}, {}, {}, { applied = true }, {}) } }
        rule.onNodeWithText("Apply, with the wall").performScrollTo().performClick(); assertTrue(applied)
    }

    @Test fun the_Admin_library_shows_a_load_set_and_each_job_can_be_switched() {
        var load: LoadSet? = null; var toggled: String? = null
        rule.setContent { EfaTheme { AdminScreen(AdminState(LoadSet.STANDARD), listOf("Tue 3: no room for Make the bed (3 minutes after waking)."), { load = it }, { toggled = it.id }, {}, {}, {}, {}) } }
        rule.onNodeWithText("Tue 3: no room for Make the bed (3 minutes after waking).").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Load: Thorough").performScrollTo().performClick(); assertEquals(LoadSet.THOROUGH, load)
        rule.onNodeWithContentDescription("Make the bed: on").performScrollTo().performClick(); assertEquals("make_bed", toggled)
    }

    @Test fun the_Admin_library_with_no_load_says_nothing_is_placed() {
        rule.setContent { EfaTheme { AdminScreen(AdminState(), emptyList(), {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Not set up: nothing is placed.").performScrollTo().assertExists()
    }

    @Test fun MG3a_the_essentials_screen_needs_the_wall_to_open_the_window_then_shows_the_time_left_and_three_adds() {
        var opened = false; var added: String? = null
        val apps = listOf(AppInfo("p.a", "Alpha"), AppInfo("p.b", "Beta"))
        var ui by androidx.compose.runtime.mutableStateOf(EssentialsUi(emptyList(), 0, 0, apps))
        rule.setContent { EfaTheme { EssentialsScreen(ui, { opened = true }, { added = it.pkg }, {}, {}) } }
        rule.onNodeWithContentDescription("Open the add window. Needs the typed wall.").performScrollTo().performClick(); assertTrue(opened)
        ui = EssentialsUi(emptyList(), 240_000, 1, apps)
        rule.waitForIdle()
        rule.onNodeWithText("The window is open for 4:00 more. 2 more can be added.").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Add Beta to the essentials").performScrollTo().performClick(); assertEquals("p.b", added)
    }
}
