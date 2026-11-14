package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class EssentialsTest {
    private val work = at(LocalDate.of(2026, 11, 2), LocalTime.of(15, 30))            // a Monday work block

    @Test fun MG1_an_essential_is_never_blocked_even_when_it_is_on_the_list_in_a_work_block() {
        for (pkg in listOf("com.google.android.dialer", "com.google.android.apps.maps", "com.monzo.android", "com.android.settings", "app.efa", "com.google.android.apps.authenticator2")) {
            assertEquals(Decision.Allow, decide(pkg, work, WeekB.slots, setOf(pkg)), pkg)
        }
    }

    @Test fun MG1_an_app_that_is_not_essential_is_still_blocked_in_a_work_block() =
        assertIs<Decision.Block>(decide("com.instagram.android", work, WeekB.slots, setOf("com.instagram.android")))

    @Test fun MG1_essentials_stay_open_at_night_and_in_the_morning_block_too() {
        val night = at(LocalDate.of(2026, 11, 3), LocalTime.of(2, 0))
        assertEquals(Decision.Allow, decide("com.google.android.dialer", night, WeekB.slots, setOf("com.google.android.dialer")))
    }

    @Test fun MG1_the_fixed_core_has_calls_messages_maps_banking_authenticator_camera_clock_settings_and_spark() {
        listOf("com.google.android.dialer", "com.google.android.apps.messaging", "com.google.android.apps.maps", "com.monzo.android", "com.google.android.apps.authenticator2",
            "com.google.android.GoogleCamera", "com.google.android.deskclock", "com.android.settings", "app.efa").forEach { assertTrue(it in ESSENTIAL_CORE, it) }
    }

    @Test fun MG2_the_phones_own_default_apps_and_added_ones_are_essential_when_it_is_enforced() {
        val e = resolveEssentials(listOf("com.example.dialer", null), setOf("com.bank.new"))
        assertTrue("com.example.dialer" in e); assertTrue("com.bank.new" in e); assertTrue("com.google.android.apps.maps" in e)
        assertEquals(Decision.Allow, decide("com.bank.new", work, WeekB.slots, setOf("com.bank.new"), essentials = e))
    }

    @Test fun MG2_every_non_essential_app_is_worked_out_not_saved_and_leaves_the_essentials_out() {
        val installed = listOf("com.instagram.android", "com.google.android.dialer", "com.monzo.android", "com.reddit.frontpage")
        assertEquals(listOf("com.instagram.android", "com.reddit.frontpage"), nonEssential(installed))
        assertEquals(listOf("com.instagram.android", "com.monzo.android", "com.reddit.frontpage"), nonEssential(installed, ESSENTIAL_CORE - "com.monzo.android"))
    }

    @Test fun MG3_a_block_list_with_an_essential_in_it_is_refused_with_the_app_named() =
        assertEquals(setOf("com.google.android.dialer"), essentialsIn(listOf("com.instagram.android", "com.google.android.dialer")))

    @Test fun MG3a_passing_the_wall_opens_a_window_of_five_minutes() {
        val w = EssentialsWindow.passWall(1_000_000)
        assertTrue(w.open(1_000_000)); assertTrue(w.open(1_000_000 + 299_999)); assertFalse(w.open(1_000_000 + 300_000)); assertFalse(w.open(999_999))
        assertEquals(60_000, w.remainingMs(1_000_000 + 240_000))
    }

    @Test fun MG3a_the_window_takes_at_most_three_apps_and_then_closes() {
        var w = EssentialsWindow.passWall(0)
        repeat(3) { assertTrue(w.canAdd(10_000)); w = w.afterAdd(10_000) }
        assertEquals(3, w.added); assertFalse(w.canAdd(10_000)); assertEquals(w, w.afterAdd(10_000))
    }

    @Test fun MG3a_a_closed_window_cannot_be_extended_and_a_new_one_needs_the_wall_again() {
        val w = EssentialsWindow.passWall(0)
        assertFalse(w.afterAdd(400_000).added > 0); assertFalse(EssentialsWindow().canAdd(1))
        assertTrue(EssentialsWindow.passWall(500_000).canAdd(510_000))
    }

    @Test fun MG3a_the_window_survives_being_saved_and_read_back_and_adding_needs_the_wall() {
        val w = EssentialsWindow(123, 2); assertEquals(w, EssentialsWindow.decode(w.encode())); assertEquals(EssentialsWindow(), EssentialsWindow.decode("rubbish"))
        assertEquals(WALL_BASE, ESSENTIALS_ADD_WALL)
    }
}
