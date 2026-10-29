package app.efa

import android.app.AlarmManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
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

/** Stored mornings: ringing, the stops, the missed flag. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class MorningRepoTest {
    private lateinit var db: EfaDb
    private lateinit var repo: MorningRepo
    private val alarmAt = at(LocalDate.of(2026, 11, 4), LocalTime.of(7, 30)).toInstant().toEpochMilli()
    private val day = LocalDate.of(2026, 11, 4)

    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build(); repo = MorningRepo(db) }
    @After fun close() = db.close()

    @Test fun M5_firing_starts_the_ring_once() = runBlocking {
        val a = repo.fire(alarmAt, "ONSET", alarmAt + 1_000); val b = repo.fire(alarmAt, "ONSET", alarmAt + 90_000)
        assertEquals(alarmAt + 1_000, a.ringStartedMs); assertEquals(a.ringStartedMs, b.ringStartedMs); assertEquals("2026-11-04", b.date)
    }

    @Test fun M10_failed_photos_are_counted_and_the_best_score_is_kept() = runBlocking {
        repo.fire(alarmAt, "ONSET", alarmAt)
        repo.failedPhoto(day, 41); repo.failedPhoto(day, 12)
        val r = repo.get(day)!!; assertEquals(2, r.failedPhotos); assertEquals(41, r.bestScore)
    }

    @Test fun M10_skipping_to_steps_uses_up_the_photo_attempts() = runBlocking {
        repo.fire(alarmAt, "ONSET", alarmAt); repo.skipToSteps(day)
        assertEquals(PHOTO_ATTEMPTS, repo.get(day)!!.failedPhotos)
        repo.failedPhoto(day, 0); repo.skipToSteps(day); assertEquals(PHOTO_ATTEMPTS + 1, repo.get(day)!!.failedPhotos)
    }

    @Test fun M2_steps_only_go_up_and_the_wait_starts_once() = runBlocking {
        repo.fire(alarmAt, "ONSET", alarmAt)
        repo.setSteps(day, 30); repo.setSteps(day, 12); assertEquals(30, repo.get(day)!!.steps)
        repo.startWait(day, 5_000); repo.startWait(day, 9_000); assertEquals(5_000L, repo.get(day)!!.waitStartedMs)
    }

    @Test fun M2_M10_the_first_stop_is_the_one_that_is_kept_and_says_how_it_happened() = runBlocking {
        repo.fire(alarmAt, "ONSET", alarmAt)
        repo.stop(day, StopMethod.PHOTO, 88, alarmAt + 60_000); repo.stop(day, StopMethod.WAIT, 0, alarmAt + 120_000)
        val r = repo.get(day)!!; assertEquals(alarmAt + 60_000, r.stoppedMs); assertEquals("PHOTO", r.stopMethod); assertEquals(88, r.bestScore)
        assertEquals(StopMethod.PHOTO, r.toState().stopMethod)
    }

    @Test fun M8_the_ready_photo_is_recorded_once() = runBlocking {
        repo.fire(alarmAt, "ONSET", alarmAt); repo.stop(day, StopMethod.PHOTO, 80, alarmAt + 1000)
        repo.ready(day, StopMethod.PHOTO, alarmAt + 600_000); repo.ready(day, StopMethod.WAIT, alarmAt + 900_000)
        assertEquals(alarmAt + 600_000, repo.get(day)!!.readyMs); assertEquals(StopMethod.PHOTO, repo.get(day)!!.toState().readyMethod)
    }

    @Test fun X2_a_morning_the_phone_missed_is_flagged_once_and_never_blocks() = runBlocking {
        assertTrue(repo.markMissed(alarmAt, "BACKSTOP")); assertFalse(repo.markMissed(alarmAt, "BACKSTOP"))
        val r = repo.get(day)!!; assertTrue(r.missed); assertNull(r.ringStartedMs)
        assertEquals(MorningPhase.NONE, morningPhase(r.toState(), at(day, LocalTime.of(9, 0))), "a missed morning starts no limit and no social block")
    }

    @Test fun X2_a_morning_that_rang_is_not_overwritten_as_missed() = runBlocking {
        repo.fire(alarmAt, "ONSET", alarmAt); assertFalse(repo.markMissed(alarmAt, "ONSET")); assertFalse(repo.get(day)!!.missed)
    }

    @Test fun M5_the_latest_morning_is_the_one_that_counts() = runBlocking {
        repo.fire(alarmAt, "ONSET", alarmAt); repo.fire(alarmAt + 86_400_000, "BACKSTOP", alarmAt + 86_400_000)
        assertEquals("2026-11-05", repo.latest()!!.date)
    }
}

/** The alarm screen's four offers (M2, M10). */
@RunWith(AndroidJUnit4::class)
class AlarmScreenTest {
    @get:Rule val rule = createComposeRule()
    private fun show(offer: Offer, message: String? = null, secs: Int? = null, photo: () -> Unit = {}, broken: () -> Unit = {}, stop: (StopMethod) -> Unit = {}) =
        rule.setContent { EfaTheme { AlarmScreen(AlarmUi("07:30", offer, message, secs), photo, broken, stop) } }

    @Test fun M2_the_first_offer_is_the_photo_and_the_clock_is_shown() {
        var took = false
        show(Offer.Photo(1), photo = { took = true })
        rule.onNodeWithText("07:30").assertExists(); rule.onNodeWithText("Take the photo of your setup to stop the alarm.").assertExists(); rule.onNodeWithText("Attempt 1 of 2.").assertExists()
        rule.onNodeWithText("Take the photo").performClick(); assertTrue(took)
        rule.onNodeWithText("Stop the alarm").assertDoesNotExist()
    }

    @Test fun M10_the_second_attempt_says_to_try_again_and_shows_why_the_last_one_failed() {
        show(Offer.Photo(2), "That does not match your setup (score 31, it needs 65).")
        rule.onNodeWithText("Attempt 2 of 2.").assertExists(); rule.onNodeWithText("That does not match your setup (score 31, it needs 65).").assertExists()
    }

    @Test fun M10_a_camera_that_does_not_work_can_be_said_so() {
        var broken = false
        show(Offer.Photo(1), broken = { broken = true }); rule.onNodeWithText("The camera is not working").performClick(); assertTrue(broken)
    }

    @Test fun M2_M3_the_steps_rung_shows_progress_and_calls_itself_the_weaker_check_with_no_stop_button() {
        show(Offer.Steps(25, STEPS_REQUIRED))
        rule.onNodeWithText("Walk 40 steps to stop the alarm.").assertExists(); rule.onNodeWithText("25 of 40 steps. This is the weaker check, so it is marked.").assertExists()
        rule.onNodeWithText("Stop the alarm").assertDoesNotExist(); rule.onNodeWithText("Take the photo").assertDoesNotExist()
    }

    @Test fun M10_the_wait_rung_counts_down() {
        show(Offer.Wait(0), secs = 179)
        rule.onNodeWithText("The alarm can be stopped in 2:59.").assertExists(); rule.onNodeWithText("Stop the alarm").assertDoesNotExist()
    }

    @Test fun M10_when_a_weaker_check_is_done_the_stop_button_appears_and_passes_its_method() {
        var method: StopMethod? = null
        show(Offer.CanStop(StopMethod.STEPS), stop = { method = it })
        rule.onNodeWithText("Steps done.").assertExists(); rule.onNodeWithText("Stop the alarm").performClick(); assertEquals(StopMethod.STEPS, method)
    }

    @Test fun M10_calls_and_messages_are_never_locked() {
        show(Offer.Photo(1)); rule.onNodeWithText("Calls and messages still work.").assertExists()
    }
}

/** The alarm manager side: one alarm is always set, it moves with sleep onset, and a restart rebuilds it. */
@RunWith(AndroidJUnit4::class)
class AlarmSchedulerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db get() = EfaDb.get(ctx)
    private val am get() = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Before fun clean() {
        listOf("sensor_event", "morning").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        runBlocking { db.seedIfEmpty(System.currentTimeMillis(), LONDON.id) }
    }
    @After fun restore() { BlockingState.clockOverride = null; runBlocking { AlarmScheduler.rebuild(ctx, java.time.ZonedDateTime.now(LONDON)) } }

    private fun inHours(h: Long) = java.time.ZonedDateTime.now(LONDON).plusHours(h)

    @Test fun G2_with_no_onset_an_alarm_is_set_at_the_backstop() = runBlocking {
        AlarmScheduler.rebuild(ctx, java.time.ZonedDateTime.now(LONDON))
        val set = am.nextAlarmClock?.triggerTime ?: fail("no alarm is set")
        val z = java.time.Instant.ofEpochMilli(set).atZone(LONDON)
        assertTrue(set > System.currentTimeMillis()); assertEquals(LocalTime.NOON, z.toLocalTime())
    }

    @Test fun Z1_applying_a_plan_sets_exactly_that_time_and_the_same_plan_again_changes_nothing() = runBlocking {
        val plan = AlarmPlan(inHours(5).withSecond(0).withNano(0), AlarmReason.ONSET)
        assertTrue(AlarmScheduler.apply(ctx, plan)); assertEquals(plan.at.toInstant().toEpochMilli(), am.nextAlarmClock.triggerTime)
        assertFalse(AlarmScheduler.apply(ctx, plan))
        assertTrue(AlarmScheduler.apply(ctx, plan.copy(at = plan.at.plusMinutes(5)))); assertEquals(plan.at.plusMinutes(5).toInstant().toEpochMilli(), am.nextAlarmClock.triggerTime)
    }

    @Test fun Z1_less_than_a_minute_of_difference_is_not_rescheduled() = runBlocking {
        val plan = AlarmPlan(inHours(5).withSecond(0).withNano(0), AlarmReason.ONSET)
        AlarmScheduler.apply(ctx, plan); assertFalse(AlarmScheduler.apply(ctx, plan.copy(at = plan.at.plusSeconds(40))))
    }

    @Test fun X7_an_alarm_in_the_past_is_never_set_because_Android_would_ring_it_at_once() = runBlocking {
        val before = am.nextAlarmClock?.triggerTime
        assertFalse(AlarmScheduler.apply(ctx, AlarmPlan(java.time.ZonedDateTime.now(LONDON).minusMinutes(5), AlarmReason.ONSET)))
        assertEquals(before, am.nextAlarmClock?.triggerTime)
    }

    @Test fun Z1_the_live_night_moves_the_alarm_to_onset_plus_8_hours() = runBlocking {
        val now = java.time.ZonedDateTime.now(LONDON).withHour(2).withMinute(0).withSecond(0).withNano(0).let { if (it.isAfter(java.time.ZonedDateTime.now(LONDON))) it.minusDays(1) else it }
        // A night that started at 22:30 the evening before the "now" below, with the screen off since.
        val evening = now.minusHours(3).withMinute(30)
        val repo = SleepRepo(db)
        repo.record(SensorKind.SCREEN_ON, 0, evening.minusHours(1).toInstant().toEpochMilli()); repo.record(SensorKind.SCREEN_OFF, 0, evening.toInstant().toEpochMilli())
        val onset = AlarmScheduler.liveOnset(repo, now.plusSeconds(1))
        assertEquals(evening.toInstant(), onset?.toInstant(), "sleep started when the screen went off for the quiet stretch")
        val plan = planAlarm(now, onset, PlanRepo(db).slots())!!
        assertEquals(evening.plusHours(8).toInstant(), plan.at.toInstant()); assertEquals(AlarmReason.ONSET, plan.reason)
    }

    @Test fun Z1_a_person_who_is_already_up_has_no_onset() = runBlocking {
        val now = java.time.ZonedDateTime.now(LONDON).withHour(8).withMinute(0)
        val repo = SleepRepo(db)
        repo.record(SensorKind.SCREEN_OFF, 0, now.minusHours(9).toInstant().toEpochMilli()); repo.record(SensorKind.SCREEN_ON, 0, now.minusHours(1).toInstant().toEpochMilli())
        assertNull(AlarmScheduler.liveOnset(repo, now), "the night is over, so tonight's alarm is the backstop")
    }

    @Test fun X2_after_a_restart_an_alarm_that_passed_while_the_phone_was_off_is_flagged_missed_with_no_penalty_and_a_new_alarm_is_set() = runBlocking {
        val past = System.currentTimeMillis() - 2 * 3_600_000
        db.meta().put(MetaRow("alarm_at_ms", past.toString())); db.meta().put(MetaRow("alarm_reason", "ONSET"))
        AlarmScheduler.rebuild(ctx, java.time.ZonedDateTime.now(LONDON))
        val m = db.mornings().get(MorningRepo(db).dateOf(past).toString())!!
        assertTrue(m.missed); assertNull(m.ringStartedMs); assertEquals(MorningPhase.NONE, morningPhase(m.toState(), java.time.ZonedDateTime.now(LONDON)))
        assertTrue((am.nextAlarmClock?.triggerTime ?: 0) > System.currentTimeMillis())
    }

    @Test fun X2_a_restart_before_the_alarm_time_just_sets_it_again() = runBlocking {
        AlarmScheduler.rebuild(ctx, java.time.ZonedDateTime.now(LONDON)); val first = am.nextAlarmClock.triggerTime
        AlarmScheduler.rebuild(ctx, java.time.ZonedDateTime.now(LONDON))
        assertEquals(first, am.nextAlarmClock.triggerTime); assertNull(db.mornings().latest())
    }
}

/**
 * The whole alarm on the emulator: it rings, the screen comes to the front, the real camera is used, and the stop is recorded. Everything is
 * driven through the accessibility tree, with no Compose test rule, because that rule takes over the clock of every Compose screen in the process.
 */
@RunWith(AndroidJUnit4::class)
class AlarmE2ETest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val screen = TestScreen(inst)
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db get() = EfaDb.get(ctx)

    companion object {
        @JvmStatic @org.junit.BeforeClass fun gateOff() { SetupGate.enforced = false }
        @JvmStatic @org.junit.AfterClass fun gateOn() { SetupGate.enforced = true }
    }

    @Before fun setUp() {
        screen.shell("pm grant app.efa android.permission.CAMERA"); screen.shell("appops set app.efa USE_FULL_SCREEN_INTENT allow")
        screen.shell("settings put secure enabled_accessibility_services app.efa/app.efa.BlockerService"); screen.shell("settings put secure accessibility_enabled 1")
        listOf("morning", "reference_photo").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        screen.shell("input keyevent KEYCODE_WAKEUP"); screen.shell("wm dismiss-keyguard"); Thread.sleep(2_500)
    }

    @After fun tearDown() {
        AlarmService.stop(ctx); AlarmSession.ringing = false
        listOf("morning", "reference_photo").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        screen.shell("settings put secure enabled_accessibility_services \"\""); screen.home()
        runBlocking { AlarmScheduler.rebuild(ctx, java.time.ZonedDateTime.now(LONDON)) }
    }

    /** Sets the night-before reference with the real camera, through the screens a person would use. */
    private fun setReferenceFromCamera() {
        ctx.startActivity(android.content.Intent(ctx, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
        assertTrue(screen.waitFor(20_000) { screen.has("Type to open") }, "home. Screen: " + screen.dump()); assertTrue(screen.scrollTo("Setup: home"), "no Setup chip"); screen.clickExact("Setup: home, venues, blocking and GitHub")
        assertTrue(screen.waitFor(10_000) { screen.has("Import a timetable file") || screen.has("BLOCKING") }, "setup. Screen: " + screen.dump()); assertTrue(screen.scrollTo("Set up mornings"), "no morning button. Screen: " + screen.dump()); screen.clickExact("Set up mornings")
        assertTrue(screen.waitFor(10_000) { screen.has("TONIGHT") }, "morning. Screen: " + screen.dump()); assertTrue(screen.scrollTo("Take tonight's setup photo")); screen.clickExact("Take tonight's setup photo")
        assertTrue(screen.waitFor(30_000) { screen.has("Take photo") }, "camera. Screen: " + screen.dump()); screen.clickExact("Take photo")
        assertTrue(screen.waitFor(30_000) { screen.has("Saved. It replaces the old one.") }, "saved. Screen: " + screen.dump())
        screen.home(); Thread.sleep(1_000)
    }

    private fun ring() {
        val at = java.time.ZonedDateTime.now(LONDON).plusSeconds(4)
        runBlocking { AlarmScheduler.apply(ctx, AlarmPlan(at, AlarmReason.BACKSTOP)) }
    }

    @Test fun M2_M5_the_alarm_rings_at_its_time_brings_its_screen_up_and_a_matching_photo_stops_it() {
        setReferenceFromCamera()
        ring()
        assertTrue(screen.waitFor(40_000) { screen.has("Take the photo of your setup") }, "the alarm screen did not come up. Screen: " + screen.dump())
        assertTrue(AlarmSession.ringing); val row = runBlocking { db.mornings().latest() }!!; assertNotNull(row.ringStartedMs); assertNull(row.stoppedMs)
        screen.clickExact("Take the photo")
        assertTrue(screen.waitFor(30_000) { screen.has("Take photo") }, "the camera did not open. Screen: " + screen.dump())
        screen.clickExact("Take photo")
        assertTrue(screen.waitFor(40_000) { runBlocking { db.mornings().latest()?.stoppedMs } != null }, "the alarm did not stop. Screen: " + screen.dump())
        val stopped = runBlocking { db.mornings().latest() }!!
        assertEquals("PHOTO", stopped.stopMethod); assertTrue(stopped.bestScore >= MATCH_PASS_SCORE, "score ${stopped.bestScore}")
        assertTrue(screen.waitFor(10_000) { !AlarmSession.ringing }, "the sound must stop")
    }

    @Test fun M10_two_photos_that_do_not_match_lead_to_the_weaker_checks_and_the_alarm_keeps_ringing() {
        runBlocking { ReferenceRepo(ctx, db).set(RefKind.NIGHT, roomJpeg(77), System.currentTimeMillis()) }          // a room the emulator camera is not looking at
        ring()
        assertTrue(screen.waitFor(40_000) { screen.has("Take the photo of your setup") }, "no alarm screen. Screen: " + screen.dump())
        repeat(2) { attempt ->
            screen.clickExact("Take the photo")
            assertTrue(screen.waitFor(30_000) { screen.has("Take photo") }, "camera $attempt. Screen: " + screen.dump())
            screen.clickExact("Take photo")
            assertTrue(screen.waitFor(30_000) { screen.has("does not match") || screen.has("Walk 40 steps") || screen.has("can be stopped in") }, "no verdict $attempt. Screen: " + screen.dump())
        }
        assertTrue(screen.waitFor(10_000) { screen.has("Walk 40 steps") || screen.has("can be stopped in") }, "no weaker rung. Screen: " + screen.dump())
        val row = runBlocking { db.mornings().latest() }!!
        assertEquals(PHOTO_ATTEMPTS, row.failedPhotos); assertNull(row.stoppedMs); assertTrue(AlarmSession.ringing, "a failed photo must not silence the alarm")
        assertFalse(screen.has("Take the photo"), "no third photo attempt")
    }

    @Test fun M10_with_no_reference_photo_the_alarm_goes_straight_to_the_weaker_checks() {
        ring()
        assertTrue(screen.waitFor(40_000) { screen.has("Walk 40 steps") || screen.has("can be stopped in") }, "Screen: " + screen.dump())
        assertFalse(screen.has("Take the photo of your setup"))
    }

    @Test fun M5_the_back_button_does_not_leave_the_alarm() {
        runBlocking { ReferenceRepo(ctx, db).set(RefKind.NIGHT, roomJpeg(78), System.currentTimeMillis()) }
        ring()
        assertTrue(screen.waitFor(40_000) { screen.has("Take the photo of your setup") }, "foreground=${screen.foreground}. Screen: " + screen.dump())
        screen.back(); Thread.sleep(1_500)
        assertTrue(screen.has("Take the photo of your setup"), "Screen: " + screen.dump())
    }
}

/** The alarm activity itself, started directly with a ringing morning in the database. */
@RunWith(AndroidJUnit4::class)
class AlarmActivityTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createEmptyComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db get() = EfaDb.get(ctx)

    @Before fun ring() {
        listOf("morning", "reference_photo").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        runBlocking { ReferenceRepo(ctx, db).set(RefKind.NIGHT, roomJpeg(5), System.currentTimeMillis()); MorningRepo(db).fire(System.currentTimeMillis(), "ONSET", System.currentTimeMillis()) }
    }
    @After fun quiet() { listOf("morning", "reference_photo").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") } }

    @Test fun M5_the_alarm_activity_shows_the_photo_offer_for_a_ringing_morning() {
        androidx.test.core.app.ActivityScenario.launch<AlarmActivity>(android.content.Intent(ctx, AlarmActivity::class.java)).use {
            try { rule.waitUntil(15_000) { rule.onAllNodesWithText("Take the photo of your setup to stop the alarm.").fetchSemanticsNodes().isNotEmpty() } }
            catch (e: Throwable) { throw AssertionError("not drawn. " + rule.onRoot().printToString(3000), e) }
            rule.onNodeWithText("Attempt 1 of 2.").assertExists()
        }
    }

    @Test fun M5_an_activity_opened_when_nothing_is_ringing_closes_itself() {
        db.openHelper.writableDatabase.execSQL("DELETE FROM morning")
        androidx.test.core.app.ActivityScenario.launch<AlarmActivity>(android.content.Intent(ctx, AlarmActivity::class.java)).use { sc ->
            val end = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < end && sc.state != androidx.lifecycle.Lifecycle.State.DESTROYED) Thread.sleep(200)
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, sc.state)
        }
    }
}
