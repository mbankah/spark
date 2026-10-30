package app.efa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.efa.data.*
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.*

/**
 * The phone talking to the real Windows service (A4, K4, K5). tools/pc-link-e2e.sh starts Spark Guard on this computer against a temporary hosts file and passes
 * its pairing code and port. The emulator reaches the computer as 10.0.2.2. Without those arguments these tests are skipped, so a plain run does not need a PC.
 */
@RunWith(AndroidJUnit4::class)
class PcLinkE2ETest {
    private val args = InstrumentationRegistry.getArguments()
    private val code get() = args.getString("pcCode").orEmpty()
    private val port get() = args.getString("pcPort")?.toIntOrNull() ?: 0
    private val addr get() = PcAddress("10.0.2.2", port)
    private fun needService() = assumeTrue("start the service with tools/pc-link-e2e.sh", code.isNotEmpty() && port != 0)

    private fun state(issued: Instant = Instant.now()) =
        pcStateJson(issued, setOf("youtube.com"), listOf(PcWindow(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), "Work block")))

    @Test fun A4_the_service_accepts_the_phones_signed_state() {
        needService()
        val r = PcLink.send(addr, code, state()); assertTrue(r.ok, r.message); assertEquals("Sent.", r.message)
    }

    @Test fun A4_the_service_refuses_a_wrong_pairing_code() {
        needService()
        val r = PcLink.send(addr, "not-the-code", state(Instant.now().plusSeconds(30))); assertFalse(r.ok); assertTrue(r.message.contains("pairing code"))
    }

    @Test fun K5_the_same_state_sent_twice_is_noticed_and_is_not_a_failure() {
        needService()
        val s = state(Instant.now().plusSeconds(60))
        assertEquals("Sent.", PcLink.send(addr, code, s).message)
        val again = PcLink.send(addr, code, s); assertTrue(again.ok); assertEquals("The PC already has this.", again.message)
    }

    @Test fun X7_an_address_nobody_answers_says_so_plainly() {
        val r = PcLink.send(PcAddress("10.0.2.2", 1), "x", state()); assertFalse(r.ok); assertTrue(r.message.startsWith("Could not reach"))
    }

    @Test fun A4_the_whole_push_from_the_blocking_plan_reaches_the_service() {
        needService()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking {
            val db = EfaDb.get(ctx); db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
            BlockingRepo(db).saveList(setOf("com.google.android.youtube"))
            db.meta().put(MetaRow(META_PC_ADDRESS, "10.0.2.2:$port")); db.meta().put(MetaRow(META_PC_CODE, code))
            BlockingState.refresh(ctx)
            val r = PcLink.push(ctx); assertTrue(r.ok, r.message)
            BlockingRepo(db).saveList(emptySet())
        }
    }
}

/** The PC link screen. */
@RunWith(AndroidJUnit4::class)
class PcScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun A4_the_screen_asks_for_an_address_and_a_code_and_saves_both() {
        var saved: Pair<String, String>? = null
        rule.setContent { EfaTheme { PcScreen("192.168.1.20", "abc", "Not sent yet.", { a, k -> saved = a to k }, {}, {}) } }
        rule.onNodeWithText("Not sent yet.").assertExists()
        rule.onNodeWithText("Save and send").performScrollTo().performClick(); assertEquals("192.168.1.20" to "abc", saved)
    }

    @Test fun X7_an_address_that_is_not_one_is_refused_before_anything_is_sent() {
        var saved = false
        rule.setContent { EfaTheme { PcScreen("not an address", "abc", "", { _, _ -> saved = true }, {}, {}) } }
        rule.onNodeWithText("That is not an address like 192.168.1.20 or 192.168.1.20:47831.").assertExists()
        rule.onNodeWithText("Save and send").performScrollTo().performClick(); assertFalse(saved)
    }

    @Test fun K9_the_screen_says_the_plan_decides_and_the_wall_does_not_open_the_pc() {
        rule.setContent { EfaTheme { PcScreen("", "", "Not sent yet.", { _, _ -> }, {}, {}) } }
        rule.onNodeWithText("Passing the typed wall on the phone does not open the PC", substring = true).performScrollTo().assertExists()
    }
}
