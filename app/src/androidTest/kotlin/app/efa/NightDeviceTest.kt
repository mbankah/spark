package app.efa

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.efa.data.*
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

private fun ms(m: Int, d: Int, h: Int, min: Int = 0) = at(LocalDate.of(2026, m, d), LocalTime.of(h, min)).toInstant().toEpochMilli()

/** The night layer's storage: late mornings for the wake drift (Z3), and the two photos that bound a night (S1, S2). */
@RunWith(AndroidJUnit4::class)
class NightStorageTest {
    private lateinit var db: EfaDb
    private lateinit var ctx: Context
    private val slots = WeekB.slots

    @Before fun open() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, EfaDb::class.java).build()
    }
    @After fun close() = db.close()

    private suspend fun morning(date: LocalDate, stopped: ZonedDateTime) {
        val r = MorningRepo(db); val at = stopped.minusMinutes(3).toInstant().toEpochMilli()
        r.fire(at, "ONSET", at); r.stop(date, StopMethod.PHOTO, 90, stopped.toInstant().toEpochMilli())
    }

    @Test fun Z3_only_mornings_that_were_later_than_the_planned_wake_are_listed_with_how_late() = runBlocking {
        val tue = LocalDate.of(2026, 11, 3); val wed = tue.plusDays(1)
        val planned = slotsFor(slots, tue).first { it.slot.title == "Wake, shower" }.start
        morning(tue, planned.plusMinutes(47))
        morning(wed, slotsFor(slots, wed).first { it.slot.title == "Wake, shower" }.start.minusMinutes(5))
        assertEquals(mapOf(tue to 47), MorningRepo(db).lateness(slots, wed.plusDays(1)))
    }

    @Test fun Z3_a_morning_with_no_alarm_stop_or_no_planned_wake_has_no_lateness() = runBlocking {
        val tue = LocalDate.of(2026, 11, 3)
        val r = MorningRepo(db); r.fire(ms(11, 3, 7, 30), "ONSET", ms(11, 3, 7, 30))          // still ringing: not stopped
        assertTrue(r.lateness(slots, tue.plusDays(1)).isEmpty())
        val bare = emptyList<PatternSlot>()
        r.stop(tue, StopMethod.PHOTO, 80, ms(11, 3, 11, 0)); assertTrue(r.lateness(bare, tue.plusDays(1)).isEmpty(), "a free morning has no planned wake to be late for")
    }

    @Test fun Z3_mornings_older_than_14_days_are_not_read() = runBlocking {
        val old = LocalDate.of(2026, 10, 20)
        val planned = slotsFor(slots, old).first { it.slot.title == "Wake, shower" }.start
        morning(old, planned.plusMinutes(90))
        assertTrue(MorningRepo(db).lateness(slots, LocalDate.of(2026, 11, 20)).isEmpty())
        assertEquals(mapOf(old to 90), MorningRepo(db).lateness(slots, LocalDate.of(2026, 10, 25)))
    }

    private suspend fun sleepNight(repo: SleepRepo) {
        repo.record(SensorKind.SCREEN_OFF, 0, ms(11, 3, 19)); repo.record(SensorKind.SCREEN_ON, 0, ms(11, 3, 21))        // a long evening stretch
        repo.record(SensorKind.SCREEN_OFF, 0, ms(11, 3, 23, 40)); repo.record(SensorKind.SCREEN_ON, 0, ms(11, 4, 7, 10))
    }
    private val noon = at(LocalDate.of(2026, 11, 4), LocalTime.of(13, 0)).toInstant()

    @Test fun S2_the_night_before_photo_is_the_shutdown_photo_and_sleep_starts_after_it() = runBlocking {
        val repo = SleepRepo(db); sleepNight(repo)
        ReferenceRepo(ctx, db).set(RefKind.NIGHT, roomJpeg(3), ms(11, 3, 21, 30))
        repo.rollup(noon)
        val n = repo.latestNight()!!; assertEquals(ms(11, 3, 23, 40), n.startMs); assertEquals(450, n.sleepMin)
    }

    @Test fun S1_with_no_shutdown_photo_the_sensors_alone_still_give_a_night_and_nothing_is_penalised() = runBlocking {
        val repo = SleepRepo(db); sleepNight(repo)
        repo.rollup(noon)
        assertEquals(ms(11, 3, 19), repo.latestNight()!!.startMs)
    }

    @Test fun S2_a_photo_taken_in_the_morning_is_not_a_shutdown_photo() = runBlocking {
        val repo = SleepRepo(db); sleepNight(repo)
        ReferenceRepo(ctx, db).set(RefKind.NIGHT, roomJpeg(3), ms(11, 4, 7, 5))
        repo.rollup(noon); assertEquals(ms(11, 3, 19), repo.latestNight()!!.startMs)
    }

    @Test fun S2_S4_the_photo_that_stops_the_alarm_is_the_wake_photo_and_bounds_the_end() = runBlocking {
        val repo = SleepRepo(db)
        repo.record(SensorKind.SCREEN_OFF, 0, ms(11, 3, 23, 30))                 // no screen-on at all in the morning
        MorningRepo(db).let { val at = ms(11, 4, 7, 30); it.fire(at, "ONSET", at); it.stop(LocalDate.of(2026, 11, 4), StopMethod.PHOTO, 90, ms(11, 4, 7, 33)) }
        repo.rollup(noon)
        val n = repo.latestNight()!!; assertEquals(ms(11, 4, 7, 33), n.endMs); assertEquals(483, n.sleepMin)
    }

    @Test fun Z1_S2_the_live_onset_for_the_alarm_ignores_a_stretch_before_the_shutdown_photo() = runBlocking {
        val repo = SleepRepo(db)
        val now = ZonedDateTime.now(LONDON).withHour(3).withMinute(0).withSecond(0).withNano(0).let { if (it.isAfter(ZonedDateTime.now(LONDON))) it.minusDays(1) else it }
        val evening = now.minusDays(0).minusHours(8)                              // 19:00 the evening before
        repo.record(SensorKind.SCREEN_OFF, 0, evening.toInstant().toEpochMilli()); repo.record(SensorKind.SCREEN_ON, 0, evening.plusHours(2).toInstant().toEpochMilli())
        repo.record(SensorKind.SCREEN_OFF, 0, evening.plusHours(4).plusMinutes(40).toInstant().toEpochMilli())     // 23:40
        ReferenceRepo(ctx, db).set(RefKind.NIGHT, roomJpeg(3), evening.plusHours(2).plusMinutes(30).toInstant().toEpochMilli())
        val onset = AlarmScheduler.liveOnset(repo, now.plusSeconds(1))
        assertEquals(evening.plusHours(4).plusMinutes(40).toInstant(), onset?.toInstant(), "sleep started at 23:40, after the shutdown photo")
    }
}

/** The night card on the real phone: the earned 45 minutes (N5) and the drifted cutoff (Z3). */
@RunWith(AndroidJUnit4::class)
class NightE2ETest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val screen = TestScreen(inst)
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db get() = EfaDb.get(ctx)
    private val chrome = "com.android.chrome"
    private val slots = WeekB.slots
    private var savedList: String? = null
    private val monday = LocalDate.of(2026, 11, 2)

    @Before fun setUp() {
        runBlocking {
            db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
            savedList = db.meta().get(META_BLOCKLIST); BlockingRepo(db).saveList(setOf(chrome))
        }
        listOf("allowance", "block", "morning", "tamper").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        screen.shell("settings put secure enabled_accessibility_services app.efa/app.efa.BlockerService"); screen.shell("settings put secure accessibility_enabled 1")
        screen.shell("am force-stop $chrome"); screen.shell("input keyevent KEYCODE_WAKEUP"); screen.shell("wm dismiss-keyguard"); screen.home(); Thread.sleep(2_000)
    }

    @After fun tearDown() {
        BlockingState.clockOverride = null
        listOf("allowance", "block", "morning", "tamper").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        runBlocking { if (savedList == null) db.openHelper.writableDatabase.execSQL("DELETE FROM meta WHERE key = 'blocklist'") else db.meta().put(MetaRow(META_BLOCKLIST, savedList!!)); BlockingState.refresh(ctx) }
        screen.shell("settings put secure enabled_accessibility_services \"\""); screen.shell("am force-stop $chrome"); screen.home()
    }

    private fun verifyWholeDay() = runBlocking {
        for (ds in slotsFor(slots, monday).filter { proofFor(it.slot) != Proof.NONE })
            db.blocks().upsert(BlockRow(key = blockKey(ds), title = ds.slot.title, type = ds.slot.type, startMs = ds.start.toInstant().toEpochMilli(), endMs = ds.end.toInstant().toEpochMilli(),
                zone = LONDON.id, verified = true, creditedMin = 60, provenAtMs = ds.end.toInstant().toEpochMilli()))
    }

    private fun tuesdayWake() = nextWake(slots, at(monday, LocalTime.of(12, 0)))!!
    private fun clockAt(z: ZonedDateTime) { BlockingState.clockOverride = { z }; runBlocking { BlockingState.refresh(ctx) } }
    private fun openChromeAndWaitForCard() {
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(chrome)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(screen.waitFor { screen.has("is blocked") }, "no card. Screen: " + screen.dump())
    }

    @Test fun N5_after_a_day_with_every_proof_the_override_card_offers_45_minutes() {
        verifyWholeDay()
        clockAt(nightCutoff(tuesdayWake()).plusMinutes(15))
        assertTrue(BlockingState.fullyVerifiedTonight())
        openChromeAndWaitForCard()
        assertTrue(screen.has("for 45 minutes"), "Screen: " + screen.dump())
    }

    @Test fun N3_N5_after_an_ordinary_day_the_override_card_offers_30_minutes() {
        clockAt(nightCutoff(tuesdayWake()).plusMinutes(15))
        assertFalse(BlockingState.fullyVerifiedTonight())
        openChromeAndWaitForCard()
        assertTrue(screen.has("for 30 minutes"), "Screen: " + screen.dump())
    }

    @Test fun N5_one_missing_proof_takes_the_45_back() {
        verifyWholeDay()
        runBlocking { val k = db.blocks().verifiedKeys().first(); db.openHelper.writableDatabase.execSQL("DELETE FROM block WHERE `key` = '${k.replace("'", "''")}'") }
        clockAt(nightCutoff(tuesdayWake()).plusMinutes(15))
        assertFalse(BlockingState.fullyVerifiedTonight())
    }

    @Test fun Z3_N2_a_late_morning_keeps_the_list_open_for_the_extra_time_and_then_the_night_block_starts() {
        val wake = tuesdayWake()
        val plannedMonday = slotsFor(slots, monday).first { it.slot.title == "Wake, shower" }.start
        runBlocking {                                                              // Monday's alarm was stopped 60 minutes late
            val r = MorningRepo(db); val at = plannedMonday.toInstant().toEpochMilli()
            r.fire(at, "ONSET", at); r.stop(monday, StopMethod.PHOTO, 90, plannedMonday.plusMinutes(60).toInstant().toEpochMilli())
        }
        // 25 minutes after the old cutoff is past the Shutdown block (a sleep block, which closes the list at any time), and 35 minutes before the drifted cutoff.
        clockAt(nightCutoff(wake).plusMinutes(25))
        assertEquals(Decision.Allow, BlockingState.decide(chrome), "25 minutes after the old cutoff, with a 60 minute drift, the night block has not started")
        clockAt(nightCutoff(wake).plusMinutes(65))
        assertTrue(BlockingState.decide(chrome) is Decision.Block, "after the drifted cutoff it has")
        openChromeAndWaitForCard()
        assertTrue(screen.has("Wake ${"%02d:%02d".format(wake.plusMinutes(60).hour, wake.plusMinutes(60).minute)}"), "the 8-hour sum must use the drifted wake. Screen: " + screen.dump())
    }
}
