package app.efa

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import kotlin.test.*

/** LA6 on the emulator's real Settings app: Spark's own pages meet the wall, other pages do not. */
@RunWith(AndroidJUnit4::class)
class SettingsGateE2ETest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val screen = TestScreen(inst)
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun open(intent: Intent) = ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun appInfo(pkg: String) = open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    @Before fun setUp() {
        SettingsGate.allowance = SettingsAllowance(null)
        runBlocking { EfaDb.get(ctx).openHelper.writableDatabase.execSQL("DELETE FROM tamper") }
        screen.shell("settings put secure enabled_accessibility_services app.efa/app.efa.BlockerService")
        screen.shell("settings put secure accessibility_enabled 1")
        screen.shell("am force-stop com.android.settings")                                  // each test starts from a closed Settings
        screen.home(); Thread.sleep(2_000)
    }

    @After fun tearDown() {
        SettingsGate.allowance = SettingsAllowance(null)
        screen.shell("settings put secure enabled_accessibility_services \"\"")
        screen.shell("am force-stop com.android.settings"); screen.home()
    }

    @Test fun LA6_spark_s_own_app_info_page_meets_the_wall_and_is_logged() {
        appInfo("app.efa")
        val shown = screen.waitFor(25_000) { screen.has("You are opening the app info page for Spark") }
        screen.shell("screencap -p /sdcard/settings_gate.png")
        assertTrue(shown, "the wall did not appear. Screen: " + screen.dump())
        assertTrue(screen.has("Characters to type: "), "the typed wall is missing")
        val rows = runBlocking { EfaDb.get(ctx).tamper().all() }
        assertTrue(rows.any { it.kind == "settings_page" && it.detail == "APP_INFO" }, "the sighting was not logged: $rows")
    }

    @Test fun LA6_going_home_from_the_wall_returns_to_the_launcher() {
        appInfo("app.efa")
        assertTrue(screen.waitFor(25_000) { screen.has("Go home") })
        screen.click("Go home"); Thread.sleep(2_500)
        assertFalse(screen.has("You are opening"), "the wall came back")
    }

    @Test fun LA6_another_apps_info_page_opens_normally() {
        appInfo("com.android.chrome")
        assertTrue(screen.waitFor(20_000) { screen.foreground == "com.android.settings" })
        Thread.sleep(4_000)
        assertFalse(screen.has("You are opening"), "another app's page was walled")
    }

    @Test fun LA6_android_settings_itself_stays_openable() {
        open(Intent(Settings.ACTION_SETTINGS))
        assertTrue(screen.waitFor(20_000) { screen.foreground == "com.android.settings" })
        Thread.sleep(4_000)
        assertFalse(screen.has("You are opening"))
    }

    @Test fun LA6_after_the_wall_is_passed_the_page_opens_for_ten_minutes() {
        SettingsGate.allowance = SettingsAllowance(null).after(BlockingState.now())
        appInfo("app.efa")
        assertTrue(screen.waitFor(20_000) { screen.foreground == "com.android.settings" })
        Thread.sleep(4_000)
        assertFalse(screen.has("You are opening"), "the wall came back inside the ten minutes")
    }
}
