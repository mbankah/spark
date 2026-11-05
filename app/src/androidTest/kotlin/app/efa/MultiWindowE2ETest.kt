package app.efa

import android.content.Context
import android.content.Intent
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

/** LA7 on the emulator: a blocked app is closed in split screen and when it was already open as a block began. */
@RunWith(AndroidJUnit4::class)
class MultiWindowE2ETest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val screen = TestScreen(inst)
    private lateinit var ctx: Context
    private lateinit var db: EfaDb

    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    private fun clockAt(z: ZonedDateTime) { BlockingState.clockOverride = { z }; runBlocking { BlockingState.refresh(ctx) } }

    @Before fun setUp() {
        runBlocking {
            ctx = ApplicationProvider.getApplicationContext(); db = EfaDb.get(ctx)
            db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
            val raw = db.openHelper.writableDatabase; raw.execSQL("DELETE FROM allowance"); raw.execSQL("DELETE FROM block"); raw.execSQL("DELETE FROM tamper")
            BlockingRepo(db).saveList(setOf("com.android.chrome"))
        }
        screen.shell("settings put secure enabled_accessibility_services app.efa/app.efa.BlockerService")
        screen.shell("settings put secure accessibility_enabled 1")
        screen.shell("am force-stop com.android.chrome")
        screen.home(); Thread.sleep(2_000)
    }

    @After fun tearDown() {
        BlockingState.clockOverride = null
        runBlocking { BlockingRepo(db).saveList(emptySet()); db.openHelper.writableDatabase.execSQL("DELETE FROM allowance") }
        screen.shell("settings put secure enabled_accessibility_services \"\"")
        screen.shell("am force-stop com.android.chrome"); screen.home()
    }

    @Test fun LA7_a_blocked_app_opened_beside_another_app_in_split_screen_meets_the_card() {
        clockAt(t(19, 10, 30))                                                                    // Monday 10:30: a work block
        ctx.startActivity(Intent(Intent.ACTION_MAIN).setClassName("com.android.settings", "com.android.settings.Settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(screen.waitFor(15_000) { screen.foreground == "com.android.settings" })
        screen.shell("am start --windowingMode 3 -n com.android.chrome/com.google.android.apps.chrome.Main")
        val shown = screen.waitFor(25_000) { screen.has("is blocked") }
        screen.shell("screencap -p /sdcard/split_blocked.png")
        assertTrue(shown, "the card did not appear for an app in split screen. Screen: " + screen.dump())
    }

    @Test fun K1_an_app_that_was_already_open_when_the_work_block_began_is_closed_within_a_minute() {
        clockAt(t(19, 12, 20))                                                                    // lunch: the phone is open
        screen.shell("am start -n com.android.chrome/com.google.android.apps.chrome.Main")
        assertTrue(screen.waitFor(20_000) { screen.foreground == "com.android.chrome" })
        Thread.sleep(3_000); assertFalse(screen.has("is blocked"))
        clockAt(t(19, 15, 30))                                                                    // the work block begins while Chrome is in front
        val shown = screen.waitFor(80_000) { screen.has("is blocked") }
        assertTrue(shown, "Chrome stayed open after the block began. Screen: " + screen.dump())
    }
}
