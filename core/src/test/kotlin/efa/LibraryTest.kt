package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class LibraryTest {
    private val slots = WeekB.slots
    private val hours = LibraryHours(8 * 60, 22 * 60)
    private val tue = LocalDate.of(2026, 11, 3)
    private val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(9, 0))

    private fun withLibrary(start: LocalDate = tue, end: LocalDate = tue, h: LibraryHours = hours, base: List<PatternSlot> = slots) = base + libraryItems(base, start, end, h)

    private fun work(list: List<PatternSlot>, d: LocalDate) = slotsFor(list, d).filter { it.slot.type in CUTTABLE_WORK }

    @Test fun C2_work_blocks_inside_the_opening_hours_move_to_the_library() {
        val after = work(withLibrary(), tue)
        assertTrue(after.isNotEmpty())
        assertTrue(after.any { it.slot.venue == LIBRARY_VENUE })
        assertTrue(after.filter { it.slot.venue != LIBRARY_VENUE }.all { it.slot.endMin > hours.closeMin || it.slot.startMin < hours.openMin || it.slot.endMin - it.slot.startMin < LIBRARY_MIN_BLOCK_MIN })
    }

    @Test fun C2_a_stretch_of_work_gets_a_walk_each_way_of_fifteen_minutes_taken_from_the_work() {
        val day = libraryDay(slots, tue, hours)
        val walks = day.items.filter { isLibraryWalkSlot(it) }
        assertEquals(day.walks, walks.size)
        assertTrue(walks.all { it.endMin - it.startMin == LIBRARY_WALK_MIN })
        assertEquals(day.walks * LIBRARY_WALK_MIN, day.lostMin)
        val before = work(slots, tue).sumOf { it.slot.endMin - it.slot.startMin }
        val after = work(withLibrary(), tue).sumOf { it.slot.endMin - it.slot.startMin }
        assertEquals(before - day.lostMin, after)
    }

    @Test fun C2_a_lecture_between_two_stretches_gives_each_stretch_its_own_walks() {
        val thu = LocalDate.of(2026, 11, 5)
        val day = libraryDay(slots, thu, hours)
        assertTrue(day.walks >= 4, "walks ${day.walks}")
        assertEquals(day.walks, day.items.count { it.title == WALK_TO_LIBRARY } + day.items.count { it.title == WALK_FROM_LIBRARY })
    }

    @Test fun C2_nothing_overlaps_after_the_move() {
        for (d in 0L..6L) {
            val date = LocalDate.of(2026, 11, 2).plusDays(d)
            val list = slotsFor(withLibrary(date, date), date).sortedBy { it.slot.startMin }
            for ((a, b) in list.zipWithNext()) assertTrue(a.slot.endMin <= b.slot.startMin, "$date ${a.slot.title} overlaps ${b.slot.title}")
        }
    }

    @Test fun C2_a_moved_block_keeps_its_type_so_blocking_is_the_same() {
        val before = work(slots, tue).map { it.slot.type }.distinct().sorted()
        val after = work(withLibrary(), tue).map { it.slot.type }.distinct().sorted()
        assertEquals(before, after)
    }

    @Test fun O11_a_block_outside_the_opening_hours_stays_at_home_and_is_named() {
        val late = LibraryHours(8 * 60, 15 * 60)
        val day = libraryDay(slots, tue, late)
        assertTrue(day.stayedHome.isNotEmpty())
        assertTrue(work(withLibrary(h = late), tue).any { it.slot.venue != LIBRARY_VENUE })
    }

    @Test fun O11_hours_are_read_once_from_text_and_rubbish_is_no_answer() {
        assertEquals(LibraryHours(480, 1320), parseLibraryHours("08:00-22:00"))
        assertEquals(LibraryHours(0, 1440), parseLibraryHours("00:00-24:00"))
        assertNull(parseLibraryHours(null)); assertNull(parseLibraryHours("late")); assertNull(parseLibraryHours("22:00-08:00")); assertNull(parseLibraryHours("08:00-25:00"))
        assertEquals("08:00-22:00", LibraryHours(480, 1320).text())
    }

    @Test fun O13_meals_football_lectures_and_routines_do_not_move() {
        val keep = { l: List<PatternSlot> -> slotsFor(l, tue).filter { it.slot.type !in CUTTABLE_WORK && !isLibraryWalkSlot(it.slot) }.map { it.slot.title to it.slot.startMin } }
        assertEquals(keep(slots), keep(withLibrary()))
        val sat = LocalDate.of(2026, 11, 7)
        val ref = slotsFor(slots, sat).filter { it.slot.type in setOf("meal", "free", "chore") }.map { it.slot.title to it.slot.startMin }
        assertEquals(ref, slotsFor(withLibrary(sat, sat), sat).filter { it.slot.type in setOf("meal", "free", "chore") }.map { it.slot.title to it.slot.startMin })
    }

    @Test fun C1_PW1_a_period_is_one_to_fourteen_days_starting_today_or_later() {
        val today = LocalDate.of(2026, 11, 2)
        assertNull(checkLibraryPeriod(today, today, today)); assertNull(checkLibraryPeriod(today, today.plusDays(13), today))
        assertNotNull(checkLibraryPeriod(today, today.plusDays(14), today)); assertNotNull(checkLibraryPeriod(today.minusDays(1), today, today))
        assertNotNull(checkLibraryPeriod(today.plusDays(2), today, today))
    }

    @Test fun C2_the_minutes_the_walks_took_come_back_as_catch_up_work_after_the_period() {
        val demand = libraryDemand(slots, tue, tue, hours, now)
        assertEquals(1, demand.size)
        val lost = libraryDay(slots, tue, hours).lostMin
        assertEquals(lost / 60.0, demand.single().hours, 0.0001)
        assertTrue(demand.single().due.toLocalDate() > tue)
        assertEquals("library:2026-11-03", demand.single().key)
    }

    @Test fun C5_a_day_off_inside_a_library_period_has_no_library_and_takes_its_walks_away() {
        val base = slots + dayOffMarker(tue)
        assertTrue(libraryDay(base, tue, hours).items.isEmpty())
        val moved = withLibrary() + dayOffMarker(tue)                       // the day off arrives after the library plan
        val day = slotsFor(moved, tue)
        assertTrue(day.none { it.slot.type in CUTTABLE_WORK || isLibraryWalkSlot(it.slot) })
    }

    @Test fun C2_a_library_plan_is_the_same_each_time_it_is_worked_out() {
        assertEquals(libraryItems(slots, tue, tue.plusDays(4), hours), libraryItems(slots, tue, tue.plusDays(4), hours))
    }

    @Test fun C2_an_import_that_cut_a_work_block_keeps_its_cut_at_the_library() {
        val cut = PatternSlot(tue.dayOfWeek, 0, 0, "x", "errand", "", false, "", date = tue)
        val first = work(slots, tue).first().slot
        val event = cut.copy(startMin = first.startMin + 20, endMin = first.startMin + 50, title = "Dentist")
        val base = slots + event
        val after = work(base + libraryItems(base, tue, tue, hours), tue)
        assertTrue(after.none { it.slot.startMin < event.endMin && it.slot.endMin > event.startMin })
    }
}
