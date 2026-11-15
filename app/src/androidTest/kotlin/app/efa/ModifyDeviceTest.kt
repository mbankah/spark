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

    @Test fun a_catch_up_week_adds_study_slots_by_module_as_work_due_each_evening() = runBlocking<Unit> {
        repo.apply(Instance(Preset.CATCH_UP, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20)), now, 75)
        val mine = db.tasks().open().filter { it.source == "modifier" }
        assertTrue(mine.isNotEmpty()); assertTrue(mine.all { it.title.endsWith(": notes and problems") })
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

    @Test fun the_home_lists_every_built_preset_and_says_what_is_not_built() {
        rule.setContent { EfaTheme { ModifyHomeScreen(emptyList(), today, {}, {}, {}, {}, null, {}) } }
        Preset.entries.forEach { rule.onNodeWithText(it.label).performScrollTo().assertExists() }
        rule.onNodeWithText("Not built yet: Revision", substring = true).performScrollTo().assertExists()
    }

    @Test fun picking_a_preset_and_removing_an_active_one_report_back() {
        var picked: Preset? = null; var removed: Long? = null
        val row = ModifierRow(7, "assignment", "2026-11-02", "2026-11-20", "x", "", "", "active", 1, false)
        rule.setContent { EfaTheme { ModifyHomeScreen(listOf(row), today, { picked = it }, { removed = it.id }, {}, {}, null, {}) } }
        rule.onNodeWithContentDescription("Set up Focus week").performScrollTo().performClick(); assertEquals(Preset.FOCUS, picked)
        rule.onNodeWithContentDescription("Remove the Assignment that runs 2026-11-02 to 2026-11-20").performScrollTo().performClick(); assertEquals(7L, removed)
    }

    @Test fun the_form_draws_settings_from_their_definitions_and_changes_them() {
        var d = Instance(Preset.CATCH_UP, today, today.plusDays(6))
        rule.setContent { androidx.compose.runtime.key(d) { EfaTheme { ModifyFormScreen(d, today, null, emptyMap(), null, { d = it }, {}, {}, {}, {}) } } }
        rule.onNodeWithText("Study slots a day (at least)").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("More Study slots a day (at least)").performScrollTo().performClick()
        assertEquals("4", d.settings["slots_floor"])
        rule.onNodeWithContentDescription("Intensity: rapid").performScrollTo().performClick(); assertEquals("rapid", d.settings["intensity"])
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
