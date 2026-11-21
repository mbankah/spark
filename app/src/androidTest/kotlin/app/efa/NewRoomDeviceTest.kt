package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/** L6: a room that changed is asked about. */
@RunWith(AndroidJUnit4::class)
class NewRoomStorageTest {
    private lateinit var db: EfaDb
    private val monday = LocalDate.of(2026, 11, 2)
    private fun lecture(venue: String, date: LocalDate = monday.plusDays(2)) = PatternSlot(date.dayOfWeek, 9 * 60, 10 * 60, "Game Theory lecture - $venue", "lecture", venue, false, "", date = date)

    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build() }
    @After fun close() = db.close()

    @Test fun L6_a_lecture_in_a_room_that_is_not_in_the_directory_is_asked_about_and_stops_being_asked_once_placed() = runBlocking {
        val places = PlaceRepo(db)
        val slots = listOf(lecture("Nowhere Hall NH-999"))
        val ask = places.unplaced(slots, monday)
        assertEquals("Nowhere Hall NH-999", ask?.venueText)
        places.setVenuePoint("Nowhere Hall NH-999", 52.45, -1.93, 1)
        assertNull(places.unplaced(slots, monday))
    }

    @Test fun L5_a_room_the_directory_knows_is_not_asked_about() = runBlocking {
        val known = WeekB.slots.first { it.type == "lecture" && it.venue.isNotBlank() }
        assertNull(PlaceRepo(db).unplaced(listOf(known), monday))
    }

    @Test fun L6_a_room_that_comes_later_than_two_weeks_is_left_until_then() = runBlocking {
        assertNull(PlaceRepo(db).unplaced(listOf(lecture("Nowhere Hall NH-999", monday.plusDays(30))), monday))
    }

    @Test fun L6_rooms_for_things_that_are_not_places_to_attend_are_not_asked_about() = runBlocking {
        val errand = PatternSlot(monday.dayOfWeek, 600, 660, "Dentist", "errand", "Some Clinic", false, "", date = monday)
        assertNull(PlaceRepo(db).unplaced(listOf(errand), monday))
    }
}

@RunWith(AndroidJUnit4::class)
class NewRoomScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun L6_home_asks_where_a_new_room_is_and_opens_the_map_when_tapped() {
        var tapped = false
        rule.setContent { EfaTheme { HomeScreen(HomeState(date = "Mon 2 Nov", phone = "Phone closed", phoneOpen = false, time = "10:00", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null, freeToSpendPence = null, newRoom = "Nowhere Hall NH-999"), {}, {}, onNewRoom = { tapped = true }) } }
        rule.onNodeWithText("Where is Nowhere Hall NH-999?").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Put Nowhere Hall NH-999 on the map").performScrollTo().performClick(); assertTrue(tapped)
    }
}
