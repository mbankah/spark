package efa

import java.time.ZonedDateTime

/**
 * LA6: Android Settings stays openable, but this app's own accessibility, app-info, uninstall and default-home pages go through the wall. This is best effort and says
 * so: the pages are recognised by what they show (the app's name next to the page's own words), which can differ between phone makers, and it cannot stop a person who
 * boots into safe mode or uses another route. Each time one of these pages is seen it is logged (tampering), and passing the wall opens Settings for ten minutes.
 */
enum class GuardedPage(val label: String) {
    ACCESSIBILITY("the accessibility page for Spark"), APP_INFO("the app info page for Spark"), UNINSTALL("the uninstall question for Spark"), DEFAULT_HOME("the home app choice"),
}

const val SETTINGS_ALLOW_MINUTES = 10L

private val SETTINGS_PACKAGES = setOf("com.android.settings", "com.google.android.settings", "com.samsung.android.settings", "com.android.settings.intelligence")
private val INSTALLER_PACKAGES = setOf("com.google.android.packageinstaller", "com.android.packageinstaller", "com.samsung.android.packageinstaller")

/** [texts] is what the window shows. [appName] is how Spark is called on this phone. Returns the guarded page it is, or null. */
fun guardedPage(pkg: String, texts: List<String>, appName: String = "Spark"): GuardedPage? {
    val all = texts.joinToString(" | ").lowercase()
    val me = appName.lowercase()
    if (pkg in INSTALLER_PACKAGES) return if (me in all && (all.contains("uninstall") || all.contains("delete"))) GuardedPage.UNINSTALL else null
    if (pkg !in SETTINGS_PACKAGES) return null
    if (me !in all) return null
    return when {
        all.contains("uninstall") && (all.contains("force stop") || all.contains("app info") || all.contains("storage") || all.contains("permissions")) -> GuardedPage.APP_INFO
        all.contains("accessibility") && (all.contains("use $me") || all.contains("downloaded apps") || all.contains("installed apps") || all.contains("shortcut")) -> GuardedPage.ACCESSIBILITY
        (all.contains("home app") || all.contains("default home")) -> GuardedPage.DEFAULT_HOME
        else -> null
    }
}

/** The wall has been passed and Settings is open until [until]. */
data class SettingsAllowance(val until: ZonedDateTime?) {
    fun open(now: ZonedDateTime) = until?.isAfter(now) == true
    fun after(now: ZonedDateTime) = SettingsAllowance(now.plusMinutes(SETTINGS_ALLOW_MINUTES))
}

/** The wall for these pages is the same string as every other loosening (K6, X5). */
val SETTINGS_WALL_LENGTH = WallLength.TAMPER
