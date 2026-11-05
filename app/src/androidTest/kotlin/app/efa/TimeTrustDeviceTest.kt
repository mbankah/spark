package app.efa

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.EfaDb
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** The trusted time source on the phone (G10, E14). */
@RunWith(AndroidJUnit4::class)
class TimeTrustDeviceTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    @After fun reset() { TimeTrust.setNetworkOffsetForTest(null) }

    @Test fun G10_E14_a_clock_set_forward_by_hand_is_logged_as_tampering() = runBlocking {
        val db = EfaDb.get(ctx); db.openHelper.writableDatabase.execSQL("DELETE FROM tamper")
        assertNull(TimeTrust.observe(ctx, 10_000_000, 1_000, 777))
        val j = TimeTrust.observe(ctx, 10_000_000 + 4 * 3_600_000, 1_000 + 60_000, 777)
        assertEquals("clock set forward", j?.kind)
        val row = db.tamper().all().last(); assertEquals("clock_changed", row.kind); assertTrue(row.detail.contains("clock set forward"))
    }

    @Test fun X4_a_reboot_is_not_logged_as_tampering() = runBlocking {
        val db = EfaDb.get(ctx); db.openHelper.writableDatabase.execSQL("DELETE FROM tamper")
        TimeTrust.observe(ctx, 50_000_000, 40_000_000, 1); assertNull(TimeTrust.observe(ctx, 50_000_000 + 600_000, 20_000, 2))
        assertTrue(db.tamper().all().isEmpty())
    }

    @Test fun G10_when_the_network_says_the_phone_is_hours_out_blocking_uses_corrected_time() {
        BlockingState.clockOverride = null
        val before = BlockingState.now().toInstant().toEpochMilli()
        TimeTrust.setNetworkOffsetForTest(3 * 3_600_000L)
        val after = BlockingState.now().toInstant().toEpochMilli()
        assertTrue(after - before in (3 * 3_600_000L - 5_000)..(3 * 3_600_000L + 5_000), "moved ${after - before}")
    }

    @Test fun X7_a_small_difference_or_no_answer_changes_nothing() {
        BlockingState.clockOverride = null
        TimeTrust.setNetworkOffsetForTest(45_000); assertEquals(0L, TimeTrust.correction())
        TimeTrust.setNetworkOffsetForTest(null); assertEquals(0L, TimeTrust.correction())
    }

    @Test fun G10_an_unreachable_time_server_is_not_an_error() { assertNull(TimeTrust.lookup("nonexistent.invalid", 800)) }
}
