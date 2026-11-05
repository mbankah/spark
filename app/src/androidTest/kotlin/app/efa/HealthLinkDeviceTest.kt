package app.efa

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

/** S6 against the emulator's real Health Connect: a sleep session written there is read back with the awake minutes taken off. */
@RunWith(AndroidJUnit4::class)
class HealthLinkE2ETest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun grant() {
        val ua = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (p in listOf("android.permission.health.READ_SLEEP", "android.permission.health.WRITE_SLEEP")) runCatching { ua.grantRuntimePermission(ctx.packageName, p) }
    }

    @Test fun S6_a_sleep_session_in_health_connect_is_read_with_the_awake_minutes_taken_off() = runBlocking<Unit> {
        assumeTrue("Health Connect is not on this device", HealthLink.status(ctx) == HealthLink.Status.READY)
        grant()
        assumeTrue("the sleep permissions could not be granted here", HealthLink.granted(ctx))
        val client = HealthConnectClient.getOrCreate(ctx)
        val start = Instant.now().minusSeconds(9 * 3600); val end = start.plusSeconds(8 * 3600)
        client.insertRecords(listOf(SleepSessionRecord(start, ZoneOffset.UTC, end, ZoneOffset.UTC, metadata = Metadata.manualEntry(),
            stages = listOf(SleepSessionRecord.Stage(start, start.plusSeconds(3600), SleepSessionRecord.STAGE_TYPE_SLEEPING), SleepSessionRecord.Stage(start.plusSeconds(3600), start.plusSeconds(3600 + 1800), SleepSessionRecord.STAGE_TYPE_AWAKE),
                SleepSessionRecord.Stage(start.plusSeconds(3600 + 1800), end, SleepSessionRecord.STAGE_TYPE_SLEEPING)))))
        val found = HealthLink.sessions(ctx, start.minusSeconds(3600), end.plusSeconds(3600))
        val mine = found.firstOrNull { kotlin.math.abs(java.time.Duration.between(it.start, start).toMillis()) < 1000 }
        assertNotNull(mine, "the session was not read back: $found")
        assertEquals(8 * 60 - 30, mine.asleepMin)
        val phoneNight = sessionForNight(found, start.plusSeconds(600), end.minusSeconds(600)); assertNotNull(phoneNight)
    }

    @Test fun X7_a_time_with_no_sleep_gives_an_empty_list_and_no_error() = runBlocking<Unit> {
        grant()
        assertTrue(HealthLink.sessions(ctx, Instant.parse("2001-01-01T00:00:00Z"), Instant.parse("2001-01-02T00:00:00Z")).isEmpty())
    }
}
