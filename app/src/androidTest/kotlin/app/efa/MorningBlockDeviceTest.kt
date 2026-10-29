package app.efa

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

/** The blocked card and the ready screen for the morning (M5, M6, M8, M10). */
@RunWith(AndroidJUnit4::class)
class MorningScreensTest {
    @get:Rule val rule = createComposeRule()
    private val now = at(LocalDate.of(2026, 11, 4), LocalTime.of(8, 0))
    private fun block(reason: BlockReason, label: String) = Decision.Block(reason, label, now.plusHours(6), 0, night = false)

    @Test fun M6_the_ready_block_says_why_when_it_ends_and_offers_no_typed_wall() {
        var ready = false
        rule.setContent { EfaTheme { BlockedCard("Instagram", block(BlockReason.MORNING_READY, "Ready photo"), 0, {}, {}, null, onReady = { ready = true }) } }
        rule.onNodeWithText("Morning: social stays blocked until you photograph your ready spot. If you cannot, it ends by itself at 14:00.").assertExists()
        rule.onNodeWithText("It cannot be opened with a typed string. Calls, messages, maps, music and the Buffer still work.").assertExists()
        rule.onNodeWithText("Open anyway", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Take the ready photo").performClick(); assertTrue(ready)
    }

    @Test fun M5_the_ringing_block_says_to_stop_the_alarm_first_and_has_no_wall_and_no_ready_button() {
        rule.setContent { EfaTheme { BlockedCard("Instagram", block(BlockReason.MORNING_ALARM, "Alarm"), 0, {}, {}, null) } }
        rule.onNodeWithText("The alarm is ringing. Stop it with the photo first.").assertExists()
        rule.onNodeWithText("Open anyway", substring = true).assertDoesNotExist(); rule.onNodeWithText("Take the ready photo").assertDoesNotExist()
    }

    @Test fun M6_other_blocks_still_offer_the_wall() {
        rule.setContent { EfaTheme { BlockedCard("Instagram", Decision.Block(BlockReason.TRANSITION, "Walk", now.plusMinutes(10), 0, night = false), 0, {}, {}, null) } }
        rule.onNodeWithText("Open anyway: type 75 characters").assertExists()
    }

    private fun ready(offer: Offer, message: String? = null, secs: Int? = null, photo: () -> Unit = {}, broken: () -> Unit = {}, done: (StopMethod) -> Unit = {}) =
        rule.setContent { EfaTheme { ReadyScreen(AlarmUi("", offer, message, secs), photo, broken, done, {}) } }

    @Test fun M8_the_ready_screen_asks_for_the_photo_of_the_last_step() {
        var took = false
        ready(Offer.Photo(1), photo = { took = true })
        rule.onNodeWithText("Ready?").assertExists(); rule.onNodeWithText("Photograph the last getting-ready step at its spot", substring = true).assertExists()
        rule.onNodeWithText("Take the ready photo").performClick(); assertTrue(took); rule.onNodeWithText("Attempt 1 of 2.").assertExists()
    }

    @Test fun M10_a_failed_ready_photo_shows_why_and_a_second_attempt() {
        ready(Offer.Photo(2), "That does not match your ready spot (score 20, it needs 65).")
        rule.onNodeWithText("That does not match your ready spot (score 20, it needs 65).").assertExists(); rule.onNodeWithText("Attempt 2 of 2.").assertExists()
    }

    @Test fun M10_after_two_failures_the_wait_counts_down_and_there_is_no_end_button_yet() {
        ready(Offer.Wait(0), secs = 125); rule.onNodeWithText("You can end the block in 2:05.").assertExists(); rule.onNodeWithText("End the social block").assertDoesNotExist()
    }

    @Test fun M10_when_the_wait_is_over_the_end_button_passes_the_method() {
        var m: StopMethod? = null
        ready(Offer.CanStop(StopMethod.WAIT), done = { m = it }); rule.onNodeWithText("End the social block").performClick(); assertEquals(StopMethod.WAIT, m)
    }
}

/** The social block on the real phone: blocked after the alarm stops, no typed wall, and the ready photo from the real camera lifts it. */
@RunWith(AndroidJUnit4::class)
class MorningBlockE2ETest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val screen = TestScreen(inst)
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db get() = EfaDb.get(ctx)
    private val chrome = "com.android.chrome"
    private var savedList: String? = null

    companion object {
        @JvmStatic @org.junit.BeforeClass fun gateOff() { SetupGate.enforced = false }
        @JvmStatic @org.junit.AfterClass fun gateOn() { SetupGate.enforced = true }
    }

    @Before fun setUp() {
        screen.shell("pm grant app.efa android.permission.CAMERA")
        screen.shell("settings put secure enabled_accessibility_services app.efa/app.efa.BlockerService"); screen.shell("settings put secure accessibility_enabled 1")
        listOf("morning", "reference_photo").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        runBlocking { savedList = db.meta().get(META_BLOCKLIST); BlockingRepo(db).saveList(setOf(chrome)) }
        screen.shell("am force-stop $chrome"); screen.shell("input keyevent KEYCODE_WAKEUP"); screen.shell("wm dismiss-keyguard"); Thread.sleep(2_500)
    }

    @After fun tearDown() {
        listOf("morning", "reference_photo").forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        runBlocking { if (savedList == null) db.openHelper.writableDatabase.execSQL("DELETE FROM meta WHERE key = 'blocklist'") else db.meta().put(MetaRow(META_BLOCKLIST, savedList!!)); BlockingState.refresh(ctx) }
        screen.shell("settings put secure enabled_accessibility_services \"\""); screen.shell("am force-stop $chrome"); screen.home()
    }

    private fun stoppedMorning() {
        val now = System.currentTimeMillis()
        runBlocking {
            val r = MorningRepo(db); val at = now - 600_000
            r.fire(at, "ONSET", at); r.stop(r.dateOf(at), StopMethod.PHOTO, 90, now - 300_000)
            BlockingState.refresh(ctx)
        }
    }

    private fun openMain(action: String? = null) {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (action != null) i.putExtra(BufferNotifier.EXTRA_ACTION, action)
        ctx.startActivity(i)
    }

    private fun takeReadyReferenceViaUi() {
        openMain()
        assertTrue(screen.waitFor(20_000) { screen.has("Type to open") }, "home. Screen: " + screen.dump())
        assertTrue(screen.scrollTo("Setup: home")); screen.clickExact("Setup: home, venues, blocking and GitHub")
        assertTrue(screen.waitFor(10_000) { screen.has("BLOCKING") }); assertTrue(screen.scrollTo("Set up mornings")); screen.clickExact("Set up mornings")
        assertTrue(screen.waitFor(10_000) { screen.has("READY SPOT") }); assertTrue(screen.scrollTo("Take the ready spot photo")); screen.clickExact("Take the ready spot photo")
        assertTrue(screen.waitFor(30_000) { screen.has("Take photo") }, "camera. Screen: " + screen.dump()); screen.clickExact("Take photo")
        assertTrue(screen.waitFor(30_000) { screen.has("Saved. It replaces the old one.") }, "saved. Screen: " + screen.dump())
        screen.home(); Thread.sleep(1_000)
    }

    @Test fun M6_M8_a_blocked_app_is_closed_after_the_alarm_stops_and_the_real_ready_photo_opens_it_again() {
        takeReadyReferenceViaUi()
        stoppedMorning()
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(chrome)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(screen.waitFor { screen.has("is blocked") }, "no blocked card. Screen: " + screen.dump())
        assertTrue(screen.has("Morning: social stays blocked until you photograph your ready spot"), screen.dump())
        assertFalse(screen.has("Open anyway"), "the morning block has no typed wall")
        screen.clickExact("Take the ready photo")
        assertTrue(screen.waitFor(20_000) { screen.has("Ready?") }, "no ready screen. Screen: " + screen.dump())
        screen.clickExact("Take the ready photo")
        assertTrue(screen.waitFor(30_000) { screen.has("Take photo") }, "camera. Screen: " + screen.dump()); screen.clickExact("Take photo")
        assertTrue(screen.waitFor(40_000) { runBlocking { db.mornings().latest()?.readyMs } != null }, "the ready photo was not accepted. Screen: " + screen.dump())
        assertEquals("PHOTO", runBlocking { db.mornings().latest()!!.readyMethod })
        screen.home(); Thread.sleep(1_000); screen.shell("am force-stop $chrome")
        // The phone may be in a work block at this hour, so the test asks only that the morning block is over, not that everything is open.
        val d = BlockingState.decide(chrome)
        assertTrue(d !is Decision.Block || !d.reason.isMorning, "the morning block must be over, got $d")
    }

    @Test fun M10_a_photo_that_does_not_match_the_ready_spot_leaves_the_block_in_place_and_counts_the_failure() {
        runBlocking { ReferenceRepo(ctx, db).set(RefKind.READY, roomJpeg(91), System.currentTimeMillis()) }
        stoppedMorning()
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(chrome)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(screen.waitFor { screen.has("is blocked") }); screen.clickExact("Take the ready photo")
        assertTrue(screen.waitFor(20_000) { screen.has("Ready?") }); screen.clickExact("Take the ready photo")
        assertTrue(screen.waitFor(30_000) { screen.has("Take photo") }); screen.clickExact("Take photo")
        assertTrue(screen.waitFor(30_000) { screen.has("does not match your ready spot") }, "Screen: " + screen.dump())
        val row = runBlocking { db.mornings().latest()!! }
        assertNull(row.readyMs); assertEquals(1, row.readyFailed); assertTrue(BlockingState.decide(chrome) is Decision.Block)
    }

    @Test fun M10_with_no_ready_spot_photo_the_ready_screen_goes_to_the_wait() {
        stoppedMorning()
        openMain(BufferNotifier.ACTION_READY)
        assertTrue(screen.waitFor(20_000) { screen.has("Ready?") }, "Screen: " + screen.dump())
        assertTrue(screen.waitFor(10_000) { screen.has("No ready spot photo is set") }, "Screen: " + screen.dump())
        assertTrue(screen.waitFor(10_000) { screen.has("You can end the block in") }, "Screen: " + screen.dump())
    }
}
