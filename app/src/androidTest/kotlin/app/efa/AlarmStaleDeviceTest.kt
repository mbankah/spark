package app.efa

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.EfaDb
import app.efa.data.MorningRepo
import app.efa.data.MorningRow
import efa.AlarmReason
import efa.at
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** HA17: an alarm for a morning that was already stopped must not ring (found on 21 November 2026: a second alarm at 12:00 after the 11:30 one was stopped, and it could not be stopped). */
@RunWith(AndroidJUnit4::class)
class AlarmStaleDeviceTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val day = LocalDate.of(2026, 11, 4)
    private val noon = at(day, LocalTime.of(12, 0)).toInstant().toEpochMilli()
    private val stopped = at(day, LocalTime.of(11, 33)).toInstant().toEpochMilli()

    @Before fun open() { runBlocking { val db = EfaDb.get(ctx); db.mornings().put(MorningRow(day.toString(), noon - 1_800_000, AlarmReason.ONSET.name, ringStartedMs = noon - 1_800_000, stoppedMs = stopped)) } }
    @After fun close() { runBlocking { EfaDb.get(ctx).openHelper.writableDatabase.execSQL("DELETE FROM morning WHERE date = '$day'") } }

    @Test fun HA17_the_12_oclock_backstop_for_a_morning_that_was_stopped_at_11_33_does_not_ring() {
        AlarmSession.ringing = false
        AlarmReceiver().onReceive(ctx, Intent(AlarmScheduler.ACTION_FIRE).putExtra(AlarmScheduler.EXTRA_AT, noon).putExtra(AlarmScheduler.EXTRA_REASON, AlarmReason.BACKSTOP.name))
        Thread.sleep(2_500)
        assertFalse(AlarmSession.ringing, "nothing rings for a morning that is already stopped")
        val row = runBlocking { MorningRepo(EfaDb.get(ctx)).get(day) }
        assertNotNull(row); assertEquals(stopped, row.stoppedMs, "the stop is kept"); assertEquals(noon - 1_800_000, row.ringStartedMs, "the ring is not started again")
    }
}
