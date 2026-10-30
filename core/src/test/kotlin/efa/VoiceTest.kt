package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class VoiceTest {
    private val today = LocalDate.of(2026, 11, 2)      // a Monday

    private fun add(s: String, vocab: List<String> = emptyList()) = assertIs<VoiceIntent.AddTask>(parseVoice(s, today, vocab))

    @Test fun V1_assignment_due_friday_heavy_gives_a_title_a_day_and_a_heaviness() {
        val a = add("assignment due Friday, heavy")
        assertEquals("Assignment", a.title); assertEquals(LocalDate.of(2026, 11, 6), a.day); assertEquals(8, a.heaviness); assertNull(a.time)
    }

    @Test fun V1_a_time_a_date_and_a_number_are_all_read() {
        val a = add("GT assignment due 4th of November at 5pm heaviness 6")
        assertEquals(LocalDate.of(2026, 11, 4), a.day); assertEquals(LocalTime.of(17, 0), a.time); assertEquals(6, a.heaviness); assertEquals("Gt assignment", a.title)
    }

    @Test fun V1_tomorrow_today_and_next_week_days() {
        assertEquals(today.plusDays(1), add("essay due tomorrow").day); assertEquals(today, add("essay due today").day)
        assertEquals(LocalDate.of(2026, 11, 9), add("essay due Monday").day)                 // today is Monday, so the next one
        assertEquals(LocalDate.of(2026, 11, 13), add("essay due next Friday").day)
    }

    @Test fun V1_nothing_said_about_heaviness_or_time_stays_empty_so_quick_adds_defaults_apply() {
        val a = add("report due Wednesday"); assertNull(a.heaviness); assertNull(a.time)
    }

    @Test fun V1_words_for_heaviness_map_to_numbers() {
        assertEquals(1, add("note due friday tiny").heaviness); assertEquals(10, add("project due friday huge").heaviness); assertEquals(5, add("quiz due friday medium").heaviness)
    }

    @Test fun V1_noon_and_midnight_and_24_hour_times() {
        assertEquals(LocalTime.NOON, add("essay due friday at noon").time); assertEquals(LocalTime.of(23, 59), add("essay due friday midnight").time)
        assertEquals(LocalTime.of(14, 30), add("essay due friday 14:30").time); assertEquals(LocalTime.of(9, 15), add("essay due friday 9:15 am").time)
    }

    @Test fun V2_module_names_and_own_words_come_out_with_their_spelling() {
        val a = add("randomness and computation problem sheet due friday", listOf("Randomness and Computation", "GT"))
        assertEquals("Randomness and Computation problem sheet", a.title)
        assertEquals("GT assignment", add("gt assignment due friday", listOf("GT")).title)
    }

    @Test fun V2_the_vocabulary_comes_from_the_timetable() {
        val v = voiceVocabulary(WeekB.slots)
        assertTrue("Neural Computation" in v); assertTrue("Randomness and Computation" in v)
    }

    @Test fun V3_voice_opens_screens() {
        assertEquals(VoiceTarget.BUFFER, assertIs<VoiceIntent.Go>(parseVoice("show the Buffer", today)).target)
        assertEquals(VoiceTarget.SHOPPING, assertIs<VoiceIntent.Go>(parseVoice("shopping list", today)).target)
        assertEquals(VoiceTarget.WIDGETS, assertIs<VoiceIntent.Go>(parseVoice("edit widgets", today)).target)
        assertEquals(VoiceTarget.LIBRARY, assertIs<VoiceIntent.Go>(parseVoice("plan the library", today)).target)
        assertEquals(VoiceTarget.MONEY, assertIs<VoiceIntent.Go>(parseVoice("free to spend", today)).target)
    }

    @Test fun V3_V4_i_need_a_day_off_opens_the_request_and_nothing_more() {
        val g = assertIs<VoiceIntent.Go>(parseVoice("I need a day off", today))
        assertEquals(VoiceTarget.DAY_OFF, g.target); assertTrue(g.target.loosening)
        assertEquals(VoiceTarget.BLOCKLIST, assertIs<VoiceIntent.Go>(parseVoice("change the block list", today)).target)
    }

    @Test fun V4_no_voice_result_can_finish_a_loosening() {
        // The only things voice can produce are: a task to be shown on a card, a screen to open, or nothing. None of them plans a day off or edits the block list.
        // This `when` has no else: a new kind of result, such as one that plans a day off, would stop it compiling until this test is thought about.
        fun kind(i: VoiceIntent) = when (i) { is VoiceIntent.AddTask -> "task for a card"; is VoiceIntent.Go -> "open a screen"; is VoiceIntent.Unknown -> "nothing" }
        assertEquals(setOf("task for a card", "open a screen", "nothing"), listOf(add("essay due friday"), parseVoice("buffer", today), parseVoice("x", today)).map(::kind).toSet())
        assertTrue(VoiceTarget.entries.filter { it.loosening }.toSet() == setOf(VoiceTarget.DAY_OFF, VoiceTarget.BLOCKLIST))
    }

    @Test fun V2_what_was_not_understood_is_not_guessed() {
        assertIs<VoiceIntent.Unknown>(parseVoice("", today)); assertIs<VoiceIntent.Unknown>(parseVoice("purple monkey dishwasher", today))
        assertIs<VoiceIntent.Unknown>(parseVoice("due friday", today))                        // no title
    }

    @Test fun G7_E10_voice_is_off_in_lectures_and_the_library_when_the_setting_is_on_and_never_otherwise() {
        val lecture = PatternSlot(DayOfWeek.MONDAY, 600, 660, "Lecture", "lecture", "Haworth", false, "")
        val library = PatternSlot(DayOfWeek.MONDAY, 700, 760, "Study", "study", LIBRARY_VENUE, false, "")
        val home = PatternSlot(DayOfWeek.MONDAY, 800, 860, "Study", "study", "", false, "")
        assertFalse(voiceAllowed(lecture, true)); assertFalse(voiceAllowed(library, true)); assertTrue(voiceAllowed(home, true)); assertTrue(voiceAllowed(null, true))
        assertTrue(voiceAllowed(lecture, false)); assertTrue(voiceAllowed(library, false))
    }

    @Test fun V1_a_date_that_is_not_real_is_not_read() {
        assertNull(add("essay due 31 november").day)
    }
}
