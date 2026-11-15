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
        rule.onNodeWithText("17.5h").assertExists(); rule.onAllNodesWithText("13.0h").assertCountEquals(2)       // the Work tile and the total
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
}
