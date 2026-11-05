package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class SettingsGuardTest {
    private val S = "com.android.settings"

    @Test fun LA6_the_app_info_page_for_spark_is_recognised() =
        assertEquals(GuardedPage.APP_INFO, guardedPage(S, listOf("App info", "Spark", "Open", "Uninstall", "Force stop", "Permissions")))

    @Test fun LA6_the_accessibility_page_for_spark_is_recognised() =
        assertEquals(GuardedPage.ACCESSIBILITY, guardedPage(S, listOf("Spark", "Use Spark", "Accessibility", "Spark checks which app is in front")))

    @Test fun LA6_the_uninstall_question_is_recognised_in_the_package_installer() =
        assertEquals(GuardedPage.UNINSTALL, guardedPage("com.google.android.packageinstaller", listOf("Do you want to uninstall this app?", "Spark", "OK", "Cancel")))

    @Test fun LA6_the_default_home_choice_naming_spark_is_recognised() =
        assertEquals(GuardedPage.DEFAULT_HOME, guardedPage(S, listOf("Home app", "Spark", "Pixel Launcher")))

    @Test fun LA6_android_settings_in_general_stays_openable() {
        assertNull(guardedPage(S, listOf("Settings", "Network and internet", "Battery", "Display")))
        assertNull(guardedPage(S, listOf("App info", "Chrome", "Uninstall", "Force stop")))            // another app's page is not ours
        assertNull(guardedPage(S, listOf("Accessibility", "TalkBack", "Select to Speak")))
    }

    @Test fun LA6_other_apps_are_never_treated_as_settings() {
        assertNull(guardedPage("com.example.notes", listOf("App info", "Spark", "Uninstall", "Force stop")))
        assertNull(guardedPage("com.google.android.packageinstaller", listOf("Install Chrome?", "Chrome")))
    }

    @Test fun LA6_the_apps_name_can_be_different_on_a_phone_that_renamed_it() =
        assertEquals(GuardedPage.APP_INFO, guardedPage(S, listOf("App info", "My Spark", "Uninstall", "Storage"), appName = "My Spark"))

    @Test fun LA6_passing_the_wall_opens_settings_for_ten_minutes_only() {
        val now = at(LocalDate.of(2026, 11, 5), LocalTime.of(10, 0))
        assertFalse(SettingsAllowance(null).open(now))
        val a = SettingsAllowance(null).after(now)
        assertTrue(a.open(now.plusMinutes(9))); assertFalse(a.open(now.plusMinutes(10)))
    }

    @Test fun K6_X5_the_settings_wall_is_the_same_string_as_the_other_tamper_walls() = assertEquals(WallLength.TAMPER, SETTINGS_WALL_LENGTH)

    @Test fun L7_the_myuob_app_is_found_by_its_name_not_a_guessed_package() {
        assertEquals("uk.ac.bham.x", findMyUoB(listOf("com.a" to "Chrome", "uk.ac.bham.x" to "MyUoB")))
        assertEquals("p2", findMyUoB(listOf("p1" to "Maps", "p2" to "University of Birmingham")))
        assertNull(findMyUoB(listOf("p1" to "Maps"))); assertNull(findMyUoB(emptyList()))
    }
}
