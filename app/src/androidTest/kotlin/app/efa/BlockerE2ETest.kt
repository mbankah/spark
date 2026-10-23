package app.efa

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
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
import java.time.ZonedDateTime
import kotlin.test.*

private const val CHROME = "com.android.chrome"
private const val SETTINGS = "com.android.settings"

/**
 * A small stand-in for UiDevice. UiAutomator connects a UiAutomation that switches every other accessibility service off, which
 * would switch the blocker off too, so this connects one with FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES and reads the screen
 * through it.
 */
internal class TestScreen(inst: Instrumentation) {
    val ua: UiAutomation = inst.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).also {
        it.serviceInfo = it.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS }
    }

    fun shell(cmd: String): String = ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes().decodeToString() }

    fun find(pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = ua.rootInActiveWindow ?: return null
        fun walk(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (pred(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { c -> walk(c)?.let { return it } }
            return null
        }
        return walk(root)
    }

    fun text(sub: String) = find { it.text?.contains(sub) == true || it.contentDescription?.contains(sub) == true }
    fun has(sub: String) = text(sub) != null

    fun waitFor(ms: Long = 20_000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(250) }
        return cond()
    }

    fun click(sub: String): Boolean {
        val node = checkNotNull(text(sub)) { "no node with \"$sub\"" }
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return (n ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Every visible text, for a failure message. */
    fun dump(): String {
        val out = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo) { n.text?.let { out += it.toString() }; n.contentDescription?.let { out += "[d]$it" }; for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) } }
        ua.rootInActiveWindow?.let { walk(it) }
        return out.joinToString(" | ").take(900)
    }

    val foreground: String? get() = ua.rootInActiveWindow?.packageName?.toString()
    fun home() = shell("input keyevent KEYCODE_HOME")
    fun back() = shell("input keyevent KEYCODE_BACK")
}

/**
 * Real blocking on the emulator: the accessibility service is switched on, a real app is opened, and the blocked card must
 * appear. The test and the service share one process, so the test moves the clock the service reads (BlockingState.clockOverride).
 */
@RunWith(AndroidJUnit4::class)
class BlockerE2ETest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val screen = TestScreen(inst)
    private lateinit var ctx: Context
    private lateinit var db: EfaDb

    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    private fun clockAt(z: ZonedDateTime) { BlockingState.clockOverride = { z }; runBlocking { BlockingState.refresh(ctx) } }
    private fun openChrome() { ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(CHROME)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun waitCard() = screen.waitFor { screen.has("is blocked") }

    @Before fun setUp() {
        runBlocking {
            ctx = ApplicationProvider.getApplicationContext()
            db = EfaDb.get(ctx)
            db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
            val raw = db.openHelper.writableDatabase
            raw.execSQL("DELETE FROM allowance"); raw.execSQL("DELETE FROM block"); raw.execSQL("DELETE FROM tamper")
            BlockingRepo(db).saveList(setOf(CHROME))
        }
        screen.shell("settings put secure enabled_accessibility_services app.efa/app.efa.BlockerService")
        screen.shell("settings put secure accessibility_enabled 1")
        screen.shell("am force-stop $CHROME")
        clockAt(t(19, 10, 30))                         // Monday 10:30, inside the work block "Notes and problems: Randomness"
        screen.home()
        Thread.sleep(2_000)                            // let the service bind
    }

    @After fun tearDown() {
        BlockingState.clockOverride = null
        runBlocking {
            BlockingRepo(db).saveList(emptySet())
            db.openHelper.writableDatabase.execSQL("DELETE FROM allowance")
        }
        screen.shell("settings put secure enabled_accessibility_services \"\"")
        screen.shell("am force-stop $CHROME")
        screen.home()
    }

    @Test fun LA8_K1_a_blocked_app_opened_in_a_work_block_ends_at_the_plain_card() {
        openChrome()
        assertTrue(waitCard(), "the blocked card did not appear")
        Thread.sleep(700); screen.shell("screencap -p /sdcard/blocked_card.png")      // kept for a visual check
        assertTrue(screen.has("Chrome is blocked"))
        assertTrue(screen.has("Work block: Notes and problems: Randomness"))
        assertTrue(screen.has("125 unproven minutes"), "the cost line is missing")
        assertTrue(screen.has("Buffer now"), "the Buffer arithmetic is missing")
    }

    @Test fun LA10_go_home_returns_to_the_launcher_and_does_not_loop() {
        openChrome(); assertTrue(waitCard())
        screen.click("Go home")
        Thread.sleep(3_000)
        assertFalse(screen.has("is blocked"), "the card came back")
        assertNotEquals(CHROME, screen.foreground)
    }

    @Test fun LA10_back_from_the_card_goes_home_not_back_into_the_app() {
        openChrome(); assertTrue(waitCard())
        screen.back()
        Thread.sleep(3_000)
        assertFalse(screen.has("is blocked"))
        assertNotEquals(CHROME, screen.foreground)
    }

    @Test fun X1_an_app_not_on_the_list_opens_normally_during_a_work_block() {
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(SETTINGS)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(screen.waitFor(10_000) { screen.foreground == SETTINGS })
        Thread.sleep(2_000)
        assertFalse(screen.has("is blocked"))
    }

    private fun typeWall(): String {
        val target = checkNotNull(screen.text("Characters to type: ")).contentDescription.toString().removePrefix("Characters to type: ")
        val field = checkNotNull(screen.text("Type the characters here"))
        Thread.sleep(1_500)                                          // the field takes focus by itself when the wall opens
        // One key press at a time, waiting for the field to take each one, as a person types.
        for ((i, ch) in target.withIndex()) {
            screen.shell("input text $ch")
            if (i < target.length - 1) assertTrue(screen.waitFor(3_000) { screen.has("(${i + 1} OF") }, "character ${i + 1} was not taken. Screen: " + screen.dump())
        }
        return target
    }

    @Test fun LA9_passing_the_wall_opens_the_app_for_30_minutes_and_breaks_the_block() {
        openChrome(); assertTrue(waitCard())
        screen.click("Open anyway")
        assertTrue(screen.waitFor(5_000) { screen.has("Characters to type: ") })
        val target = typeWall()
        assertEquals(75, target.length)
        assertTrue(screen.waitFor { screen.foreground == CHROME }, "the app did not open after the wall. Screen: " + screen.dump())
        val a = runBlocking { db.allowances().active(0) }.single()
        assertEquals("app", a.kind); assertEquals(CHROME, a.pkg); assertEquals(75, a.wallLength)
        val key = blockKey(nowAndNext(WeekB.slots, t(19, 10, 30)).now!!)
        assertNotNull(runBlocking { db.blocks().get(key) }?.brokenAtMs, "the work block should be broken")
        clockAt(t(19, 10, 59)); assertEquals(Decision.Allow, BlockingState.decide(CHROME))
        clockAt(t(19, 11, 1)); assertTrue(BlockingState.decide(CHROME) is Decision.Block)
    }

    @Test fun LA9_a_broken_block_can_no_longer_be_proved() {
        runBlocking {
            val ds = nowAndNext(WeekB.slots, t(19, 10, 30)).now!!
            val repo = BlockRepo(ctx, db, WeekB.slots)
            repo.recordHold(ds, 1L); repo.breakBlock(ds, 2L)
            assertEquals(Reason.BLOCK_BROKEN, repo.submitPhoto(ds, jpeg(91), t(19, 12, 20)).reason)
        }
    }

    @Test fun N3_N4_a_night_override_opens_the_list_and_the_next_one_is_longer() {
        clockAt(t(24, 0, 30))                           // Saturday 00:30, past Friday night's cutoff (00:10) and before the 02:30 hard stop
        openChrome(); assertTrue(waitCard())
        assertTrue(screen.has("Past the night cutoff"), "night card text missing. Screen: " + screen.dump())
        assertTrue(screen.has("Wake 08:30, so sleeping after 00:30 means under 8h"))
        screen.click("Open anyway: type 75")
        assertTrue(screen.waitFor(5_000) { screen.has("Characters to type: ") })
        typeWall()
        assertTrue(screen.waitFor { screen.foreground == CHROME }, "no override. Screen: " + screen.dump())
        val n = runBlocking { db.allowances().active(0) }.single()
        assertEquals("night", n.kind); assertEquals(75, n.wallLength)
        screen.shell("am force-stop $CHROME"); screen.home()
        clockAt(t(24, 1, 5))
        openChrome(); assertTrue(waitCard(), "the card did not return. Screen: " + screen.dump())
        assertTrue(screen.has("Open anyway: type 100"), "the second override should be 100 characters. Screen: " + screen.dump())
    }

    @Test fun N7_no_override_is_offered_within_six_hours_of_the_wake() {
        clockAt(t(24, 2, 40))
        openChrome(); assertTrue(waitCard())
        assertTrue(screen.has("No override is possible now"))
        assertFalse(screen.has("Open anyway"))
    }
}

/** The wall as a control: pasting restarts it, typing it by hand completes it. */
@RunWith(AndroidJUnit4::class)
class WallFieldTest {
    @get:Rule val rule = createComposeRule()

    @Test fun M7_pasting_the_whole_string_restarts_it_and_does_not_finish() {
        var done = false
        val target = "abcdefghij"
        rule.setContent { EfaTheme { WallField(target) { done = true } } }
        rule.onNodeWithContentDescription("Type the characters here").performTextInput(target)      // all ten at once
        rule.onNodeWithText("Mistake. Start again", substring = true).assertExists()
        assertFalse(done)
    }

    @Test fun M7_a_wrong_character_restarts_from_the_first() {
        var done = false
        rule.setContent { EfaTheme { WallField("abcd") { done = true } } }
        val f = rule.onNodeWithContentDescription("Type the characters here")
        f.performTextInput("a"); f.performTextInput("b"); f.performTextInput("x")
        rule.onNodeWithText("Mistake. Start again", substring = true).assertExists()
        rule.onNodeWithText("TYPE THIS EXACTLY, BY HAND (0 OF 4)").assertExists()
        assertFalse(done)
    }

    @Test fun M7_typing_every_character_by_hand_finishes() {
        var done = false
        val target = newWallString(75)
        rule.setContent { EfaTheme { WallField(target) { done = true } } }
        val f = rule.onNodeWithContentDescription("Type the characters here")
        target.forEach { f.performTextInput(it.toString()) }
        rule.waitUntil(5_000) { done }
    }

    @Test fun M7_the_string_to_type_is_fresh_each_time_it_is_shown() {
        assertNotEquals(newWallString(75), newWallString(75))
    }
}
