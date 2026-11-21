package app.efa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import efa.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** The second home page, the whole schedule (SC1), on the phone. */
@RunWith(AndroidJUnit4::class)
class SchedulePageDeviceTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 2)
    private val now = at(today, LocalTime.of(10, 30))

    private fun days(slots: List<PatternSlot> = WeekB.slots, tasks: List<OpenTask> = emptyList()) = scheduleView(slots, tasks, allocateTasks(slots, tasks, now), now, days = 14)
    private fun state(d: List<ScheduleDay>) = HomeState(date = "Mon 2 Nov", phone = "Phone closed", phoneOpen = false, time = "10:30", buffer = BufferState.NoDeadline,
        nowLabel = null, nowTitle = null, nowDetail = null, freeToSpendPence = 1250, schedule = d)

    @Test fun SC1_the_home_screen_has_a_second_page_with_the_whole_schedule() {
        rule.setContent { EfaTheme { HomeScreen(state(days()), {}, {}) } }
        rule.onNodeWithContentDescription("Home page, showing").assertExists()
        rule.onNodeWithContentDescription("Full schedule").assertDoesNotExist()
        rule.onNodeWithContentDescription("Schedule page").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Full schedule").assertExists()
        rule.onNodeWithContentDescription("Schedule page, showing").assertExists()
        rule.onNodeWithText("Schedule").assertExists()
        rule.onNodeWithText("Mon 2 Nov").assertExists(); rule.onNodeWithText("TODAY").assertExists()
    }

    @Test fun SC1_swiping_left_opens_the_schedule_and_swiping_right_returns() {
        rule.setContent { EfaTheme { HomeScreen(state(days()), {}, {}) } }
        rule.onNodeWithContentDescription("Home page, showing").assertExists()
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Full schedule").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().performTouchInput { swipeRight() }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Home page, showing").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun SC1_the_search_bar_and_the_add_button_stay_on_both_pages() {
        rule.setContent { EfaTheme { HomeScreen(state(days()), {}, {}) } }
        rule.onNodeWithContentDescription("Schedule page").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Type to open an app").assertExists(); rule.onNodeWithContentDescription("Quick add").assertExists()
    }

    @Test fun SC1_a_day_off_is_marked_and_a_deadline_is_shown_on_its_day() {
        val off = today.plusDays(1)
        val due = OpenTask(1, "GT report", at(today, LocalTime.of(17, 0)), 120)
        rule.setContent { EfaTheme { HomeScreen(state(days(WeekB.slots + dayOffMarker(off), listOf(due))), {}, {}) } }
        rule.onNodeWithContentDescription("Schedule page").performClick(); rule.waitForIdle()
        rule.onNodeWithText("GT report").assertExists(); rule.onNodeWithText("DUE 17:00").assertExists()
        rule.onNodeWithContentDescription("Full schedule").performScrollToNode(hasText("DAY OFF"))
        rule.onNodeWithText("DAY OFF").assertExists()
    }

    @Test fun SC1_the_running_block_says_NOW_and_a_finished_one_does_not() {
        val v = days()
        rule.setContent { EfaTheme { ScheduleScreen(v) } }
        val running = v.first().rows.firstOrNull { it.state == RowState.NOW && (it.kind == RowKind.WORK || it.kind == RowKind.FIXED) }
        if (running != null) rule.onNodeWithText("NOW").assertExists() else rule.onAllNodesWithText("NOW").assertCountEquals(0)
    }

    @Test fun SC1_with_no_schedule_the_page_says_so_instead_of_showing_something_wrong() {
        rule.setContent { EfaTheme { ScheduleScreen(emptyList()) } }
        rule.onNodeWithText("Schedule unavailable").assertExists()
    }

    @Test fun SC1_the_first_page_still_has_the_buffer_now_next_and_money() {
        rule.setContent { EfaTheme { HomeScreen(state(days()), {}, {}) } }
        rule.onNodeWithContentDescription("Buffer.", substring = true).assertExists()
        rule.onNodeWithContentDescription("Free to spend this week.", substring = true).assertExists()
        rule.onNodeWithText("Next", ignoreCase = true).assertExists()
    }

    @Test fun SC1_the_home_button_returns_to_the_first_page() {
        var tick by androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent { EfaTheme { HomeScreen(state(days()), {}, {}, homeTick = tick) } }
        rule.onNodeWithContentDescription("Schedule page").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Schedule page, showing").assertExists()
        tick++; rule.waitForIdle()
        rule.onNodeWithContentDescription("Home page, showing").assertExists()
    }

    // SC2: calculations are drawn as well as written, and the written working is kept.
    private fun result(available: Double, vararg tasks: Task) = (computeBuffer(available, tasks.toList(), false) as BufferState.Value).result.copy(deadlineLabel = "Thu 5 Nov 17:00")

    @Test fun SC2_the_buffer_working_shows_available_against_work_and_every_task_line() {
        val r = result(17.5, Task("Neural Computation coursework", 10.0, 8), Task("Randomness problem sheet", 3.0, 5))
        rule.setContent { EfaTheme { WorkingScreen(r) {} } }
        rule.onNodeWithText("Buffer working").assertExists(); rule.onNodeWithText("+4.5h").assertExists()
        rule.onNodeWithText("17.5h").assertExists(); assertTrue(rule.onAllNodesWithText("13.0h").fetchSemanticsNodes().size >= 2)       // the Work tile, the ring and the total
        rule.onNodeWithContentDescription("Neural Computation coursework (heaviness 8): 10.0h x 1.0 = 10.0h").assertExists()
        rule.onNodeWithContentDescription("Randomness problem sheet (heaviness 5): 3.0h x 1.0 = 3.0h").assertExists()
        rule.onNodeWithText("TO THU 5 NOV 17:00").assertExists()
    }

    @Test fun SC2_a_negative_buffer_working_says_NEGATIVE() {
        rule.setContent { EfaTheme { WorkingScreen(result(2.0, Task("Essay", 6.0, 6))) {} } }
        rule.onNodeWithText("NEGATIVE", substring = true).assertExists()
    }

    @Test fun SC2_the_money_screen_keeps_the_working_lines_and_the_sums() {
        val slots = WeekB.slots; val monday = today
        val credited = weekBlocks(slots, monday, emptyMap()).associate { it.key to (it.plannedMin * 0.5).toInt() }
        val v = app.efa.data.MoneyView(true, LocalDate.of(2026, 11, 16), weekResult(slots, monday, credited), 2750, 1)
        rule.setContent { EfaTheme { MoneyScreen(v) {} } }
        for (line in listOf(v.week.working().first()) + v.week.working().takeLast(3)) rule.onNodeWithContentDescription(line).assertExists()
        val first = v.week.blocks.first { it.creditedMin > 0 }
        val said = "${first.title}: ${first.heaviness} x ${fmt1(minOf(first.creditedMin, first.plannedMin) / 60.0)}h = ${fmt1(first.verifiedWeight)}"
        assertTrue(rule.onAllNodesWithContentDescription(said).fetchSemanticsNodes().isNotEmpty(), "block row $said")
    }

    @Test fun SC2_the_running_block_card_shows_how_far_through_it_is_and_the_week_shows_progress() {
        rule.setContent { EfaTheme { HomeScreen(state(days()).copy(nowLabel = "Now, until 12:15", nowTitle = "Notes", nowFraction = 0.4f, progress = "14.0 of 60.0 hours done", progressFraction = 0.23f), {}, {}) } }
        rule.onNodeWithText("NOW, UNTIL 12:15").assertExists(); rule.onNodeWithText("14.0 of 60.0 hours done").assertExists()
    }

    // AN1: the days fold, the weeks are a carousel, and what moves does so briefly and only when you cause it.
    private fun header(name: String, state: String) = hasClickAction() and hasContentDescription(name, substring = true) and hasStateDescription(state)
    // A deadline's title is only shown inside an open day, so it tells whether that day is open.
    private val dues = listOf(
        OpenTask(1, "Today essay", at(today, LocalTime.of(17, 0)), 60),
        OpenTask(2, "Tomorrow essay", at(today.plusDays(1), LocalTime.of(12, 0)), 60),
        OpenTask(3, "Friday essay", at(today.plusDays(4), LocalTime.of(12, 0)), 60),
    )
    private fun scrollTo(name: String, state: String) = rule.onNodeWithContentDescription("Full schedule").performScrollToNode(header(name, state))

    @Test fun AN1_today_starts_open_and_every_other_day_is_folded_to_its_header() {
        rule.setContent { EfaTheme { ScheduleScreen(days(tasks = dues)) } }
        rule.onNodeWithText("Today essay").assertExists()
        rule.onNodeWithText("Tomorrow essay").assertDoesNotExist(); rule.onNodeWithText("Friday essay").assertDoesNotExist()
        scrollTo("Tomorrow", "Closed"); rule.onNode(header("Tomorrow", "Closed")).assertExists()
        rule.onNode(header("Today", "Open")).assertExists()
    }

    @Test fun AN1_tapping_a_day_opens_it_and_tapping_again_folds_it() {
        rule.setContent { EfaTheme { ScheduleScreen(days(tasks = dues)) } }
        scrollTo("Tomorrow", "Closed"); rule.onNode(header("Tomorrow", "Closed")).performClick(); rule.waitForIdle()
        rule.onNode(header("Tomorrow", "Open")).assertExists(); rule.onNodeWithText("Tomorrow essay").assertExists()
        rule.onNode(header("Tomorrow", "Open")).performClick(); rule.waitForIdle()
        rule.onNodeWithText("Tomorrow essay").assertDoesNotExist(); rule.onNode(header("Tomorrow", "Closed")).assertExists()
    }

    @Test fun AN1_every_day_of_the_window_opens_when_tapped_and_closes_when_tapped_again_today_included() {
        val d = days(tasks = dues)
        rule.setContent { EfaTheme { ScheduleScreen(d) } }
        for (day in d.take(7)) {
            val name = day.tag ?: day.heading
            val state = if (day.today) "Open" else "Closed"
            val other = if (day.today) "Closed" else "Open"
            scrollTo(name, state); rule.onNode(header(name, state)).performClick(); rule.waitForIdle()
            rule.onNode(header(name, other)).assertExists()
            rule.onNode(header(name, other)).performClick(); rule.waitForIdle()
            rule.onNode(header(name, state)).assertExists()
        }
    }

    @Test fun SC3_the_carousel_of_the_next_7_days_is_first_and_today_is_beneath_it() {
        rule.setContent { EfaTheme { ScheduleScreen(days()) } }
        rule.onNode(header("Today", "Open")).assertExists()
        assertTrue(rule.onNodeWithText("Next 7 days").fetchSemanticsNode().positionInRoot.y < rule.onNode(header("Today", "Open")).fetchSemanticsNode().positionInRoot.y, "the carousel is above today")
        rule.onNodeWithText("3 to 9 Nov").assertExists()
        rule.onNodeWithContentDescription("Full schedule").performScrollToNode(hasText("Wed 4 Nov"))
        rule.onNodeWithText("Wed 4 Nov").assertExists(); rule.onNodeWithText("Tue 10 Nov").assertDoesNotExist()
    }

    @Test fun AN1_the_weeks_are_a_carousel_and_the_days_listed_are_the_shown_window() {
        rule.setContent { EfaTheme { ScheduleScreen(days()) } }
        rule.onNodeWithContentDescription("Full schedule").performScrollToNode(hasText("Next 7 days"))
        rule.onNodeWithText("Next 7 days").assertExists()
        rule.onNodeWithContentDescription("Week 2 of 2").performClick(); rule.waitForIdle()
        rule.onNodeWithText("The 7 days after").assertExists(); rule.onNodeWithText("10 to 15 Nov").assertExists()
        rule.onNodeWithContentDescription("Week 2 of 2, showing").assertExists()
        rule.onNodeWithContentDescription("Full schedule").performScrollToNode(hasText("Tue 10 Nov"))
        rule.onNodeWithText("Tue 10 Nov").assertExists(); rule.onNodeWithText("Wed 4 Nov").assertDoesNotExist()
        rule.onNode(header("Today", "Open")).assertExists()           // today stays beneath the carousel whichever window is showing
    }

    @Test fun AN1_swiping_the_week_card_moves_to_the_next_week() {
        rule.setContent { EfaTheme { ScheduleScreen(days()) } }
        rule.onNodeWithContentDescription("Full schedule").performScrollToNode(hasText("Next 7 days"))
        rule.onNodeWithText("Next 7 days").performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("The 7 days after").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Week 2 of 2, showing").assertExists()
    }

    @Test fun AN1_tapping_a_day_in_the_chart_opens_that_day() {
        rule.setContent { EfaTheme { ScheduleScreen(days(tasks = dues)) } }
        rule.onNodeWithText("Friday essay").assertDoesNotExist()
        val column = hasClickAction() and hasContentDescription("Fri 6 Nov", substring = true) and hasContentDescription("Open this day", substring = true)
        rule.onNodeWithContentDescription("Full schedule").performScrollToNode(column)
        rule.onNode(column).performClick(); rule.waitForIdle()
        rule.onNode(header("Fri 6 Nov", "Open")).assertExists(); rule.onNodeWithText("Friday essay").assertExists()
    }

    @Test fun AN1_with_animations_off_the_screen_works_the_same_and_nothing_waits_on_motion() {
        rule.setContent { EfaTheme { androidx.compose.runtime.CompositionLocalProvider(LocalMotion provides false) { ScheduleScreen(days(tasks = dues)) } } }
        scrollTo("Tomorrow", "Closed"); rule.onNode(header("Tomorrow", "Closed")).performClick()
        rule.onNode(header("Tomorrow", "Open")).assertExists(); rule.onNodeWithText("Tomorrow essay").assertExists()
    }

    @Test fun AN1_the_home_dots_move_to_the_schedule_and_back_with_a_tap() {
        rule.setContent { EfaTheme { HomeScreen(state(days()), {}, {}) } }
        rule.onNodeWithContentDescription("Schedule page").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Schedule page, showing").assertExists()
        rule.onNodeWithContentDescription("Home page").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Home page, showing").assertExists()
    }

    // PH1: when to take a photo is the first thing on the home screen, on each block, and in a notification.
    private fun photoState(at: java.time.ZonedDateTime, verified: Set<String> = emptySet()): HomeState {
        val plan = photoPlan(WeekB.slots, at, verified)
        val f = photoFocus(plan)
        return state(days()).copy(photo = f, photoLeft = f?.minutesLeft(at)?.toInt())
    }
    private val lecture get() = slotsFor(WeekB.slots, today).first { it.slot.type == "lecture" }

    @Test fun PH1_an_open_photo_is_the_first_card_and_says_to_take_it_now_and_by_when() {
        val l = lecture; var tapped = false
        rule.setContent { EfaTheme { HomeScreen(photoState(l.start.plusMinutes(10)), {}, {}, onPhoto = { tapped = true }) } }
        rule.onNodeWithText("PHOTO DUE").assertExists(); rule.onNodeWithText("Take the photo now").assertExists()
        rule.onNodeWithText(l.slot.title.substringBefore(" - "), substring = true).assertExists()
        rule.onNodeWithText("before ${java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(l.end)}", substring = true).assertExists()
        rule.onNodeWithText("50").assertExists()               // the minutes left, inside its ring
        rule.onNodeWithContentDescription("Photo due.", substring = true).performClick(); assertTrue(tapped)
    }

    @Test fun PH1_before_a_photo_opens_the_home_screen_says_when_the_next_one_is() {
        val l = lecture
        rule.setContent { EfaTheme { HomeScreen(photoState(l.start.minusMinutes(30)), {}, {}) } }
        rule.onNodeWithText("NEXT PHOTO").assertExists(); rule.onNodeWithText("Photo at 09:00").assertExists()
        rule.onNodeWithText("PHOTO DUE").assertDoesNotExist()
    }

    @Test fun PH1_a_late_study_photo_says_it_still_counts_and_a_done_one_shows_no_card() {
        val s = slotsFor(WeekB.slots, today).first { it.slot.type.startsWith("study") }
        rule.setContent { EfaTheme { HomeScreen(photoState(s.end.plusMinutes(30)), {}, {}) } }
        rule.onNodeWithText("Still counts, late").assertExists()
    }

    @Test fun PH1_each_block_that_needs_a_photo_says_what_and_when_on_the_schedule() {
        val l = lecture
        val v = scheduleView(WeekB.slots, emptyList(), null, l.start.minusMinutes(30), days = 1, from = today)
        rule.setContent { EfaTheme { ScheduleScreen(v) } }
        rule.onNodeWithText("PHOTO 09:00 TO 10:00").assertExists()
    }

    @Test fun PH1_the_setup_screen_has_a_switch_for_the_photo_reminders() {
        var set: Boolean? = null
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, photoReminders = true, onPhotoReminders = { set = it }) } }
        rule.onNodeWithContentDescription("Turn the photo reminders off").performScrollTo().performClick()
        assertEquals(false, set)
    }

    // The share-of-a-whole charts are rings and say their percentage.
    @Test fun SC2_the_buffer_working_draws_the_work_as_a_share_of_the_available_time() {
        val r = result(17.5, Task("Neural Computation coursework", 10.0, 8), Task("Randomness problem sheet", 3.0, 5))
        rule.setContent { EfaTheme { WorkingScreen(r) {} } }
        rule.onNodeWithText("74%").assertExists(); rule.onNodeWithText("Work takes 74%").assertExists(); rule.onNodeWithText("4.5h left over").assertExists()
    }

    @Test fun SC2_home_shows_the_confirmed_share_of_the_week_as_a_ring_with_its_percentage() {
        rule.setContent { EfaTheme { HomeScreen(state(days()).copy(progress = "14.0 of 60.0 hours done", progressFraction = 14f / 60f), {}, {}) } }
        rule.onNodeWithText("23%").assertExists(); rule.onNodeWithText("14.0 of 60.0 hours done").assertExists()
    }

    @Test fun SC3_todays_finished_blocks_fold_into_one_line_that_opens_them() {
        val l = lecture
        val later = l.end.plusHours(6)                      // by the evening, several blocks are over
        val v = scheduleView(WeekB.slots, emptyList(), null, later, days = 3, from = later.toLocalDate())
        val pastCount = v.first().rows.count { it.state == RowState.PAST }
        assertTrue(pastCount >= 3)
        rule.setContent { EfaTheme { ScheduleScreen(v) } }
        rule.onNodeWithContentDescription("Earlier today, $pastCount blocks").assertExists()
        rule.onNodeWithContentDescription("Wake, shower", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Earlier today, $pastCount blocks").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Hide the earlier blocks today").assertExists()
        rule.onNodeWithContentDescription("Wake, shower", substring = true).assertExists()
    }
}

