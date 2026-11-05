package efa

import java.time.LocalDate
import kotlin.test.*

class LauncherLookTest {
    private val mon = LocalDate.of(2026, 11, 2)

    @Test fun U9_icons_and_grid_are_off_by_default() { val l = LauncherLook(); assertFalse(l.effective(mon).icons); assertFalse(l.effective(mon).grid) }

    @Test fun U9_turning_icons_on_takes_effect_from_the_next_day_not_today() {
        val l = LauncherLook().change(mon, icons = true)
        assertFalse(l.effective(mon).icons); assertFalse(l.effective(mon).grid)
        assertTrue(l.effective(mon.plusDays(1)).icons)
        assertEquals("icons on from Tue 3", l.note(mon))
    }

    @Test fun U9_turning_them_off_is_also_a_next_day_decision() {
        val on = LauncherLook(icons = true, grid = true)
        val l = on.change(mon, icons = false)
        assertTrue(l.effective(mon).icons); assertFalse(l.effective(mon.plusDays(1)).icons); assertTrue(l.effective(mon.plusDays(1)).grid)
    }

    @Test fun U9_asking_for_what_is_already_in_force_cancels_a_pending_change() {
        val l = LauncherLook().change(mon, icons = true).change(mon, icons = false)
        assertNull(l.note(mon)); assertFalse(l.effective(mon.plusDays(1)).icons)
    }

    @Test fun U9_icons_and_grid_can_be_changed_together_and_each_keeps_the_other_pending() {
        val l = LauncherLook().change(mon, icons = true).change(mon, grid = true)
        assertTrue(l.effective(mon.plusDays(1)).icons); assertTrue(l.effective(mon.plusDays(1)).grid); assertEquals("icons on and grid on from Tue 3", l.note(mon))
    }

    @Test fun U9_a_pending_change_lasts_until_its_day_and_then_is_just_the_setting() {
        val l = LauncherLook().change(mon, grid = true).effective(mon.plusDays(2))
        assertTrue(l.grid); assertNull(l.pendingFrom)
    }

    @Test fun U9_the_setting_survives_being_saved_and_read_back() {
        val l = LauncherLook(icons = true).change(mon, grid = true)
        assertEquals(l, LauncherLook.decode(l.encode())); assertEquals(LauncherLook(), LauncherLook.decode(null)); assertEquals(LauncherLook(), LauncherLook.decode("rubbish"))
    }

    @Test fun U9_badges_never_appear() = assertFalse(LAUNCHER_SHOWS_BADGES)
}
