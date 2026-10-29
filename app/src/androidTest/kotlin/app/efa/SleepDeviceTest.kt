package app.efa

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

private fun ms(m: Int, d: Int, h: Int, min: Int = 0) = at(LocalDate.of(2026, m, d), LocalTime.of(h, min)).toInstant().toEpochMilli()

/** Storage, the nightly summary and the 30-day clean-up for passive sleep logging. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class SleepStorageTest {
    private lateinit var db: EfaDb
    private lateinit var repo: SleepRepo

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
        repo = SleepRepo(db)
    }
    @After fun close() = db.close()

    private suspend fun night(m: Int, d: Int, offH: Int, offMin: Int, nextD: Int, onH: Int, onMin: Int) {
        repo.record(SensorKind.SCREEN_OFF, 0, ms(m, d, offH, offMin)); repo.record(SensorKind.SCREEN_ON, 0, ms(m, nextD, onH, onMin))
    }

    @Test fun S7_a_heartbeat_is_written_only_when_nothing_has_been_for_15_minutes() = runBlocking {
        assertTrue(repo.heartbeat(1_000_000L))
        assertFalse(repo.heartbeat(1_000_000L + 14 * 60_000)); assertTrue(repo.heartbeat(1_000_000L + 15 * 60_000))
        assertEquals(2, db.sensors().count())
    }

    @Test fun S2_S5_a_finished_night_becomes_one_summary_line() = runBlocking {
        night(11, 3, 23, 30, 4, 7, 10)
        repo.record(SensorKind.CHARGING_ON, 0, ms(11, 3, 22, 0)); repo.record(SensorKind.MOTION, 0, ms(11, 4, 2, 0)); repo.record(SensorKind.LIGHT, 0, ms(11, 4, 3, 0))
        assertEquals(1, repo.rollup(at(LocalDate.of(2026, 11, 4), LocalTime.of(13, 0)).toInstant()))
        val n = repo.latestNight()!!
        assertEquals("2026-11-03", n.date); assertEquals(460, n.sleepMin); assertEquals(1, n.motionEvents); assertEquals(100, n.chargingPct); assertEquals(100, n.darkPct)
    }

    @Test fun S2_a_night_that_is_not_over_is_not_summarised_yet() = runBlocking {
        night(11, 3, 23, 30, 4, 7, 10)
        assertEquals(0, repo.rollup(at(LocalDate.of(2026, 11, 4), LocalTime.of(9, 0)).toInstant()))
        assertNull(repo.latestNight())
    }

    @Test fun S2_rolling_up_again_changes_nothing() = runBlocking {
        night(11, 3, 23, 30, 4, 7, 10)
        val now = at(LocalDate.of(2026, 11, 4), LocalTime.of(13, 0)).toInstant()
        repo.rollup(now); val first = db.nights().all(); repo.rollup(now.plusSeconds(60))
        assertEquals(first.map { it.copy(derivedAtMs = 0) }, db.nights().all().map { it.copy(derivedAtMs = 0) })
    }

    @Test fun S3_a_night_check_is_joined_and_taken_off() = runBlocking {
        repo.record(SensorKind.SCREEN_OFF, 0, ms(11, 3, 23, 30)); repo.record(SensorKind.SCREEN_ON, 0, ms(11, 4, 3, 0))
        repo.record(SensorKind.SCREEN_OFF, 0, ms(11, 4, 3, 8)); repo.record(SensorKind.SCREEN_ON, 0, ms(11, 4, 7, 10))
        repo.rollup(at(LocalDate.of(2026, 11, 4), LocalTime.of(13, 0)).toInstant())
        val n = repo.latestNight()!!; assertEquals(1, n.checkCount); assertEquals(8, n.checkMin); assertEquals(452, n.sleepMin)
    }

    @Test fun ST4_raw_events_older_than_30_days_go_and_their_night_summary_stays() = runBlocking {
        night(10, 1, 23, 30, 2, 7, 0)
        night(11, 25, 23, 30, 26, 7, 0)
        repo.rollup(at(LocalDate.of(2026, 10, 5), LocalTime.of(13, 0)).toInstant())                  // the first night is summarised while it is new
        assertEquals("2026-10-01", repo.latestNight()!!.date)
        val later = at(LocalDate.of(2026, 11, 26), LocalTime.of(13, 0)).toInstant()
        repo.rollup(later)
        assertEquals(listOf("2026-10-01", "2026-11-25"), db.nights().all().map { it.date })
        assertEquals(2, db.sensors().count(), "only the recent night's two events are left")
        assertEquals(0, db.sensors().before(later.minus(StoragePolicy.SENSOR_EVENTS_KEEP).toEpochMilli()).size)
    }

    @Test fun ST4_an_old_summary_is_never_replaced_by_a_partial_one() = runBlocking {
        night(10, 1, 23, 30, 2, 7, 0)
        repo.rollup(at(LocalDate.of(2026, 10, 5), LocalTime.of(13, 0)).toInstant())
        val original = repo.latestNight()!!
        repo.record(SensorKind.SCREEN_OFF, 0, ms(10, 1, 22, 0))                       // a stray late row for the same night, long after
        repo.rollup(at(LocalDate.of(2026, 11, 20), LocalTime.of(13, 0)).toInstant())
        assertEquals(original.copy(derivedAtMs = 0), db.nights().get("2026-10-01")!!.copy(derivedAtMs = 0))
    }

    @Test fun X7_a_dropped_write_never_stops_logging() = runBlocking {
        db.close()
        repo.record(SensorKind.SCREEN_ON)                    // the database is closed: the write fails and is swallowed
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build(); repo = SleepRepo(db)
        repo.record(SensorKind.SCREEN_ON); assertEquals(1, db.sensors().count())
    }
}

@RunWith(AndroidJUnit4::class)
class SleepMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_7_to_8_keeps_data_and_adds_the_sleep_tables() {
        helper.createDatabase("mig78.db", 7).apply {
            execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('Essay',1,'Europe/London',5,6.25,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig78.db", 8, true, MIGRATION_7_8)
        db.query("SELECT COUNT(*) FROM task").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO sensor_event (atMs,kind,value) VALUES (1,2,0)")
        db.execSQL("INSERT INTO night_summary (date,startMs,endMs,sleepMin,checkCount,checkMin,chargingPct,motionEvents,darkPct,gapCount,derivedAtMs) VALUES ('2026-11-03',1,2,3,0,0,0,0,0,0,1)")
    }
}

@RunWith(AndroidJUnit4::class)
class SleepCardTest {
    @get:Rule val rule = createComposeRule()

    @Test fun S7_with_the_blocking_service_off_the_card_says_logging_is_off() {
        rule.setContent { EfaTheme { SleepCard(SleepStatus(), serviceOn = false) } }
        rule.onNodeWithText("Off. Logging runs with the blocking service, so turn that on first.").assertExists()
    }

    @Test fun S2_before_any_night_the_card_says_what_a_night_needs() {
        rule.setContent { EfaTheme { SleepCard(SleepStatus(events = 12), serviceOn = true) } }
        rule.onNodeWithText("No night recorded yet. A night needs the screen off for an hour.").assertExists()
    }

    @Test fun S2_S3_S5_after_a_night_the_card_shows_hours_checks_gaps_and_that_there_is_no_microphone() {
        val s = SleepStatus(nights = 3, lastDate = "2026-11-03", lastSleepMin = 452, lastStart = "23:30", lastEnd = "07:10", lastChecks = 1, lastGaps = 0, events = 90, sinceText = "Mon 2 Nov")
        rule.setContent { EfaTheme { SleepCard(s, serviceOn = true) } }
        rule.onNodeWithText("Last night: 7h 32m asleep, 23:30 to 07:10.").assertExists()
        rule.onNodeWithText("1 night check, taken off. Logging gaps: 0.").assertExists()
        rule.onNodeWithText("never a microphone", substring = true).assertExists()
        rule.onNodeWithText("3 nights kept, 90 recent events, logging since Mon 2 Nov.").assertExists()
    }
}

/** The real service on the emulator: screen and charging events are recorded as they happen, with the screen turned off by the phone itself. */
@RunWith(AndroidJUnit4::class)
class SleepCollectorE2ETest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val screen = TestScreen(inst)
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db get() = EfaDb.get(ctx)

    private fun kinds(sinceMs: Long) = runBlocking { db.sensors().between(sinceMs, Long.MAX_VALUE).map { SensorKind.of(it.kind) } }

    @Before fun setUp() {
        screen.shell("settings put secure enabled_accessibility_services app.efa/app.efa.BlockerService")
        screen.shell("settings put secure accessibility_enabled 1")
        screen.shell("input keyevent KEYCODE_WAKEUP"); screen.shell("wm dismiss-keyguard")
        Thread.sleep(2_500)
    }

    @After fun tearDown() {
        screen.shell("dumpsys battery reset"); screen.shell("input keyevent KEYCODE_WAKEUP")
        screen.shell("settings put secure enabled_accessibility_services \"\"")
        screen.home()
    }

    @Test fun S5_S7_the_service_records_its_start_and_the_state_it_found() {
        screen.shell("settings put secure enabled_accessibility_services \"\""); Thread.sleep(1_000)
        val before = System.currentTimeMillis()
        screen.shell("settings put secure enabled_accessibility_services app.efa/app.efa.BlockerService"); Thread.sleep(3_000)
        val k = kinds(before)
        assertTrue(SensorKind.SERVICE_START in k, k.toString())
        assertTrue(SensorKind.SCREEN_ON in k || SensorKind.SCREEN_OFF in k); assertTrue(SensorKind.CHARGING_ON in k || SensorKind.CHARGING_OFF in k)
    }

    @Test fun S2_pressing_the_power_button_is_recorded_as_screen_off_then_screen_on() {
        val before = System.currentTimeMillis()
        screen.shell("input keyevent KEYCODE_SLEEP"); Thread.sleep(2_000)
        screen.shell("input keyevent KEYCODE_WAKEUP"); Thread.sleep(2_000)
        val k = kinds(before).filter { it == SensorKind.SCREEN_ON || it == SensorKind.SCREEN_OFF }
        assertEquals(listOf(SensorKind.SCREEN_OFF, SensorKind.SCREEN_ON), k.take(2), k.toString())
    }

    @Test fun S5_plugging_and_unplugging_is_recorded() {
        screen.shell("dumpsys battery set ac 1"); Thread.sleep(1_500)               // start plugged in, whatever it was
        val before = System.currentTimeMillis()
        screen.shell("dumpsys battery unplug"); Thread.sleep(1_500)
        screen.shell("dumpsys battery set ac 1"); Thread.sleep(1_500)
        val k = kinds(before).filter { it == SensorKind.CHARGING_ON || it == SensorKind.CHARGING_OFF }
        assertTrue(SensorKind.CHARGING_OFF in k && SensorKind.CHARGING_ON in k, k.toString())
    }

    @Test fun S5_the_light_sensor_is_read_into_one_of_four_buckets() {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        assumeTrue("this phone has no light sensor", sm.getDefaultSensor(Sensor.TYPE_LIGHT) != null)
        val before = System.currentTimeMillis()
        SleepCollector.sampleLightNow(ctx); Thread.sleep(3_000)
        val rows = runBlocking { db.sensors().between(before, Long.MAX_VALUE).filter { it.kind == SensorKind.LIGHT.code } }
        assertTrue(rows.isNotEmpty() && rows.all { it.value in 0..3 }, rows.toString())
    }

    @Test fun S5_no_microphone_permission_is_declared() {
        val perms = ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertFalse("android.permission.RECORD_AUDIO" in perms, perms.toString())
    }

    @Test fun LA13_one_minute_of_logging_writes_a_handful_of_rows_not_a_stream() {
        val before = System.currentTimeMillis()
        Thread.sleep(65_000)
        assertTrue(kinds(before).size <= 4, kinds(before).toString())
    }
}
