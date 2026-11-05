package efa

import java.time.LocalDate

/**
 * App icons and a grid layout (U9). Both are off by default and can be turned on in settings. A change, on or off, takes effect from the next day, so it is a decision
 * made in advance and not in a weak moment. The home screen stays text only (U5); this only changes the type-to-open screen.
 */
data class LauncherLook(
    val icons: Boolean = false, val grid: Boolean = false,
    val pendingIcons: Boolean? = null, val pendingGrid: Boolean? = null, val pendingFrom: LocalDate? = null,
) {
    /** What is in force on [today]: a pending change applies once its day has come. */
    fun effective(today: LocalDate): LauncherLook =
        if (pendingFrom != null && !pendingFrom.isAfter(today)) LauncherLook(pendingIcons ?: icons, pendingGrid ?: grid) else copy(pendingIcons = pendingIcons.takeIf { pendingFrom?.isAfter(today) == true }, pendingGrid = pendingGrid.takeIf { pendingFrom?.isAfter(today) == true }, pendingFrom = pendingFrom?.takeIf { it.isAfter(today) })

    /** Asking for [icons] and [grid] (null leaves one as it is). The new value starts tomorrow. Asking for what is already in force clears a pending change. */
    fun change(today: LocalDate, icons: Boolean? = null, grid: Boolean? = null): LauncherLook {
        val now = effective(today)
        val wantIcons = icons ?: pendingIcons ?: now.icons; val wantGrid = grid ?: pendingGrid ?: now.grid
        if (wantIcons == now.icons && wantGrid == now.grid) return LauncherLook(now.icons, now.grid)
        return LauncherLook(now.icons, now.grid, wantIcons.takeIf { it != now.icons }, wantGrid.takeIf { it != now.grid }, today.plusDays(1))
    }

    /** What the settings card says. */
    fun note(today: LocalDate): String? {
        val e = effective(today)
        if (e.pendingFrom == null) return null
        val parts = listOfNotNull(e.pendingIcons?.let { if (it) "icons on" else "icons off" }, e.pendingGrid?.let { if (it) "grid on" else "grid off" })
        return if (parts.isEmpty()) null else parts.joinToString(" and ") + " from ${e.pendingFrom!!.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${e.pendingFrom!!.dayOfMonth}"
    }

    fun encode(): String = listOf(icons, grid, pendingIcons ?: "", pendingGrid ?: "", pendingFrom ?: "").joinToString("|")

    companion object {
        fun decode(s: String?): LauncherLook {
            val p = s?.split("|") ?: return LauncherLook()
            if (p.size != 5) return LauncherLook()
            return runCatching { LauncherLook(p[0].toBooleanStrict(), p[1].toBooleanStrict(), p[2].takeIf { it.isNotEmpty() }?.toBooleanStrict(), p[3].takeIf { it.isNotEmpty() }?.toBooleanStrict(), p[4].takeIf { it.isNotEmpty() }?.let(LocalDate::parse)) }.getOrDefault(LauncherLook())
        }
    }
}

/** U9: notification badges never appear in the launcher, in any look. */
const val LAUNCHER_SHOWS_BADGES = false
