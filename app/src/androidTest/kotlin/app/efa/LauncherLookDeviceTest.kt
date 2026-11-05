package app.efa

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** App icons and a grid on the type-to-open screen: U6, U9. */
@RunWith(AndroidJUnit4::class)
class LauncherLookScreenTest {
    @get:Rule val rule = createComposeRule()
    private val apps = listOf(AppInfo("p.cal", "Calendar"), AppInfo("p.cam", "Camera"), AppInfo("p.chr", "Chrome"))

    @Test fun U6_with_the_grid_off_nothing_is_listed_until_you_type() {
        rule.setContent { EfaTheme { SearchScreen(apps, { null }, {}, {}) } }
        rule.onNodeWithContentDescription("Calendar. Opens the app.").assertDoesNotExist()
    }

    @Test fun U9_with_the_grid_on_every_app_is_shown_in_a_grid_and_can_be_opened() {
        var opened: String? = null
        rule.setContent { EfaTheme { SearchScreen(apps, { null }, { opened = it.pkg }, {}, grid = true) } }
        apps.forEach { rule.onNodeWithContentDescription("${it.label}. Opens the app.").assertExists() }
        rule.onNodeWithContentDescription("Camera. Opens the app.").performClick(); assertEquals("p.cam", opened)
    }

    @Test fun U9_icons_are_loaded_only_when_icons_are_on() {
        val asked = ArrayList<String>()
        rule.setContent { EfaTheme { SearchScreen(apps, { null }, {}, {}, icons = false, grid = true, iconFor = { asked += it; null }) } }
        rule.waitForIdle(); assertTrue(asked.isEmpty(), "icons were loaded while off")
    }

    @Test fun U9_with_icons_on_each_shown_app_asks_for_its_icon() {
        val asked = ArrayList<String>()
        rule.setContent { EfaTheme { SearchScreen(apps, { null }, {}, {}, icons = true, grid = true, iconFor = { asked += it; ImageBitmap(8, 8) }) } }
        rule.waitForIdle(); assertEquals(apps.map { it.pkg }.toSet(), asked.toSet())
    }

    @Test fun U7_a_blocked_app_still_says_blocked_in_the_grid() {
        rule.setContent { EfaTheme { SearchScreen(apps, { if (it == "p.chr") "Work block" else null }, {}, {}, grid = true) } }
        rule.onNodeWithContentDescription("Chrome. Blocked: Work block.").assertExists()
    }
}
