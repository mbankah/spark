package app.efa

import android.app.NotificationManager
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

private fun z(m: Int, d: Int, h: Int = 12, min: Int = 0) = at(LocalDate.of(2026, m, d), LocalTime.of(h, min))

/** The weekly money, earnings, football and resets on a real database. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class RewardsStorageTest {
    private lateinit var db: EfaDb
    private lateinit var rewards: RewardsRepo
    private lateinit var plan: PlanRepo
    private val slots = WeekB.slots
    private val firstUse = z(10, 21, 9).toInstant().toEpochMilli()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
        rewards = RewardsRepo(db); plan = PlanRepo(db)
        runBlocking { db.seedIfEmpty(firstUse, LONDON.id) }
    }
    @After fun close() = db.close()

    private fun verifyAll(monday: LocalDate, fraction: Double = 1.0) = runBlocking {
        for (b in weekBlocks(slots, monday, emptyMap())) {
            val ds = (0L..6L).flatMap { slotsFor(slots, monday.plusDays(it)) }.first { blockKey(it) == b.key }
            db.blocks().upsert(BlockRow(key = b.key, title = b.title, type = b.type, startMs = ds.start.toInstant().toEpochMilli(), endMs = ds.end.toInstant().toEpochMilli(),
                zone = LONDON.id, verified = true, creditedMin = (b.plannedMin * fraction).toInt(), provenAtMs = ds.end.toInstant().toEpochMilli()))
        }
    }

    @Test fun R8_the_week_is_read_from_the_blocks_that_were_proven() = runBlocking {
        val monday = LocalDate.of(2026, 11, 2)
        assertEquals(0, rewards.week(slots, monday).money.freeToSpendPence)
        verifyAll(monday)
        assertEquals(FREE_CAP_PENCE, rewards.week(slots, monday).money.freeToSpendPence)
    }

    @Test fun R8_half_the_confirmed_minutes_pay_half() = runBlocking {
        val monday = LocalDate.of(2026, 11, 2); verifyAll(monday, 0.5)
        assertTrue(kotlin.math.abs(rewards.week(slots, monday).money.freeToSpendPence - 1250) <= 15, "half the minutes pay about half (odd minutes round down)")
    }

    @Test fun G6_the_first_week_that_counts_is_the_first_Monday_after_14_days() {
        assertEquals(LocalDate.of(2026, 11, 16), rewards.liveFrom(z(11, 2, 9).toInstant().toEpochMilli()), "a Monday plus 14 days is a Monday")
        assertEquals(LocalDate.of(2026, 11, 23), rewards.liveFrom(z(11, 4, 9).toInstant().toEpochMilli()))
        assertEquals(LocalDate.of(2026, 11, 9), rewards.liveFrom(firstUse))
    }

    @Test fun R9_weeks_that_have_ended_are_written_once_and_the_first_two_weeks_are_not_live() = runBlocking {
        assertEquals(4, rewards.closeWeeks(slots, firstUse, z(11, 17)))
        assertEquals(0, rewards.closeWeeks(slots, firstUse, z(11, 17)), "a second look closes nothing")
        val rows = db.weekMoney().all()
        assertEquals(listOf("2026-10-19", "2026-10-26", "2026-11-02", "2026-11-09"), rows.map { it.monday })
        assertEquals(listOf(false, false, false, true), rows.map { it.live })
    }

    @Test fun R9_the_unearned_part_moves_to_savings_but_only_in_live_weeks() = runBlocking {
        verifyAll(LocalDate.of(2026, 11, 9), 0.5)
        rewards.closeWeeks(slots, firstUse, z(11, 17))
        val v = rewards.view(slots, firstUse, z(11, 17))
        assertEquals(1, v.closedWeeks); assertTrue(kotlin.math.abs(v.savedPence - 2750) <= 15, "50% pays about 12.50, so about 27.50 is saved: ${v.savedPence}")
        assertTrue(v.live)
    }

    @Test fun R9_a_closed_week_is_frozen_and_a_later_change_does_not_rewrite_it() = runBlocking {
        rewards.closeWeeks(slots, firstUse, z(11, 17))
        val before = db.weekMoney().all()
        verifyAll(LocalDate.of(2026, 11, 9))
        rewards.closeWeeks(slots, firstUse, z(11, 24))
        assertEquals(before, db.weekMoney().all().take(before.size))
    }

    @Test fun G6_R20_F5_inside_the_first_14_days_the_guide_records_and_pays_nothing_to_show() = runBlocking {
        val v = rewards.view(slots, firstUse, z(10, 28))
        assertFalse(v.live); assertEquals(LocalDate.of(2026, 11, 9), v.liveFrom)
    }

    @Test fun R2_O3_a_proven_work_block_earns_ten_minutes_an_hour_and_a_lecture_earns_nothing() = runBlocking {
        val now = z(11, 4, 18)
        val cycle = slotsFor(slots, LocalDate.of(2026, 11, 4)).first { it.slot.type == "study-standard" }
        val lecture = slotsFor(slots, LocalDate.of(2026, 11, 4)).first { it.slot.type == "lecture" }
        db.blocks().upsert(BlockRow(key = blockKey(cycle), title = cycle.slot.title, type = cycle.slot.type, startMs = 1, endMs = 2, zone = LONDON.id, verified = true, creditedMin = 125, provenAtMs = z(11, 4, 12, 30).toInstant().toEpochMilli()))
        db.blocks().upsert(BlockRow(key = blockKey(lecture), title = lecture.slot.title, type = "lecture", startMs = 1, endMs = 2, zone = LONDON.id, verified = true, creditedMin = 60, provenAtMs = z(11, 4, 11).toInstant().toEpochMilli()))
        assertEquals(listOf(Earning(z(11, 4, 12, 30).toInstant().toEpochMilli(), 20)), rewards.earnings(now))
    }

    @Test fun R2_what_was_earned_yesterday_is_not_earned_today() = runBlocking {
        db.blocks().upsert(BlockRow(key = "k", title = "t", type = "study", startMs = 1, endMs = 2, zone = LONDON.id, verified = true, creditedMin = 120, provenAtMs = z(11, 3, 15).toInstant().toEpochMilli()))
        assertTrue(rewards.earnings(z(11, 4, 9)).isEmpty())
    }

    @Test fun F7_Y5_football_is_placed_for_this_week_and_next_once_and_survives_a_second_look() = runBlocking {
        val now = z(11, 2, 12)
        plan.automate(now, firstUse, emptySet())
        val football = db.overlay().all().filter { it.kind == "football" }
        assertEquals(2 * FOOTBALL_WEEK_MIN, football.sumOf { it.endMin - it.startMin }, "3 hours this week and 3 hours next")
        plan.automate(now.plusMinutes(1), firstUse, emptySet())
        assertEquals(football.size, db.overlay().all().count { it.kind == "football" })
        assertTrue(plan.slots().filter { isFootball(it) }.all { it.type == "match" })
    }

    /** Week B has 10.75 hours of free time a week, and 3 hours of football and 6 protected hours leave under 2 hours to convert (Q-G69), so the tests add two free windows. */
    private fun roomToConvert() = runBlocking {
        db.pattern().insertAll(listOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY).map { PatternSlotRow(day = it.value, startMin = 1200, endMin = 1280, title = "Free time - phone open", type = "free", venue = "", hardAlarm = false, notes = "") })
    }

    @Test fun W4_every_conversion_the_planner_stores_comes_with_its_reset() = runBlocking {
        roomToConvert()
        db.tasks().insert(TaskRow(title = "Dissertation", dueMs = z(11, 13).toInstant().toEpochMilli(), dueZone = LONDON.id, heaviness = 8, hoursEstimate = 150.0, createdMs = 1L))
        plan.automate(z(11, 2, 12), firstUse, emptySet())
        val rows = db.overlay().all()
        val conv = rows.filter { it.kind == "convert" }; val resets = rows.filter { it.kind == "reset" }
        assertTrue(conv.isNotEmpty()); assertEquals(conv.size, resets.size)
        for (c in conv) assertTrue(resets.any { it.date == c.date && it.startMin == c.endMin && it.endMin - it.startMin == RESET_MIN })
    }

    @Test fun F4_turning_a_converted_slot_back_removes_it_and_its_reset_and_it_is_not_converted_again() = runBlocking {
        roomToConvert()
        db.tasks().insert(TaskRow(title = "Dissertation", dueMs = z(11, 13).toInstant().toEpochMilli(), dueZone = LONDON.id, heaviness = 8, hoursEstimate = 150.0, createdMs = 1L))
        val now = z(11, 2, 12)
        plan.automate(now, firstUse, emptySet())
        val c = db.overlay().all().first { it.kind == "convert" }
        plan.turnBack(db, LocalDate.parse(c.date), c.startMin, c.endMin + RESET_MIN, now.toInstant().toEpochMilli())
        assertNull(db.overlay().conversion(c.date, c.startMin)); assertTrue(db.overlay().all().none { it.kind == "reset" && it.date == c.date && it.startMin == c.endMin })
        assertTrue(plan.slots().none { it.date == LocalDate.parse(c.date) && it.startMin == c.startMin && isConversion(it) })
        plan.automate(now.plusMinutes(1), firstUse, emptySet())
        assertNull(db.overlay().conversion(c.date, c.startMin), "the window the person turned back stays a break")
    }

    @Test fun B8_F6_after_a_short_night_the_day_counts_remaining_work_heavier() = runBlocking {
        db.nights().upsert(NightSummaryRow("2026-11-03", z(11, 3, 23).toInstant().toEpochMilli(), z(11, 4, 5).toInstant().toEpochMilli(), 330, 0, 0, 0, 0, 0, 0, 1))
        assertTrue(isShortSleepToday(db, z(11, 4, 9))); assertFalse(isShortSleepToday(db, z(11, 5, 9)), "it is that day's night only")
        db.nights().upsert(NightSummaryRow("2026-11-04", z(11, 4, 23).toInstant().toEpochMilli(), z(11, 5, 7).toInstant().toEpochMilli(), 480, 0, 0, 0, 0, 0, 0, 1))
        assertFalse(isShortSleepToday(db, z(11, 5, 9)))
    }

    @Test fun B8_the_Buffer_working_is_heavier_after_a_short_night() {
        val work = listOf(DueWork("Hard", 4.0, 8, z(11, 6)), DueWork("Easy", 2.0, 2, z(11, 6)))
        val normal = (bufferNow(z(11, 4, 9), work, slots, false) as BufferState.Value).result.workHours
        val tired = (bufferNow(z(11, 4, 9), work, slots, true) as BufferState.Value).result.workHours
        assertEquals(normal + 4.0 * 0.5 + 2.0 * 0.15, tired, 1e-9)
    }
}

@RunWith(AndroidJUnit4::class)
class RewardsMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_9_to_10_keeps_data_and_adds_the_weekly_money_table() {
        helper.createDatabase("mig910.db", 9).apply {
            execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('Essay',1,'Europe/London',5,6.25,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig910.db", 10, true, MIGRATION_9_10)
        db.query("SELECT COUNT(*) FROM task").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO week_money (monday,plannedWeight,verifiedWeight,freeToSpendPence,savingsPence,live,closedAtMs) VALUES ('2026-11-02',10.0,5.0,1250,2750,1,1)")
    }
}

/** The money screen, the home card and the block screen's reward lines. */
@RunWith(AndroidJUnit4::class)
class RewardsScreenTest {
    @get:Rule val rule = createComposeRule()
    private val slots = WeekB.slots
    private val monday = LocalDate.of(2026, 11, 2)

    private fun view(live: Boolean, fraction: Double = 0.5, saved: Int = 2750, weeks: Int = 1): MoneyView {
        val credited = weekBlocks(slots, monday, emptyMap()).associate { it.key to (it.plannedMin * fraction).toInt() }
        return MoneyView(live, LocalDate.of(2026, 11, 16), weekResult(slots, monday, credited), saved, weeks)
    }

    @Test fun R8_the_money_screen_shows_the_payout_the_working_and_savings() {
        rule.setContent { EfaTheme { MoneyScreen(view(true)) {} } }
        val v = view(true)
        rule.onNodeWithText(pounds(v.week.money.freeToSpendPence) + " free to spend").assertExists(); rule.onNodeWithText("Food: £40.00, guaranteed every week, never tied to work.").assertExists()
        rule.onNodeWithText("Moved to savings: " + pounds(v.week.money.savingsPence)).assertExists(); rule.onNodeWithText("Saved so far: £27.50 over 1 week.").assertExists()
        rule.onNodeWithText("Free to spend = £25.00 x", substring = true).assertExists()
    }

    @Test fun G6_before_the_first_live_week_the_screen_says_recording_and_when_money_starts() {
        rule.setContent { EfaTheme { MoneyScreen(view(false)) {} } }
        rule.onNodeWithText("Recording").assertExists(); rule.onNodeWithText("Money starts on Mon 16 Nov.", substring = true).assertExists()
    }

    @Test fun R4_the_screen_shows_progress_as_hours_done_of_hours_planned() {
        val v = view(true); rule.setContent { EfaTheme { MoneyScreen(v) {} } }
        rule.onNodeWithText(v.week.progress() + " this week").assertExists()
    }

    private fun home(pence: Int?, note: String? = null, earned: Boolean = false, progress: String? = null, onMoney: () -> Unit = {}) = rule.setContent {
        EfaTheme {
            HomeScreen(HomeState(date = "Wed 4 Nov", phone = "Phone closed", phoneOpen = false, time = "17:05", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null,
                freeToSpendPence = pence, progress = progress, moneyNote = note, earnedOn = earned), {}, {}, onMoney = onMoney)
        }
    }

    @Test fun R8_G6_home_shows_the_pounds_once_money_is_live_and_opens_the_working_on_tap() {
        var tapped = false
        home(1250, progress = "14.0 of 60.0 hours done", onMoney = { tapped = true })
        rule.onNodeWithText("£12.50").assertExists(); rule.onNodeWithText("14.0 of 60.0 hours done").assertExists()
        rule.onNodeWithContentDescription("Free to spend this week. £12.50. Tap for the working.").performScrollTo().performClick(); assertTrue(tapped)
    }

    @Test fun G6_home_says_recording_and_when_money_starts_before_that() {
        home(null, note = "Money starts on Mon 16 Nov. Until then it is only recorded.")
        rule.onNodeWithText("Recording").assertExists(); rule.onNodeWithText("Money starts on Mon 16 Nov. Until then it is only recorded.").assertExists()
    }

    @Test fun R2_home_says_when_earned_phone_access_is_open() {
        home(null, earned = true); rule.onNodeWithText("PHONE CLOSED, EARNED").assertExists()
    }

    private class Fake(val lines: List<String>, val note: String? = null) : BlockActions {
        override suspend fun load(ds: DatedSlot): BlockRow? = BlockRow(key = blockKey(ds), title = ds.slot.title, type = ds.slot.type, startMs = 1, endMs = 2, zone = LONDON.id, verified = true, creditedMin = 125, provenAtMs = 3)
        override suspend fun recordHold(ds: DatedSlot, atMs: Long) {}
        override suspend fun submitPhoto(ds: DatedSlot, jpeg: ByteArray, now: ZonedDateTime, fix: Fix?) = Verdict(true, Reason.OK, 125)
        override suspend fun checkCommit(ds: DatedSlot, now: ZonedDateTime) = Verdict(true, Reason.OK, 125)
        override suspend fun currentFix(): Fix? = null
        override fun hasLocationPermission() = false
        override suspend fun rewardLines(ds: DatedSlot, creditedMin: Int) = lines
        override suspend fun conversionNote(ds: DatedSlot) = note
    }

    @Test fun R1_R2_R3_a_verified_block_shows_what_it_kept_out_of_the_Buffer_the_phone_it_earned_and_the_next_reward() {
        val ds = slotsFor(slots, monday).first { it.slot.type == "study-standard" }
        rule.setContent { EfaTheme { BlockScreen(ds, Fake(listOf("This kept 2.5h out of the Buffer.", "Earned 20 minutes of open phone for transitions and planning.", "Next: Lunch at 12:25.")), onBack = {}) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("This kept 2.5h out of the Buffer.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Earned 20 minutes of open phone for transitions and planning.").assertExists(); rule.onNodeWithText("Next: Lunch at 12:25.").assertExists()
    }

    @Test fun F2_F4_a_converted_block_says_why_and_offers_the_wall_to_turn_it_back() {
        val conv = PatternSlot(DayOfWeek.MONDAY, 1155, 1335, CONVERTED_TITLE, "study", "", false, CONVERTED_NOTE, date = monday)
        val ds = slotsFor(listOf(conv), monday).single()
        var turned = false
        val future = ds.copy(start = ZonedDateTime.now(LONDON).plusDays(1), end = ZonedDateTime.now(LONDON).plusDays(1).plusHours(2))
        val actions = object : BlockActions by Fake(emptyList(), "Buffer +1.0h is under its margin, so free time on 2026-11-02 became work. A free slot is assigned the night before.") {
            override suspend fun load(ds: DatedSlot): BlockRow? = null
        }
        rule.setContent { EfaTheme { BlockScreen(future, actions, onTurnBack = { turned = true }, onBack = {}) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("WHY THIS IS WORK").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Buffer +1.0h is under its margin", substring = true).assertExists()
        rule.onNodeWithText("Make it a break: type 75 characters").performClick(); assertTrue(turned)
    }
}

/** Earned access and the free-slot alarm on the real phone's blocking and notification code. */
@RunWith(AndroidJUnit4::class)
class RewardsE2ETest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db get() = EfaDb.get(ctx)
    private val chrome = "com.android.chrome"
    private var savedList: String? = null
    private val wed = LocalDate.of(2026, 11, 4)

    @Before fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant app.efa android.permission.POST_NOTIFICATIONS").close()
        runBlocking {
            db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
            savedList = db.meta().get(META_BLOCKLIST); BlockingRepo(db).saveList(setOf(chrome))
        }
        listOf("allowance", "block", "morning", "overlay_item", "task").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        db.openHelper.writableDatabase.execSQL("INSERT INTO overlay_item (date,startMin,endMin,title,type,venue,notes,hardAlarm,cancelled,kind,rowHash,importId,reason,createdMs) VALUES ('2026-11-04',1020,1050,'Test walk','travel','','',0,0,'import','t1',NULL,'',1)")
    }

    @After fun tearDown() {
        BlockingState.clockOverride = null
        listOf("allowance", "block", "morning", "overlay_item", "task").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        runBlocking { if (savedList == null) db.openHelper.writableDatabase.execSQL("DELETE FROM meta WHERE key = 'blocklist'") else db.meta().put(MetaRow(META_BLOCKLIST, savedList!!)); BlockingState.refresh(ctx) }
    }

    private fun clockAt(zdt: ZonedDateTime) { BlockingState.clockOverride = { zdt }; runBlocking { BlockingState.refresh(ctx) } }
    private fun earn(minutes: Int, at: ZonedDateTime) = db.openHelper.writableDatabase.execSQL(
        "INSERT INTO block (`key`,title,type,startMs,endMs,zone,verified,creditedMin,recovered,weaker,provenAtMs) VALUES ('k${at.toInstant().toEpochMilli()}','Study','study',1,2,'Europe/London',1,$minutes,0,0,${at.toInstant().toEpochMilli()})")

    @Test fun R2_a_proven_block_opens_the_transition_for_the_minutes_it_earned_and_then_it_closes_again() {
        clockAt(z(11, 4, 17, 5)); assertTrue(BlockingState.decide(chrome) is Decision.Block, "no earnings, so the walk closes the list")
        earn(120, z(11, 4, 16, 50)); clockAt(z(11, 4, 17, 5))             // earned after the walks of the afternoon, so the bank is still full at 17:00
        assertEquals(Decision.Allow, BlockingState.decide(chrome), "120 minutes of work is 20 minutes of open phone. earnings=${BlockingState.snapshot?.earnings} now=${BlockingState.now()}")
        clockAt(z(11, 4, 17, 25)); assertTrue(BlockingState.decide(chrome) is Decision.Block, "the 20 minutes are spent")
    }

    @Test fun Y8_R5_earned_access_never_opens_the_night_cutoff() {
        earn(600, z(11, 4, 15))
        val wake = nextWake(WeekB.slots, z(11, 4, 12))!!
        clockAt(nightCutoff(wake).plusMinutes(25))
        val d = BlockingState.decide(chrome); assertTrue(d is Decision.Block && d.reason == BlockReason.NIGHT, d.toString())
    }

    @Test fun Y8_earned_access_never_opens_a_work_block() {
        earn(600, z(11, 4, 8))
        val study = slotsFor(WeekB.slots, wed).first { it.slot.type == "study-standard" }
        clockAt(study.start.plusMinutes(5))
        val d = BlockingState.decide(chrome); assertTrue(d is Decision.Block && d.reason == BlockReason.WORK_BLOCK, d.toString())
    }

    @Test fun F1_the_end_of_a_free_slot_sounds_an_alarm_that_says_what_is_next() {
        val nm = ctx.getSystemService(NotificationManager::class.java); nm.cancel(FreeSlotAlarm.NOTIFICATION_ID)
        assertTrue(FreeSlotAlarm.schedule(ctx, System.currentTimeMillis() + 3_000, "Next: Lecture at 18:15."))
        val end = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < end && nm.activeNotifications.none { it.id == FreeSlotAlarm.NOTIFICATION_ID }) Thread.sleep(500)
        val n = nm.activeNotifications.firstOrNull { it.id == FreeSlotAlarm.NOTIFICATION_ID } ?: fail("the free slot alarm did not sound")
        assertEquals("Free time is over", n.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
        assertEquals("Next: Lecture at 18:15.", n.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
        nm.cancel(FreeSlotAlarm.NOTIFICATION_ID)
    }

    @Test fun X7_a_free_slot_alarm_for_a_time_already_past_is_never_set() {
        assertFalse(FreeSlotAlarm.schedule(ctx, System.currentTimeMillis() - 1000, "x"))
    }
}
