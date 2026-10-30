package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class WidgetsTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val now = at(today, LocalTime.of(10, 0))
    private val defaults = defaultPlacements(today)

    private fun data(updated: java.time.ZonedDateTime? = now, pence: Int? = 1250) = WidgetData(
        BufferState.Value(BufferResult(24.0, emptyList(), false, "Wed 4 Nov 17:00")), "Neural Computation lecture, until 12:00", listOf("12:30 Lunch", "13:00 Randomness lecture", "15:10 Notes", "17:50 Battl.io"),
        pence, null, 12, listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), updated)

    @Test fun WG1_the_default_home_is_the_first_four_widgets() {
        assertEquals(listOf(WidgetKind.BUFFER, WidgetKind.NOW_NEXT, WidgetKind.FREE_TO_SPEND, WidgetKind.DAY_PLAN), defaults.sortedBy { it.position }.map { it.kind })
        assertTrue(defaults.all { shownOn(it, today).shown })
    }

    @Test fun WG2_there_are_seven_built_in_widgets() {
        assertEquals(7, WidgetKind.entries.size)
        assertEquals(listOf("Buffer", "Now and next", "Free to spend", "Day plan", "Shopping list", "Quick-add", "Week glance"), WidgetKind.entries.map { it.label })
    }

    @Test fun WG3_WG4_adding_a_widget_starts_tomorrow_and_says_so() {
        val list = addWidget(defaults, WidgetKind.SHOPPING, WidgetSize.TWO_BY_ONE, today)
        val s = shownOn(list.first { it.kind == WidgetKind.SHOPPING }, today)
        assertFalse(s.shown); assertEquals(today.plusDays(1), s.pendingFrom)
        assertTrue(shownOn(list.first { it.kind == WidgetKind.SHOPPING }, today.plusDays(1)).shown)
        assertEquals(list, addWidget(list, WidgetKind.SHOPPING, WidgetSize.TWO_BY_TWO, today))      // already there: nothing changes
    }

    @Test fun WG4_removing_a_widget_takes_effect_at_once_and_the_Buffer_cannot_be_removed() {
        val without = removeWidget(defaults, WidgetKind.DAY_PLAN)
        assertEquals(3, without.size); assertEquals(defaults, removeWidget(defaults, WidgetKind.BUFFER))
    }

    @Test fun WG4_a_bigger_size_starts_tomorrow_and_a_smaller_one_at_once() {
        val bigger = resizeWidget(defaults, WidgetKind.NOW_NEXT, WidgetSize.TWO_BY_TWO, today)
        val p = bigger.first { it.kind == WidgetKind.NOW_NEXT }
        assertEquals(WidgetSize.TWO_BY_ONE, shownOn(p, today).size); assertEquals(WidgetSize.TWO_BY_TWO, shownOn(p, today).pendingSize)
        assertEquals(WidgetSize.TWO_BY_TWO, shownOn(p, today.plusDays(1)).size)
        val smaller = resizeWidget(defaults, WidgetKind.DAY_PLAN, WidgetSize.ONE_BY_ONE, today).first { it.kind == WidgetKind.DAY_PLAN }
        assertEquals(WidgetSize.ONE_BY_ONE, shownOn(smaller, today).size); assertNull(smaller.nextSize)
    }

    @Test fun WG3_widgets_can_be_reordered_and_the_ends_stay_put() {
        val moved = moveWidget(defaults, WidgetKind.DAY_PLAN, -1).sortedBy { it.position }.map { it.kind }
        assertEquals(listOf(WidgetKind.BUFFER, WidgetKind.NOW_NEXT, WidgetKind.DAY_PLAN, WidgetKind.FREE_TO_SPEND), moved)
        assertEquals(defaults, moveWidget(defaults, WidgetKind.BUFFER, -1)); assertEquals(defaults, moveWidget(defaults, WidgetKind.DAY_PLAN, 1))
    }

    @Test fun WG2_WG7_each_widget_says_its_facts_in_plain_text_with_no_count_or_badge() {
        val d = data()
        assertEquals(listOf("Buffer +24.0h", "to Wed 4 Nov 17:00"), widgetLines(WidgetKind.BUFFER, WidgetSize.TWO_BY_ONE, d, now))
        assertEquals(listOf("Neural Computation lecture, until 12:00", "12:30 Lunch"), widgetLines(WidgetKind.NOW_NEXT, WidgetSize.TWO_BY_ONE, d, now))
        assertEquals(listOf("Free to spend", "£12.50"), widgetLines(WidgetKind.FREE_TO_SPEND, WidgetSize.TWO_BY_ONE, d, now))
        assertEquals(5, widgetLines(WidgetKind.DAY_PLAN, WidgetSize.TWO_BY_TWO, d, now).size)
        assertEquals(listOf("Shopping list", "12 left to buy"), widgetLines(WidgetKind.SHOPPING, WidgetSize.TWO_BY_ONE, d, now))
        assertEquals(8, widgetLines(WidgetKind.WEEK_GLANCE, WidgetSize.TWO_BY_TWO, d, now).size)
        val all = WidgetKind.entries.flatMap { widgetLines(it, WidgetSize.TWO_BY_TWO, d, now) }.joinToString(" ").lowercase()
        assertFalse(all.contains("held")); assertFalse(all.contains("unread")); assertFalse(all.contains("notification"))
    }

    @Test fun WG9_old_data_says_stale_since_and_never_shows_a_number() {
        val old = data(updated = now.minusMinutes(WIDGET_STALE_AFTER_MIN + 1))
        assertEquals(listOf("Free to spend", "stale since 09:49"), widgetLines(WidgetKind.FREE_TO_SPEND, WidgetSize.TWO_BY_ONE, old, now))
        assertEquals(listOf("Buffer", "stale since 09:49"), widgetLines(WidgetKind.BUFFER, WidgetSize.TWO_BY_ONE, old, now))
        assertNull(staleSince(data(), now)); assertNotNull(staleSince(null, now))
        assertEquals(listOf("Buffer", "stale: no data yet"), widgetLines(WidgetKind.BUFFER, WidgetSize.TWO_BY_ONE, null, now))
    }

    @Test fun WG9_quick_add_needs_no_data_so_it_is_never_stale() {
        assertEquals(listOf("Quick-add"), widgetLines(WidgetKind.QUICK_ADD, WidgetSize.ONE_BY_ONE, null, now))
    }

    @Test fun G6_a_widget_before_money_starts_says_recording() {
        assertEquals(listOf("Free to spend", "Recording"), widgetLines(WidgetKind.FREE_TO_SPEND, WidgetSize.TWO_BY_ONE, data(pence = null), now))
    }

    @Test fun WG8_tapping_a_widget_opens_its_full_screen() {
        assertEquals(WidgetTarget.WORKING, widgetTarget(WidgetKind.BUFFER)); assertEquals(WidgetTarget.DAY, widgetTarget(WidgetKind.DAY_PLAN))
        assertEquals(WidgetTarget.SHOPPING, widgetTarget(WidgetKind.SHOPPING)); assertEquals(WidgetTarget.MONEY, widgetTarget(WidgetKind.FREE_TO_SPEND))
        assertEquals(WidgetKind.entries.size, WidgetKind.entries.map { widgetTarget(it) }.size)
    }
}
