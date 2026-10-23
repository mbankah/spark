package app.efa

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.*
import efa.LONDON
import efa.WeekB
import efa.at
import efa.nowAndNext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Instrumented: runs on a device or emulator. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class DbTest {
    private lateinit var db: EfaDb

    @Before fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, EfaDb::class.java).build()
    }
    @After fun close() = db.close()

    @Test fun O9_first_run_seeds_week_B_and_only_once() = runBlocking {
        db.seedIfEmpty(1_000L, LONDON.id)
        assertEquals(WeekB.slots.size, db.pattern().count())
        db.seedIfEmpty(2_000L, LONDON.id)           // a second launch changes nothing
        assertEquals(WeekB.slots.size, db.pattern().count())
    }

    @Test fun G6_first_use_time_and_zone_are_stored_and_never_overwritten() = runBlocking {
        db.seedIfEmpty(1_000L, LONDON.id)
        db.seedIfEmpty(2_000L, "UTC")
        assertEquals("1000", db.meta().get(META_FIRST_USE))
        assertEquals("Europe/London", db.meta().get(META_ZONE))
    }

    @Test fun X4_rows_round_trip_to_the_same_pattern() = runBlocking {
        db.seedIfEmpty(1L, LONDON.id)
        val got = db.pattern().all().map { it.toSlot() }
        assertEquals(WeekB.slots.size, got.size)
        assertEquals(emptyList(), WeekB.slots - got.toSet(), "in the seed but not read back")
        assertEquals(emptyList(), got - WeekB.slots.toSet(), "read back but not in the seed")
    }

    @Test fun home_now_and_next_come_from_the_stored_pattern() = runBlocking {
        db.seedIfEmpty(1L, LONDON.id)
        val stored = db.pattern().all().map { it.toSlot() }
        val nn = nowAndNext(stored, at(LocalDate.of(2026, 10, 19), LocalTime.of(9, 30)))
        assertNotNull(nn.now)
        assertTrue(nn.now!!.slot.title.startsWith("Randomness and Computation lecture"))
        assertEquals("Walk", nn.next?.slot?.title)
    }
}
