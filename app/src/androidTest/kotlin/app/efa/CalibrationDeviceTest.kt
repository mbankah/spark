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
import kotlin.test.*

/** Calibration on a real database: B5, R10, E9. */
@RunWith(AndroidJUnit4::class)
class CalibrationStorageTest {
    private lateinit var db: EfaDb
    private lateinit var repo: CalibrationRepo

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
        repo = CalibrationRepo(db)
    }
    @After fun close() = db.close()

    private suspend fun finished(title: String, heaviness: Int = 5, source: String = "manual", doneAt: Long = 1_000L): TaskRow {
        val id = db.tasks().insert(TaskRow(title = title, dueMs = 9_999_999L, dueZone = LONDON.id, heaviness = heaviness, hoursEstimate = quickAddHours(heaviness), createdMs = 1, doneAtMs = doneAt, source = source))
        return db.tasks().get(id)!!
    }

    @Test fun B5_a_finished_task_is_offered_once_and_not_again_after_it_is_answered() = runBlocking {
        val t = finished("Essay")
        assertEquals("Essay", repo.nextToReview(0)?.task?.title)
        assertTrue(repo.report(t, TookReport.AsPlanned, 5))
        assertNull(repo.nextToReview(0))
        assertFalse(repo.report(t, TookReport.AsPlanned, 6))                    // answered once
    }

    @Test fun B5_automatic_catch_up_and_unfinished_tasks_are_never_asked_about() = runBlocking {
        finished("Catch-up", source = "missed")
        db.tasks().insert(TaskRow(title = "Open", dueMs = 9_999_999L, dueZone = LONDON.id, heaviness = 5, hoursEstimate = 6.25, createdMs = 1))
        assertNull(repo.nextToReview(0))
    }

    @Test fun B5_it_needed_more_puts_the_task_back_in_the_Buffer_with_the_time_added() = runBlocking {
        val t = finished("Essay")
        assertTrue(repo.report(t, TookReport.MoreMinutes(60), 5))
        val back = db.tasks().get(t.id)!!
        assertNull(back.doneAtMs); assertEquals(t.hoursEstimate + 1.0, back.hoursEstimate, 1e-9)
    }

    @Test fun B5_a_report_that_is_not_one_of_the_choices_records_nothing() = runBlocking {
        val t = finished("Essay")
        assertFalse(repo.report(t, TookReport.MoreMinutes(7), 5)); assertFalse(repo.report(t, TookReport.LessFraction(0.1), 5))
        assertTrue(db.calibration().all().isEmpty())
    }

    @Test fun B5_E9_after_three_tasks_run_long_the_estimate_for_that_size_rises_and_says_why() = runBlocking {
        repeat(3) { i -> val t = finished("T$i"); repo.report(t, TookReport.MoreMinutes(120), 5) }
        val cal = repo.calibration()
        assertTrue(cal.hours(5) > quickAddHours(5))
        assertTrue(cal.note(5).contains("from 3 finished"))
    }

    @Test fun R10_telling_it_took_less_again_and_again_pulls_a_big_slider_back_to_the_real_time() = runBlocking {
        repeat(6) { i -> val t = finished("H$i", heaviness = 10); repo.report(t, TookReport.LessFraction(0.5), 5) }
        assertTrue(repo.calibration().hours(10) < quickAddHours(10) * 0.8)
    }
}

@RunWith(AndroidJUnit4::class)
class CalibrationMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_11_to_12_keeps_data_and_adds_the_calibration_table() {
        helper.createDatabase("mig1112.db", 11).apply {
            execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('Essay',1,'Europe/London',5,6.25,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig1112.db", 12, true, MIGRATION_11_12)
        db.query("SELECT COUNT(*) FROM task").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO calibration_sample (taskId,heaviness,estimatedHours,actualHours,atMs,kind) VALUES (1,5,6.25,7.0,1,'x')")
    }
}

@RunWith(AndroidJUnit4::class)
class CalibrationScreenTest {
    @get:Rule val rule = createComposeRule()
    private val task = ReviewTask(1, "Essay", 6.25, "Heaviness 5 is about 6.3h of work, a first guess: 0 of 3 finished medium (4 to 6) tasks needed.")

    @Test fun B5_the_screen_offers_as_planned_more_and_less_and_reports_each() {
        val got = ArrayList<TookReport>()
        rule.setContent { EfaTheme { ReviewScreen(task, { got += it }, {}) } }
        rule.onNodeWithText("About as planned").performScrollTo().performClick()
        rule.onNodeWithText("It needed 1 hour more").performScrollTo().performClick()
        rule.onNodeWithText("It took 50% of the time: type ${WallLength.CALIBRATE_LESS} characters").performScrollTo().performClick()
        assertEquals(listOf(TookReport.AsPlanned, TookReport.MoreMinutes(60), TookReport.LessFraction(0.5)), got)
    }

    @Test fun E9_the_screen_says_how_the_estimate_is_based() {
        rule.setContent { EfaTheme { ReviewScreen(task, {}, {}) } }
        rule.onNodeWithText("0 of 3 finished medium", substring = true).assertExists()
    }
}
