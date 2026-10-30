package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

private fun lt(m: Int, d: Int, h: Int = 9, min: Int = 0) = at(LocalDate.of(2026, m, d), LocalTime.of(h, min))

/** Library periods on a real database: C1, C2, C3, O11, O13, Y9, PW1. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class LibraryStorageTest {
    private lateinit var db: EfaDb
    private lateinit var plan: PlanRepo
    private val now = lt(11, 2)
    private val tue = LocalDate.of(2026, 11, 3)
    private val hours = LibraryHours(8 * 60, 22 * 60)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
        plan = PlanRepo(db)
        runBlocking { db.seedIfEmpty(lt(10, 21).toInstant().toEpochMilli(), LONDON.id) }
    }
    @After fun close() = db.close()

    @Test fun O11_the_hours_are_asked_for_once_and_kept() = runBlocking {
        assertNull(plan.libraryHours(db))
        assertEquals("Tell Spark the library's opening hours first.", plan.addLibraryPeriod(db, tue, tue, now))
        plan.saveLibraryHours(db, hours)
        assertEquals(hours, plan.libraryHours(db))
    }

    @Test fun C2_a_planned_library_period_moves_the_work_and_adds_the_walks() = runBlocking {
        plan.saveLibraryHours(db, hours)
        assertNull(plan.addLibraryPeriod(db, tue, tue, now))
        val day = slotsFor(plan.slots(), tue)
        assertTrue(day.any { it.slot.venue == LIBRARY_VENUE })
        assertTrue(day.any { it.slot.title == WALK_TO_LIBRARY }); assertTrue(day.any { it.slot.title == WALK_FROM_LIBRARY })
    }

    @Test fun C2_the_walk_minutes_come_back_as_catch_up_work_once() = runBlocking {
        plan.saveLibraryHours(db, hours)
        plan.addLibraryPeriod(db, tue, tue, now)
        val back = db.tasks().open().filter { it.source == "library" }
        assertEquals(1, back.size)
        assertEquals("That library period is already planned.", plan.addLibraryPeriod(db, tue, tue, now))
        assertEquals(1, db.tasks().open().count { it.source == "library" })
    }

    @Test fun PW1_a_period_longer_than_fourteen_days_or_in_the_past_is_refused() = runBlocking {
        plan.saveLibraryHours(db, hours)
        assertNotNull(plan.addLibraryPeriod(db, tue, tue.plusDays(14), now))
        assertNotNull(plan.addLibraryPeriod(db, now.toLocalDate().minusDays(1), tue, now))
        assertTrue(db.periods().all().none { it.kind == LIBRARY_KIND })
    }

    @Test fun O13_a_library_period_leaves_meals_alone_and_does_not_make_an_assignment_week() = runBlocking {
        plan.saveLibraryHours(db, hours)
        suspend fun meals() = slotsFor(plan.slots(), tue).filter { it.slot.type == "meal" }.map { it.slot.startMin }
        val before = meals()
        plan.addLibraryPeriod(db, tue, tue, now)
        assertEquals(before, meals())
        assertNull(activePeriod(db.periods().all(), tue))                    // a library period is not an assignment or catch-up week
    }

    @Test fun C2_removing_the_period_takes_the_library_and_its_unstarted_catch_up_away() = runBlocking {
        plan.saveLibraryHours(db, hours)
        plan.addLibraryPeriod(db, tue, tue, now)
        plan.removeLibraryPeriod(db, db.periods().all().single().id)
        assertTrue(slotsFor(plan.slots(), tue).none { it.slot.venue == LIBRARY_VENUE })
        assertTrue(db.tasks().open().none { it.source == "library" })
    }

    @Test fun C5_a_day_off_planned_after_a_library_period_removes_its_work_and_walks() = runBlocking {
        plan.saveLibraryHours(db, hours)
        plan.addLibraryPeriod(db, tue, tue, now)
        plan.addDaysOff(db, listOf(tue), "planned", 100, now, sameDay = false)
        assertTrue(slotsFor(plan.slots(), tue).none { it.slot.type in CUTTABLE_WORK || isLibraryWalkSlot(it.slot) })
    }

    @Test fun C2_a_library_period_survives_a_second_look_of_the_automation() = runBlocking {
        plan.saveLibraryHours(db, hours)
        plan.addLibraryPeriod(db, tue, tue, now)
        val before = slotsFor(plan.slots(), tue).map { it.slot.title to it.slot.startMin }
        plan.automate(now, lt(10, 21).toInstant().toEpochMilli(), emptySet())
        assertEquals(before.filter { it.first.contains("library", true) }, slotsFor(plan.slots(), tue).map { it.slot.title to it.slot.startMin }.filter { it.first.contains("library", true) })
    }
}

/** The library screen. */
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule val rule = createComposeRule()
    private val now = lt(11, 2)
    private val today = now.toLocalDate()

    private fun ui(hours: LibraryHours?, editing: Boolean = false, start: LocalDate? = null, preview: LibraryPreview? = null) =
        LibraryUi(today, hours, editing, hours?.openMin ?: 480, hours?.closeMin ?: 1320, start, 5, preview, listOf(5L to "Tue 3 Nov to Sat 7 Nov"))

    @Test fun O11_without_hours_the_screen_asks_for_them_and_saves_what_was_chosen() {
        var saved: Pair<Int, Int>? = null
        rule.setContent { EfaTheme { LibraryScreen(ui(null), { o, c -> saved = o to c }, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}) } }
        rule.onNodeWithText("OPENING HOURS, ASKED ONCE").assertExists()
        rule.onNodeWithText("Save hours").performScrollTo().performClick()
        assertEquals(480 to 1320, saved)
    }

    @Test fun C2_with_hours_the_screen_shows_the_preview_and_plans_it() {
        var planned = false
        val p = libraryPreview(WeekB.slots, emptyList(), now, today.plusDays(1), today.plusDays(5), LibraryHours(480, 1320))
        rule.setContent { EfaTheme { LibraryScreen(ui(LibraryHours(480, 1320), start = today.plusDays(1), preview = p), { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, { planned = true }, {}, {}) } }
        rule.onNodeWithText("Library hours: 08:00-22:00").assertExists()
        rule.onNodeWithText(p.lines.first()).assertExists()
        rule.onNodeWithText("Plan the library period").performScrollTo().performClick(); assertTrue(planned)
    }

    @Test fun C1_picking_a_first_day_and_a_length_report_back_and_a_period_can_be_removed() {
        var picked: Pair<LocalDate, Int>? = null; var removed: Long? = null
        rule.setContent { EfaTheme { LibraryScreen(ui(LibraryHours(480, 1320)), { _, _ -> }, {}, { _, _ -> }, { d, n -> picked = d to n }, {}, { removed = it }, {}) } }
        rule.onNodeWithContentDescription("Library starts Tomorrow").performClick(); assertEquals(today.plusDays(1) to 5, picked)
        rule.onNodeWithContentDescription("Library for 7 days").performClick(); assertEquals(today to 7, picked)
        rule.onNodeWithContentDescription("Remove the library period Tue 3 Nov to Sat 7 Nov").performScrollTo().performClick(); assertEquals(5L, removed)
    }
}
