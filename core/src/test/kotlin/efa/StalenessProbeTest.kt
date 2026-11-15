package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** What happens to a modifier after it is applied, and whether what is stored survives the round trip. */
class StalenessProbeTest {
    private val base = WeekB.slots
    private val today = LocalDate.of(2026, 11, 2)
    private val now = at(today, LocalTime.of(9, 0))
    private fun item(t: String) = mapOf("title" to t, "due" to "2026-11-13", "due_time" to "17:00", "size_h" to "8.0")

    @Test fun ARCH_titles_with_awkward_characters_survive_storage() {
        for (t in listOf("GT | RC", "a=b", "line\nbreak", "tab\there", "back\\slash", "carriage\rreturn", "emoji 📚 and é", "  spaces  ", "x".repeat(300), "end\\", "\\n literal")) {
            val inst = Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 11, 13), mapOf("lectures" to "none"), listOf(item(t)))
            assertEquals(inst, decodeInstance(encodeInstance(inst)), "instance title [$t]")
            val slot = PatternSlot(today.dayOfWeek, 600, 660, "$t: draft", "study", "v|e", false, "${MOD_NOTE}n\nm", date = today)
            assertEquals(listOf(slot), decodeSlots(encodeSlots(listOf(slot))), "slot title [$t]")
        }
    }

    @Test fun ARCH_recommended_settings_are_always_valid_and_applicable() {
        for (p in listOf(Preset.CATCH_UP, Preset.PERIOD_OFF, Preset.LECTURE_FREE, Preset.FOCUS)) for (len in listOf(1, 3, 7, 14)) {
            val start = today.plusDays(1); val end = start.plusDays(len - 1L)
            val rec = recommend(p, base, start, end, backlogH = 12.0)
            val items = if (p == Preset.FOCUS) listOf(item("P").plus("due" to end.plusDays(10).toString()).plus("size_h" to "20.0")) else emptyList()
            val inst = Instance(p, start, end, rec.filterKeys { k -> ModDefs.settings(p).any { it.key == k } }.mapValues { it.value.value }, items)
            val v = validate(inst, today)
            assertEquals(emptyList(), v, "$p $len days: $v")
            val pv = previewOf(inst, base, emptyList(), now)
            assertTrue(pv.canApply, "$p $len days: ${pv.problems}")
        }
    }

    // A modifier is compiled when it is applied. The calendar can change afterwards (a day off, an import), and the stored slots must not end up on top of it.
    private fun applyAssignment(): List<PatternSlot> {
        val inst = Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 11, 13), emptyMap(), listOf(item("GT")))
        return previewOf(inst, base, emptyList(), now).compiled.slots
    }

    @Test fun ARCH_a_day_off_planned_after_an_assignment_takes_its_sessions_off_that_day() {
        val sessions = applyAssignment(); val day = sessions.first().date!!
        val core = base + dayOffMarker(day)
        val shown = reconcile(core, sessions, allowOnDayOff = false).kept
        assertTrue(slotsFor(core + shown, day).none { it.slot.title.startsWith("GT:") }, "sessions on a day off")
    }

    @Test fun ARCH_an_import_over_a_session_does_not_leave_two_things_at_once() {
        val sessions = applyAssignment(); val s = sessions.first()
        val imported = PatternSlot(s.date!!.dayOfWeek, s.startMin, s.startMin + 45, "Dentist", "event", "", false, "", date = s.date)
        val core = base + imported
        val shown = reconcile(core, sessions, allowOnDayOff = false).kept
        val day = slotsFor(core + shown, s.date!!).filter { it.slot.type != "free" }.sortedBy { it.slot.startMin }
        for ((a, b) in day.zipWithNext()) assertFalse(b.slot.startMin < a.slot.endMin, "${a.slot.title} overlaps ${b.slot.title}")
    }

    @Test fun ARCH_reconcile_leaves_a_still_valid_plan_untouched() {
        val sessions = applyAssignment()
        val r = reconcile(base, sessions, allowOnDayOff = false)
        assertEquals(sessions, r.kept); assertEquals(0, r.dropped)
    }
}

class StalenessHazardTest {
    @Test fun ARCH_without_reconcile_a_later_day_off_would_leave_the_sessions_on_it() {
        val today = LocalDate.of(2026, 11, 2)
        val inst = Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 11, 13), emptyMap(), listOf(mapOf("title" to "GT", "due" to "2026-11-13", "due_time" to "17:00", "size_h" to "8.0")))
        val sessions = previewOf(inst, WeekB.slots, emptyList(), at(today, LocalTime.of(9, 0))).compiled.slots
        val day = sessions.first().date!!
        assertTrue(slotsFor(WeekB.slots + dayOffMarker(day) + sessions, day).any { it.slot.title.startsWith("GT:") })   // the hazard is real
    }
}
