package app.efa

import android.content.Context
import android.content.Intent
import android.net.Uri
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

private const val YOUTUBE = "com.google.android.youtube"

/**
 * K10 on the emulator's real Chrome: with YouTube on the block list, in a work block, opening a YouTube address in the browser ends at the plain card, and a site
 * that is not listed opens normally. The address bar is read through the real accessibility service. Only Chrome is run here; the other browsers in the list
 * are covered by unit tests of their address bar ids and by the real-phone checks (DEVICE_TESTS E36).
 */
@RunWith(AndroidJUnit4::class)
class BrowserE2ETest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val screen = TestScreen(inst)
    private lateinit var ctx: Context
    private lateinit var db: EfaDb

    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    private fun clockAt(z: ZonedDateTime) { BlockingState.clockOverride = { z }; runBlocking { BlockingState.refresh(ctx) } }
    private fun view(url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.android.chrome").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** Chrome's first-run screens (sign in, notifications) are clicked through, as a person would, so the page and its address bar can show. */
    private fun passFirstRun() {
        val buttons = listOf("Accept & continue", "Use without an account", "No thanks", "Got it", "Skip", "Not now")
        val end = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < end) {
            val b = buttons.firstOrNull { screen.has(it) } ?: run { Thread.sleep(700); if (screen.foreground == "com.android.chrome" && !screen.has("Make Chrome") && !screen.has("Welcome")) return; null }
            if (b != null) { runCatching { screen.click(b) }; Thread.sleep(1_200) }
        }
    }

    @Before fun setUp() {
        runBlocking {
            ctx = ApplicationProvider.getApplicationContext()
            db = EfaDb.get(ctx)
            db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
            val raw = db.openHelper.writableDatabase
            raw.execSQL("DELETE FROM allowance"); raw.execSQL("DELETE FROM block"); raw.execSQL("DELETE FROM tamper")
            BlockingRepo(db).saveList(setOf(YOUTUBE))
        }
        screen.shell("settings put secure enabled_accessibility_services app.efa/app.efa.BlockerService")
        screen.shell("settings put secure accessibility_enabled 1")
        screen.shell("am force-stop com.android.chrome")
        clockAt(t(19, 10, 30))                         // Monday 10:30, inside a work block
        screen.home(); Thread.sleep(2_000)
    }

    @After fun tearDown() {
        BlockingState.clockOverride = null
        runBlocking { BlockingRepo(db).saveList(emptySet()); db.openHelper.writableDatabase.execSQL("DELETE FROM allowance") }
        screen.shell("settings put secure enabled_accessibility_services \"\"")
        screen.shell("am force-stop com.android.chrome"); screen.home()
    }

    @Test fun K10_a_listed_site_in_the_browser_ends_at_the_plain_card_in_a_work_block() {
        view("https://m.youtube.com/"); passFirstRun()
        val shown = screen.waitFor(30_000) { screen.has("is blocked") }
        screen.shell("screencap -p /sdcard/browser_blocked.png")
        assertTrue(shown, "the blocked card did not appear for a YouTube address in Chrome. Screen: " + screen.dump())
    }

    @Test fun K10_a_site_that_is_not_listed_opens_normally() {
        view("https://example.com/"); passFirstRun()
        assertTrue(screen.waitFor(30_000) { screen.foreground == "com.android.chrome" })
        Thread.sleep(6_000)
        assertFalse(screen.has("is blocked"), "an unlisted site was blocked")
    }

    @Test fun K10_outside_a_work_block_the_listed_site_opens() {
        clockAt(t(19, 12, 20))                         // Monday 12:20: lunch (12:15 to 12:40), the phone is open (K3)
        view("https://m.youtube.com/"); passFirstRun()
        assertTrue(screen.waitFor(30_000) { screen.foreground == "com.android.chrome" })
        Thread.sleep(6_000)
        assertFalse(screen.has("is blocked"))
    }
}
