package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
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
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** Attendance on a device: the directory, the home point, the venue correction log and the location rules, with a real database. */
@RunWith(AndroidJUnit4::class)
class AttendanceRepoTest {
    private lateinit var db: EfaDb
    private lateinit var repo: BlockRepo
    private lateinit var places: PlaceRepo
    private lateinit var ctx: Context
    private val monday = LocalDate.of(2026, 10, 19)
    private val lecture get() = slotsFor(WeekB.slots, monday).first { it.slot.title.startsWith("Randomness and Computation lecture") }   // Murray Learning Centre, 09:00 to 10:00
    private val murray = Venues.find("Murray Learning Centre")!!
    private val home = Pair(52.4620, -1.9100)
    private fun inWindow() = at(monday, LocalTime.of(9, 20))

    @Before fun open() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, EfaDb::class.java).build()
        repo = BlockRepo(ctx, db, WeekB.slots); places = PlaceRepo(db)
        File(ctx.filesDir, "thumbs").deleteRecursively()
    }
    @After fun close() = db.close()

    @Test fun L2_a_photo_near_the_building_is_full_proof_and_stores_only_a_band() = runBlocking {
        places.setHome(home.first, home.second)
        val v = repo.submitPhoto(lecture, jpeg(21), inWindow(), Fix(murray.lat + 0.0003, murray.lon, 20.0))
        assertTrue(v.verified); assertFalse(v.weaker)
        val rec = db.photos().withThumbs().single()
        assertEquals("near-venue", rec.locationResult); assertEquals("<50m", rec.distanceBand)
        assertTrue(PhotoRow::class.java.declaredFields.none { f -> listOf("lat", "lon", "coord", "gps").any { f.name.lowercase().contains(it) } })   // ST7
    }

    @Test fun L2_a_photo_away_from_the_building_but_not_at_home_is_weaker_proof() = runBlocking {
        places.setHome(home.first, home.second)
        val v = repo.submitPhoto(lecture, jpeg(22), inWindow(), Fix(murray.lat + 0.0020, murray.lon, 20.0))
        assertTrue(v.verified); assertTrue(v.weaker)
        assertEquals("away-from-home", db.photos().withThumbs().single().locationResult)
    }

    @Test fun L3_a_photo_taken_at_home_is_rejected_and_stores_nothing() = runBlocking {
        val h = places.setHome(home.first, home.second)
        val v = repo.submitPhoto(lecture, jpeg(23), inWindow(), Fix(h.first, h.second, 20.0))
        assertEquals(Reason.REJECTED_NEAR_HOME, v.reason)
        assertEquals(0, db.photos().fingerprints().size); assertNull(repo.load(lecture)?.takeIf { it.verified })
    }

    @Test fun E11_no_fix_or_an_imprecise_fix_asks_for_a_retake() = runBlocking {
        places.setHome(home.first, home.second)
        assertEquals(Reason.NO_LOCATION, repo.submitPhoto(lecture, jpeg(24), inWindow(), null).reason)
        assertEquals(Reason.NO_LOCATION, repo.submitPhoto(lecture, jpeg(24), inWindow(), Fix(murray.lat, murray.lon, 600.0)).reason)
    }

    @Test fun L2_without_a_home_point_only_the_building_check_can_pass() = runBlocking {
        assertEquals(Reason.HOME_NOT_SET, repo.submitPhoto(lecture, jpeg(25), inWindow(), Fix(52.5, -1.8, 20.0)).reason)
        assertTrue(repo.submitPhoto(lecture, jpeg(26), inWindow(), Fix(murray.lat, murray.lon, 20.0)).verified)
    }

    @Test fun L1_attendance_photo_outside_the_lecture_window_is_lost() = runBlocking {
        places.setHome(home.first, home.second)
        assertEquals(Reason.OUTSIDE_WINDOW, repo.submitPhoto(lecture, jpeg(27), at(monday, LocalTime.of(10, 5)), Fix(murray.lat, murray.lon, 20.0)).reason)
        assertEquals(Reason.TOO_EARLY, repo.submitPhoto(lecture, jpeg(28), at(monday, LocalTime.of(8, 59)), Fix(murray.lat, murray.lon, 20.0)).reason)
    }

    @Test fun L8_home_is_saved_rounded_to_about_100_m_and_can_be_moved() = runBlocking {
        assertNull(places.home())
        assertEquals(Pair(52.462, -1.91), places.setHome(52.46234, -1.90988))
        assertEquals(Pair(52.462, -1.91), places.home())
        places.setHome(52.4501, -1.9302); assertEquals(Pair(52.45, -1.93), places.home())
    }

    @Test fun L4_L5_venues_come_from_the_directory_and_every_one_has_a_point() = runBlocking {
        val venues = WeekB.slots.filter { it.type == "lecture" || it.type == "tutorial" }.map { it.venue }
        val st = places.statuses(venues)
        assertEquals(7, st.size); assertTrue(st.all { it.point != null && it.source == "OpenStreetMap" })
    }

    @Test fun L5_a_venue_the_user_places_overrides_the_directory_and_the_correction_is_logged() = runBlocking {
        val venue = "Murray Learning Centre LC-UG05"
        places.setVenuePoint(venue, 52.4400, -1.9500, 1_000L)
        places.setVenuePoint(venue, 52.4410, -1.9510, 2_000L)
        assertEquals(Pair(52.4410, -1.9510), places.pointFor(venue))
        assertEquals(2, db.venues().all().size, "both placements are kept as the log")
        assertEquals("placed by you", places.statuses(listOf(venue)).single().source)
        places.setHome(home.first, home.second)
        assertTrue(repo.submitPhoto(lecture, jpeg(29), inWindow(), Fix(52.4410, -1.9510, 20.0)).verified, "the corrected point is the one used")
    }

    @Test fun A8_ST7_the_location_read_is_never_stored_anywhere() = runBlocking {
        places.setHome(home.first, home.second)
        repo.submitPhoto(lecture, jpeg(30), inWindow(), Fix(murray.lat + 0.0003, murray.lon, 20.0))
        val text = db.openHelper.readableDatabase.query("SELECT group_concat(blockKey || locationResult || distanceBand, ' ') FROM photo").use { it.moveToFirst(); it.getString(0) }
        assertFalse(text.contains(Regex("\\d{2}\\.\\d{3,}")), "no coordinates in the photo record: $text")
    }
}

/** The setup and map screens. */
@RunWith(AndroidJUnit4::class)
class SetupScreenTest {
    @get:Rule val rule = createComposeRule()

    private val venues = listOf(
        VenueStatus("Haworth HWTH-101", "Haworth", Pair(52.45, -1.934), "OpenStreetMap"),
        VenueStatus("Mystery Hall 1", "Mystery Hall 1", null, "not found"),
    )

    @Test fun L5_an_unmatched_venue_is_listed_first_and_asks_to_be_placed() {
        var tapped: VenueStatus? = null
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, venues), {}, { tapped = it }, {}) } }
        rule.onNodeWithText("Not found. Place it on the map.").assertExists()
        rule.onNodeWithText("Mystery Hall 1").performScrollTo().performClick()
        assertEquals("Mystery Hall 1", tapped?.venueText)
    }

    @Test fun L8_setup_says_home_is_not_set_and_never_a_house() {
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, venues), {}, {}, {}) } }
        rule.onNodeWithText("Not set. Lecture attendance outside the building check needs it.").assertExists()
        rule.onNodeWithText("Set home on the map").assertExists()
    }

    @Test fun O4_setup_lists_venues_within_250_m_of_home() {
        val murray = Venues.find("Murray Learning Centre")!!
        rule.setContent { EfaTheme { SetupScreen(PlacesState(Pair(murray.lat + 0.0005, murray.lon), venues), {}, {}, {}) } }
        rule.onNodeWithText("use the building check only", substring = true).assertExists()
    }

    @Test fun A8_the_map_picker_returns_the_centre_of_the_view_and_reads_no_location() {
        var got: Pair<Double, Double>? = null
        rule.setContent { EfaTheme { MapPicker("Set home", "hint", Pair(52.4508, -1.9305), 16.0, { la, lo -> got = Pair(la, lo) }, {}) } }
        rule.waitForIdle()
        rule.onNodeWithText("Use this point").performClick()
        rule.waitUntil(5_000) { got != null }
        assertEquals(52.4508, got!!.first, 0.002); assertEquals(-1.9305, got!!.second, 0.002)
    }
}

@RunWith(AndroidJUnit4::class)
class VenueMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_3_to_4_keeps_blocks_and_adds_the_venue_log() {
        helper.createDatabase("mig34.db", 3).apply {
            execSQL("INSERT INTO block (key,title,type,startMs,endMs,zone,verified,creditedMin,recovered,weaker) VALUES ('k','t','study',1,2,'Europe/London',1,60,0,0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig34.db", 4, true, MIGRATION_3_4)
        db.query("SELECT COUNT(*) FROM block WHERE verified = 1").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO venue_point (venueKey,lat,lon,atMs,source) VALUES ('Watson',52.45,-1.93,1,'user')")
        db.query("SELECT COUNT(*) FROM venue_point").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
}
