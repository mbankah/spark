package app.efa

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ActivityScenario
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
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

private const val HEADER = "kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes"
private fun csv(vararg rows: String) = (listOf(HEADER) + rows).joinToString("\n")
private fun dayAfter(min: Long, dow: DayOfWeek): LocalDate { var d = LocalDate.now(LONDON).plusDays(min); while (d.dayOfWeek != dow) d = d.plusDays(1); return d }

/** The stored calendar, imports, catch-up and free-time conversion on a real database. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class PlanStorageTest {
    private lateinit var db: EfaDb
    private lateinit var plan: PlanRepo
    private val firstUse = at(LocalDate.of(2026, 10, 1), LocalTime.of(0, 0)).toInstant().toEpochMilli()
    private val tue = at(LocalDate.of(2026, 11, 3), LocalTime.of(8, 0))          // Tuesday morning, after Monday's backfill window

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
        plan = PlanRepo(db)
        runBlocking { db.seedIfEmpty(firstUse, LONDON.id) }
    }
    @After fun close() = db.close()

    private suspend fun verify(text: String, now: java.time.ZonedDateTime = tue.minusDays(14)) =
        verifyImport(parseTimetableCsv(text, now.toLocalDate()), plan.slots(), plan.known(), db.tasks().open().map { it.toDue() }, now)

    private val file get() = csv(
        "event,2026-11-17,,16:30,17:30,Cryptography lecture,lecture,Watson LT A,,standard,,",
        "deadline,2026-11-20,,12:00,,CS Project report,,,6,,,",
        "period,2026-11-16,2026-11-20,,,,assignment_week,,,,,",
    )

    @Test fun IM1_IM8_a_file_is_stored_and_the_same_file_again_changes_nothing() = runBlocking {
        val v = verify(file); assertTrue(v.ok, v.errors.joinToString { it.text() })
        val a = plan.apply(v, "term.csv", fileHash(file), 1L, LONDON.id, "x")
        assertEquals(Triple(1, 2, 1), Triple(a.overlay, a.tasks, a.periods))                 // the lecture; its study block and the deadline; the week
        assertTrue(plan.slots().any { it.date == LocalDate.of(2026, 11, 17) && it.title == "Cryptography lecture" })
        val again = verify(file); assertTrue(again.changesNothing); assertEquals(3 + 1, again.alreadyImported + 1)
        val b = plan.apply(again, "term.csv", fileHash(file), 2L, LONDON.id, "x")
        assertEquals(Triple(0, 0, 0), Triple(b.overlay, b.tasks, b.periods)); assertTrue(b.alreadyLogged)
        assertEquals(1, db.overlay().all().size); assertEquals(2, db.tasks().open().size); assertEquals(1, db.periods().all().size); assertEquals(1, db.imports().all().size)
    }

    @Test fun IM8_the_same_row_in_a_different_file_is_still_stored_once() = runBlocking {
        plan.apply(verify(file), "a.csv", "hashA", 1L, LONDON.id, "x")
        val other = csv("deadline,2026-11-20,,12:00,,cs project report,,,6,,,", "deadline,2026-11-21,,12:00,,Another,,,3,,,")
        val v = verify(other); assertEquals(1, v.alreadyImported)
        plan.apply(v, "b.csv", "hashB", 2L, LONDON.id, "x")
        assertEquals(3, db.tasks().open().size)
    }

    @Test fun IM11_a_failure_part_way_leaves_nothing_behind() = runBlocking {
        val v = verify(file).copy(periodHashes = emptyList())            // the third write fails after two have happened
        assertFails { plan.apply(v, "term.csv", "h", 1L, LONDON.id, "x") }
        assertEquals(0, db.overlay().all().size); assertEquals(0, db.tasks().open().size); assertEquals(0, db.periods().all().size); assertEquals(0, db.imports().all().size)
    }

    @Test fun IM4_a_file_with_errors_is_never_applied() = runBlocking {
        val v = verify(csv("deadline,not-a-date,,12:00,,x,,,,,,"))
        assertFalse(v.ok); assertFails { plan.apply(v, "bad.csv", "h", 1L, LONDON.id, "x") }
        assertEquals(0, db.imports().all().size)
    }

    @Test fun IM12_removing_an_import_takes_its_rows_and_keeps_work_that_was_proven() = runBlocking {
        val v = verify(file); val a = plan.apply(v, "term.csv", "h", 1L, LONDON.id, "x")
        val deadline = db.tasks().open().first { it.source == "import" }
        db.tasks().addDone(deadline.id, 30)
        plan.removeImport(a.importId)
        assertEquals(0, db.overlay().all().size); assertEquals(0, db.periods().all().size); assertEquals(0, db.imports().all().size)
        assertEquals(listOf(deadline.id), db.tasks().open().map { it.id }, "the cycle went; the deadline with 30 proven minutes stays")
    }

    @Test fun IM12_after_removing_the_same_file_can_be_imported_again() = runBlocking {
        val a = plan.apply(verify(file), "term.csv", fileHash(file), 1L, LONDON.id, "x"); plan.removeImport(a.importId)
        val v = verify(file); assertEquals(0, v.alreadyImported); assertFalse(v.changesNothing)
    }

    @Test fun RS1_RS5_a_missed_lecture_comes_back_once_with_its_reason() = runBlocking {
        val first = plan.automate(tue, firstUse, emptySet())
        val lecture = first.catchUps.single { it.title == "Catch-up: Randomness and Computation lecture" }
        assertEquals(1.0, lecture.hoursEstimate); assertEquals("missed", lecture.source); assertTrue(lecture.reason.startsWith("Missed Randomness"))
        assertEquals(endOfDayCutoff(WeekB.slots, LocalDate.of(2026, 11, 3)).toInstant().toEpochMilli(), lecture.dueMs)
        val second = plan.automate(tue.plusMinutes(1), firstUse, emptySet())
        assertTrue(second.catchUps.isEmpty(), "second look added ${second.catchUps.map { it.title }}")
        assertEquals(1, db.tasks().open().count { it.title == lecture.title })
    }

    @Test fun P8_RS1_a_proven_block_does_not_come_back() = runBlocking {
        val keys = slotsFor(WeekB.slots, LocalDate.of(2026, 11, 2)).filter { it.slot.type in setOf("lecture", "tutorial", "code", "study") || it.slot.type.startsWith("study") }.map { blockKey(it) }.toSet()
        val r = plan.automate(tue, firstUse, keys)
        assertTrue(r.catchUps.none { it.sourceKey!!.removePrefix("missed:").startsWith("2026-11-02") })
    }

    @Test fun RS1_installing_the_app_does_not_invent_a_backlog() = runBlocking {
        val r = plan.automate(tue, tue.minusHours(2).toInstant().toEpochMilli(), emptySet())
        assertTrue(r.catchUps.isEmpty())
    }

    @Test fun W2_RS1_bringing_missed_work_back_adds_to_the_Buffer_working_and_forgives_nothing() = runBlocking {
        val before = (bufferNow(tue, studyCycles(plan.slots(), tue) + db.tasks().open().map { it.toDue() }, plan.slots(), false) as BufferState.Value).result
        plan.automate(tue, firstUse, emptySet())
        val after = (bufferNow(tue, studyCycles(plan.slots(), tue) + db.tasks().open().map { it.toDue() }, plan.slots(), false) as BufferState.Value).result
        assertTrue(after.workHours > before.workHours); assertTrue(after.lines.any { it.title.startsWith("Catch-up: ") })
    }

    private fun bigTask(hours: Double, due: java.time.ZonedDateTime) = TaskRow(title = "Dissertation", dueMs = due.toInstant().toEpochMilli(), dueZone = LONDON.id, heaviness = 8,
        hoursEstimate = hours, createdMs = 1L)

    @Test fun RS3_RS5_a_tight_Buffer_turns_free_time_into_work_once_and_never_a_protected_slot() = runBlocking {
        // 3 hours of football and 6 protected hours leave Week B almost nothing to convert (Q-G69), so two free windows are added for this test.
        db.pattern().insertAll(listOf(7, 3).map { PatternSlotRow(day = it, startMin = 1200, endMin = 1280, title = "Free time - phone open", type = "free", venue = "", hardAlarm = false, notes = "") })
        val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(12, 0))
        db.tasks().insert(bigTask(70.0, at(LocalDate.of(2026, 11, 13), LocalTime.of(12, 0))))
        val r = plan.automate(now, firstUse, emptySet())
        assertTrue(r.conversions.isNotEmpty())
        val protected = (0..2).flatMap { protectedFree(WeekB.slots, LocalDate.of(2026, 11, 2).plusWeeks(it.toLong())) }.toSet()
        for (c in r.conversions) {
            val d = LocalDate.parse(c.date)
            assertEquals("convert", c.kind); assertEquals("study", c.type); assertTrue(d.isAfter(now.toLocalDate())); assertFalse(d to c.startMin in protected)
            assertTrue(c.reason.contains("became work"))
        }
        assertTrue(plan.automate(now.plusMinutes(1), firstUse, emptySet()).conversions.isEmpty(), "a second look converted more")
        assertTrue(plan.slots().any { isConversion(it) })
    }

    @Test fun RS3_a_comfortable_Buffer_converts_nothing() = runBlocking {
        val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(12, 0))
        db.tasks().insert(bigTask(2.0, at(LocalDate.of(2026, 12, 18), LocalTime.of(12, 0))))
        assertTrue(plan.automate(now, firstUse, emptySet()).conversions.isEmpty())
    }

    @Test fun RS6_P8_proven_minutes_reduce_the_earliest_deadline_first_and_a_finished_task_closes() = runBlocking {
        val a = db.tasks().insert(TaskRow(title = "A", dueMs = 1_000, dueZone = LONDON.id, heaviness = 3, hoursEstimate = 1.0, createdMs = 1L))
        val b = db.tasks().insert(TaskRow(title = "B", dueMs = 2_000, dueZone = LONDON.id, heaviness = 3, hoursEstimate = 2.0, createdMs = 1L))
        plan.credit(90, 5_000L, null)
        assertNotNull(db.tasks().get(a)!!.doneAtMs); assertNull(db.tasks().get(b)!!.doneAtMs)
        assertEquals(60, db.tasks().get(a)!!.doneMin); assertEquals(30, db.tasks().get(b)!!.doneMin)
        assertEquals(1.5, db.tasks().get(b)!!.remainingHours())
    }

    @Test fun PW2_an_assignment_week_puts_deadline_work_first_and_a_catch_up_week_puts_catch_up_first() {
        val manual = TaskRow(title = "Report", dueMs = 1, dueZone = LONDON.id, heaviness = 5, hoursEstimate = 1.0, createdMs = 1L)
        val missed = manual.copy(source = "missed")
        val assignment = PlannedPeriodRow(startDate = "2026-11-02", endDate = "2026-11-06", kind = "assignment_week", title = "", rowHash = "a", importId = null)
        val catchUp = assignment.copy(kind = "catch_up_week")
        assertTrue(manual.priority(assignment) > missed.priority(assignment)); assertTrue(missed.priority(catchUp) > manual.priority(catchUp))
        assertEquals(0, manual.priority(null)); assertEquals(0, missed.priority(null))
    }

    @Test fun PW3_the_period_running_today_is_found_and_one_that_is_over_is_not() {
        val p = PlannedPeriodRow(startDate = "2026-11-02", endDate = "2026-11-06", kind = "assignment_week", title = "", rowHash = "a", importId = null)
        assertNotNull(activePeriod(listOf(p), LocalDate.of(2026, 11, 2))); assertNotNull(activePeriod(listOf(p), LocalDate.of(2026, 11, 6)))
        assertNull(activePeriod(listOf(p), LocalDate.of(2026, 11, 7))); assertNull(activePeriod(listOf(p), LocalDate.of(2026, 11, 1)))
    }
}

/** A proven study block credits the open tasks (RS6). */
@RunWith(AndroidJUnit4::class)
class TaskCreditHookTest {
    @Test fun RS6_a_verified_study_block_reduces_the_open_tasks_and_a_cycle_does_not() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, EfaDb::class.java).build()
        try {
            val id = db.tasks().insert(TaskRow(title = "Essay", dueMs = 9_000_000_000_000, dueZone = LONDON.id, heaviness = 5, hoursEstimate = 4.0, createdMs = 1L))
            val repo = BlockRepo(ctx, db, WeekB.slots)
            val day = LocalDate.of(2026, 10, 19)
            val cycle = slotsFor(WeekB.slots, day).first { it.slot.title == "Notes and problems: Randomness" }
            repo.recordHold(cycle, 1L); assertTrue(repo.submitPhoto(cycle, jpeg(31), cycle.end.plusMinutes(5)).verified)
            assertEquals(0, db.tasks().get(id)!!.doneMin, "a study cycle has its own work and credits no task")
            val study = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 21)).first { it.slot.type == "study" }
            repo.recordHold(study, 1L); val v = repo.submitPhoto(study, jpeg(32), study.end.plusMinutes(5))
            assertTrue(v.verified); assertEquals(v.creditedMinutes, db.tasks().get(id)!!.doneMin)
        } finally { db.close() }
    }
}

@RunWith(AndroidJUnit4::class)
class PlanMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_6_to_7_keeps_tasks_and_adds_the_calendar_tables() {
        helper.createDatabase("mig67.db", 6).apply {
            execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('Essay',1,'Europe/London',5,6.25,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig67.db", 7, true, MIGRATION_6_7)
        db.query("SELECT title, source, doneMin, reason, sourceKey, importId FROM task").use {
            it.moveToFirst(); assertEquals("Essay", it.getString(0)); assertEquals("manual", it.getString(1)); assertEquals(0, it.getInt(2)); assertEquals("", it.getString(3))
            assertTrue(it.isNull(4)); assertTrue(it.isNull(5))
        }
        db.execSQL("INSERT INTO overlay_item (date,startMin,endMin,title,type,venue,notes,hardAlarm,cancelled,kind,rowHash,importId,reason,createdMs) VALUES ('2026-11-03',990,1050,'x','lecture','','',0,0,'import','h',NULL,'',1)")
        db.execSQL("INSERT INTO period (startDate,endDate,kind,title,rowHash,importId) VALUES ('2026-11-02','2026-11-06','assignment_week','','p',NULL)")
        db.execSQL("INSERT INTO import_log (atMs,name,fileHash,summary) VALUES (1,'a.csv','f','s')")
    }
}

/** The import screen: errors with lines, the preview, the wall, earlier imports. */
@RunWith(AndroidJUnit4::class)
class ImportScreenTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 10, 26)
    private val now = at(today, LocalTime.of(12, 0))
    private fun v(text: String) = verifyImport(parseTimetableCsv(text, today), WeekB.slots, emptySet(), emptyList(), now)
    private fun show(ui: ImportUi, imports: List<ImportLogRow> = emptyList(), apply: () -> Unit = {}, remove: (ImportLogRow) -> Unit = {}, copy: () -> Unit = {}) =
        rule.setContent { EfaTheme { ImportScreen(ui, imports, {}, copy, apply, remove, {}) } }

    @Test fun IM3_the_screen_offers_the_file_and_the_format_for_an_AI() {
        var copied = false
        show(ImportUi.Idle, copy = { copied = true })
        rule.onNodeWithText("Choose a file").assertExists(); rule.onNodeWithText("Copy the format for an AI").performClick()
        assertTrue(copied)
    }

    @Test fun IM4_an_error_shows_its_line_and_column_and_there_is_nothing_to_apply() {
        val bad = v(csv("deadline,2026-11-06,,12:00,,fine,,,5,,,", "deadline,2026-11-06,,25:00,,late,,,5,,,"))
        show(ImportUi.Checked("term.csv", bad, "h"))
        rule.onNodeWithText("1 problem. Nothing was changed. Fix the file and choose it again.").assertExists()
        rule.onNodeWithText("Line 3, start: \"25:00\" is not a time. Use 24-hour 09:00 or 14:30.").assertExists()
        rule.onNodeWithText("Apply").assertDoesNotExist()
    }

    @Test fun IM5_a_good_file_shows_what_it_does_and_the_Buffer_before_and_after() {
        var applied = false
        show(ImportUi.Checked("term.csv", v(csv("deadline,2026-11-06,,12:00,,Report,,,5,,,")), "h"), apply = { applied = true })
        rule.onNodeWithText("1 deadline added").assertExists()
        rule.onNodeWithText("Buffer now", substring = true).assertExists(); rule.onNodeWithText("Buffer with this file", substring = true).assertExists()
        rule.onNodeWithText("Apply").performClick(); assertTrue(applied)
    }

    @Test fun IM6_a_file_that_loosens_says_what_and_asks_for_the_wall() {
        val loosen = v(csv("event,2026-11-02,,09:00,10:00,Randomness lecture,lecture,,,,cancel,"))
        show(ImportUi.Checked("term.csv", loosen, "h"))
        rule.onNodeWithText("THIS FILE LOOSENS").assertExists(); rule.onNodeWithText("Cancels Randomness", substring = true).assertExists()
        rule.onNodeWithText("Apply, with the wall").assertExists()
    }

    @Test fun IM5_a_file_that_changes_nothing_says_so_and_has_no_apply() {
        show(ImportUi.Checked("old.csv", v(csv("deadline,2026-10-01,,12:00,,old,,,5,,,")), "h"))
        rule.onNodeWithText("Nothing in this file changes anything").assertExists(); rule.onNodeWithText("Apply").assertDoesNotExist()
    }

    @Test fun IM11_a_failed_apply_says_nothing_was_changed() {
        show(ImportUi.Failed("Applying failed (SQLiteException)."))
        rule.onNodeWithText("Applying failed (SQLiteException).").assertExists(); rule.onNodeWithText("Nothing was changed.").assertExists()
    }

    @Test fun IM12_earlier_imports_are_listed_with_a_remove_button() {
        var removed: ImportLogRow? = null
        val row = ImportLogRow(1, 1L, "term.csv", "h", "1 deadline added")
        show(ImportUi.Idle, listOf(row), remove = { removed = it })
        rule.onNodeWithText("term.csv").assertExists(); rule.onNodeWithText("1 deadline added").assertExists()
        rule.onNodeWithText("Remove").performScrollTo().performClick(); assertEquals(row, removed)
    }

    @Test fun IM6_the_wall_for_a_loosening_file_is_75_characters_by_hand() {
        rule.setContent { EfaTheme { ImportWallGate("Apply a file that loosens", "Cancels Randomness.", {}, {}) } }
        rule.onNodeWithText("TYPE THIS EXACTLY, BY HAND (0 OF 75)").assertExists()
    }

    @Test fun RS8_PW3_home_says_the_period_and_what_was_moved_with_the_reason() {
        val state = HomeState(
            date = "Mon 2 Nov", phone = null, phoneOpen = true, time = "08:00", buffer = BufferState.NoDeadline,
            nowLabel = null, nowTitle = null, nowDetail = null, next = emptyList(), freeToSpendPence = null,
            period = "Assignment week, until Fri 6 Nov",
            moved = listOf("Missed Randomness lecture. Back in the Buffer: 1.0h, due Tue 22:00.", "Buffer +1.0h is under its margin, so free time on 2026-11-03 became work"),
        )
        rule.setContent { EfaTheme { HomeScreen(state, {}, {}) } }
        rule.onNodeWithText("ASSIGNMENT WEEK, UNTIL FRI 6 NOV").assertExists()
        rule.onNodeWithText("MOVED FOR YOU").assertExists()
        rule.onNodeWithText("Missed Randomness lecture. Back in the Buffer: 1.0h, due Tue 22:00.").assertExists()
    }

    @Test fun RS2_a_block_says_what_is_placed_in_it() {
        val state = HomeState(
            date = "Mon 2 Nov", phone = null, phoneOpen = true, time = "13:00", buffer = BufferState.NoDeadline,
            nowLabel = "Now, until 15:00", nowTitle = "Work (was free time)", nowDetail = "Includes: Catch-up: Randomness (60 min), Essay (45 min)",
            next = emptyList(), freeToSpendPence = null,
        )
        rule.setContent { EfaTheme { HomeScreen(state, {}, {}) } }
        rule.onNodeWithText("Includes: Catch-up: Randomness (60 min), Essay (45 min)").assertExists()
    }
}

/** A file reaching Spark by Share or Open with (IM10), checked and previewed but never applied on its own (IM5). */
@RunWith(AndroidJUnit4::class)
class ImportIntentTest {
    companion object {
        @JvmStatic @org.junit.BeforeClass fun gateOff() { SetupGate.enforced = false }
        @JvmStatic @org.junit.AfterClass fun gateOn() { SetupGate.enforced = true }
    }
    @get:Rule val rule = createEmptyComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db get() = EfaDb.get(ctx)

    @Before fun clean() {
        listOf("overlay_item", "period", "import_log", "task").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
    }

    private val fri = dayAfter(5, DayOfWeek.FRIDAY)
    private val goodCsv get() = csv("deadline,$fri,,12:00,,Cryptography report,,,6,,,")
    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun launch(i: Intent): ActivityScenario<MainActivity> = ActivityScenario.launch(i.setClassName(ctx, "app.efa.MainActivity"))

    @Test fun IM10_Spark_is_offered_for_Share_and_Open_with_of_a_csv() {
        val pm = ctx.packageManager
        fun offered(i: Intent) = pm.queryIntentActivities(i, 0).any { it.activityInfo.packageName == "app.efa" }
        assertTrue(offered(Intent(Intent.ACTION_SEND).setType("text/csv")))
        assertTrue(offered(Intent(Intent.ACTION_SEND).setType("text/plain")))
        assertTrue(offered(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://x/a.csv"), "text/csv")))
        assertTrue(offered(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///a.csv"), "text/comma-separated-values")))
    }

    @Test fun IM10_a_web_link_cannot_send_a_file() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/timetable.csv")).addCategory(Intent.CATEGORY_BROWSABLE)
        assertTrue(ctx.packageManager.queryIntentActivities(i, 0).none { it.activityInfo.packageName == "app.efa" })
    }

    @Test fun IM5_IM10_shared_text_from_an_AI_app_is_previewed_and_nothing_is_stored() {
        launch(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, goodCsv)).use {
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Import a timetable file").fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(10_000) { rule.onAllNodesWithText("1 deadline added").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Shared text").assertExists(); rule.onNodeWithText("Apply").assertExists()
            assertEquals(0, count("task WHERE source LIKE 'import%'")); assertEquals(0, count("import_log"))
        }
    }

    @Test fun IM4_IM10_a_bad_shared_file_shows_its_errors_and_stores_nothing() {
        launch(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, csv("deadline,$fri,,12:00,,ok,,,5,,,", "deadline,31/02/2027,,12:00,,x,,,,,,"))).use {
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Line 3, date", substring = true).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Apply").assertDoesNotExist(); assertEquals(0, count("task WHERE source LIKE 'import%'"))
        }
    }

    @Test fun IM10_a_file_opened_with_Spark_is_read_from_its_address() {
        val f = File(ctx.cacheDir, "term.csv").also { it.writeText(goodCsv) }
        launch(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(f), "text/csv")).use {
            rule.waitUntil(10_000) { rule.onAllNodesWithText("1 deadline added").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("term.csv").assertExists(); assertEquals(0, count("task WHERE source LIKE 'import%'"))
        }
    }

    @Test fun IM5_IM11_pressing_Apply_stores_it_and_it_reaches_the_Buffer_working() {
        launch(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, goodCsv)).use {
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Apply").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Apply").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("DONE").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, count("task WHERE source LIKE 'import%'")); assertEquals(1, count("import_log"))
            assertEquals("Cryptography report", runBlocking { db.tasks().open().single { it.source == "import" }.title })
        }
    }

    @Test fun IM8_applying_the_same_shared_file_twice_stores_it_once() {
        repeat(2) {
            launch(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, goodCsv)).use {
                rule.waitUntil(10_000) { rule.onAllNodesWithText("Import a timetable file").fetchSemanticsNodes().isNotEmpty() }
                if (it == null) return@use
                rule.waitUntil(10_000) { rule.onAllNodesWithText("Apply").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("Nothing in this file changes anything").fetchSemanticsNodes().isNotEmpty() }
                if (rule.onAllNodesWithText("Apply").fetchSemanticsNodes().isNotEmpty()) {
                    rule.onNodeWithText("Apply").performClick(); rule.waitUntil(10_000) { rule.onAllNodesWithText("DONE").fetchSemanticsNodes().isNotEmpty() }
                }
            }
        }
        assertEquals(1, count("task WHERE source LIKE 'import%'")); assertEquals(1, count("import_log"))
    }

    @Test fun IM6_a_file_that_cancels_a_lecture_is_applied_only_after_the_wall() {
        val monday = dayAfter(3, DayOfWeek.MONDAY)
        val cancel = csv("event,$monday,,09:00,10:00,Randomness and Computation lecture,lecture,,,,cancel,")
        launch(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, cancel)).use {
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Apply, with the wall").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("THIS FILE LOOSENS").assertExists()
            rule.onNodeWithText("Apply, with the wall").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("TYPE THIS EXACTLY, BY HAND (0 OF 75)").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, count("overlay_item WHERE kind = 'import'"), "nothing is stored before the wall is passed")
            val target = rule.onNodeWithContentDescription("Characters to type", substring = true).fetchSemanticsNode().config
                .let { c -> c[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].first().removePrefix("Characters to type: ") }
            assertEquals(75, target.length)
            val field = rule.onNodeWithContentDescription("Type the characters here")
            target.forEach { ch -> field.performTextInput(ch.toString()) }
            rule.waitUntil(15_000) { rule.onAllNodesWithText("DONE").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, count("overlay_item WHERE kind = 'import'"))
            assertTrue(runBlocking { PlanRepo(db).slots() }.any { it.cancelled && it.date == monday })
        }
    }

    @Test fun IM12_removing_an_import_needs_the_wall_and_then_removes_it() {
        launch(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, goodCsv)).use {
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Apply").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Apply").performClick(); rule.waitUntil(10_000) { rule.onAllNodesWithText("DONE").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Remove").performScrollTo().performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("TYPE THIS EXACTLY, BY HAND (0 OF 75)").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, count("task WHERE source LIKE 'import%'"))
            val target = rule.onNodeWithContentDescription("Characters to type", substring = true).fetchSemanticsNode().config
                .let { c -> c[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].first().removePrefix("Characters to type: ") }
            val field = rule.onNodeWithContentDescription("Type the characters here")
            target.forEach { ch -> field.performTextInput(ch.toString()) }
            rule.waitUntil(15_000) { count("import_log") == 0 }
            assertEquals(0, count("task WHERE source LIKE 'import%'"))
        }
    }
}

/** The blocking service's own refresh does the automatic work (RS1, RS3) and the blocker follows it. */
@RunWith(AndroidJUnit4::class)
class AutoPlanE2ETest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db get() = EfaDb.get(ctx)
    private var savedList: String? = null

    @Before fun clean() {
        listOf("overlay_item", "period", "import_log", "task", "block").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        runBlocking {
            db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
            savedList = db.meta().get(META_BLOCKLIST)
            db.meta().put(MetaRow(META_FIRST_USE, at(LocalDate.of(2026, 10, 1), LocalTime.MIDNIGHT).toInstant().toEpochMilli().toString()))
            db.meta().put(MetaRow(META_BLOCKLIST, "com.instagram.android"))
        }
    }

    @After fun restore() {
        BlockingState.clockOverride = null
        db.openHelper.writableDatabase.execSQL("DELETE FROM pattern_slot WHERE title = 'Test free'")
        listOf("overlay_item", "period", "import_log", "task", "block").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        runBlocking { if (savedList == null) db.openHelper.writableDatabase.execSQL("DELETE FROM meta WHERE key = '$META_BLOCKLIST'") else db.meta().put(MetaRow(META_BLOCKLIST, savedList!!)) }
    }

    @Test fun RS1_RS5_each_refresh_of_the_blocker_looks_for_missed_work_and_adds_it_once() = runBlocking {
        BlockingState.clockOverride = { at(LocalDate.of(2026, 11, 3), LocalTime.of(8, 0)) }
        BlockingState.refresh(ctx)
        val first = db.tasks().open().filter { it.source == "missed" }
        assertTrue(first.any { it.title == "Catch-up: Randomness and Computation lecture" }, first.map { it.title }.toString())
        assertTrue(BlockingState.snapshot!!.tasks.any { it.title.startsWith("Catch-up: ") }, "the blocker sees the new work at once")
        repeat(3) { BlockingState.refresh(ctx) }
        assertEquals(first.size, db.tasks().open().count { it.source == "missed" })
    }

    @Test fun RS3_RS8_a_tight_Buffer_turns_free_time_into_work_and_the_blocker_then_blocks_it_as_work() = runBlocking {
        val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(12, 0))
        BlockingState.clockOverride = { now }
        // 3 hours of football and 6 protected hours leave Week B almost nothing to convert (Q-G69), so two free windows are added for this test.
        db.openHelper.writableDatabase.execSQL("INSERT INTO pattern_slot (day,startMin,endMin,title,type,venue,hardAlarm,notes,edited) VALUES (7,1200,1280,'Test free','free','',0,'',0), (3,1200,1280,'Test free','free','',0,'',0)")
        db.tasks().insert(TaskRow(title = "Dissertation", dueMs = at(LocalDate.of(2026, 11, 13), LocalTime.of(12, 0)).toInstant().toEpochMilli(), dueZone = LONDON.id,
            heaviness = 8, hoursEstimate = 140.0, createdMs = 1L))
        BlockingState.refresh(ctx)
        val converted = db.overlay().all().filter { it.kind == "convert" }
        assertTrue(converted.isNotEmpty()); assertTrue(converted.all { it.reason.contains("became work") })
        val c = converted.first()
        val inside = at(LocalDate.parse(c.date), LocalTime.of(0, 0)).plusMinutes(c.startMin + 5L)
        BlockingState.clockOverride = { inside }
        BlockingState.refresh(ctx)
        val d = BlockingState.decide("com.instagram.android")
        assertTrue(d is Decision.Block && d.reason == BlockReason.WORK_BLOCK, d.toString())
        val countBefore = db.overlay().all().size
        BlockingState.clockOverride = { now }
        repeat(2) { BlockingState.refresh(ctx) }
        assertEquals(countBefore, db.overlay().all().size, "a refresh must not convert more")
    }
}
