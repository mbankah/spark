package efa

import java.time.ZonedDateTime

/**
 * Appearance modes (section 24: AP2 to AP8). Four modes and Auto share one layout, so changing mode never moves anything or hides a rule (AP7). Colourful gives the
 * four home cards their own colours with black outlines and adds no motion and no badges (AP5). A negative Buffer is the only red in every mode and always says
 * NEGATIVE, so it never depends on colour (AP6).
 */
enum class CardKind { BUFFER, NOW, NEXT, SPEND }

/** The Colourful palette: Buffer yellow, now pink, next green, spend blue (AP5). Text is black on all four. */
object Colourful {
    val tokens = Tokens("#fffaf0", "#ffffff", "#000000", "#2b2f36", "#0033cc", "#000000", "#ffffff", "#0033cc")
    val tints = mapOf(CardKind.BUFFER to "#ffe14d", CardKind.NOW to "#ff9ec4", CardKind.NEXT to "#8ee08a", CardKind.SPEND to "#8cc4ff")
}

enum class AppearanceMode(val label: String) { AUTO("Auto"), LIGHT("Light"), DARK("Dark"), NIGHT("Night"), COLOURFUL("Colourful") }

/** What Auto picks right now (AP4): Night from the night cutoff (or the shutdown photo, whichever came first) until the morning alarm is stopped, otherwise Light by day and Dark by night. */
fun autoMode(now: ZonedDateTime, inNight: Boolean): AppearanceMode = when {
    inNight -> AppearanceMode.NIGHT
    now.hour in 7..18 -> AppearanceMode.LIGHT
    else -> AppearanceMode.DARK
}

/**
 * [inNight] for [autoMode]: after the earlier of the cutoff and the shutdown photo, until the alarm is stopped. [shutdownAtMs] is the shutdown photo time tonight if there
 * was one. [alarmStoppedAtMs] is when this morning's alarm was stopped.
 */
fun inNightMode(now: ZonedDateTime, window: NightWindow?, shutdownAtMs: Long?, alarmStoppedAtMs: Long?): Boolean {
    val nowMs = now.toInstant().toEpochMilli()
    val start = listOfNotNull(window?.cutoff?.toInstant()?.toEpochMilli(), shutdownAtMs).minOrNull() ?: return false
    if (nowMs < start) return false
    val wakeMs = window?.wake?.toInstant()?.toEpochMilli()
    if (alarmStoppedAtMs != null && alarmStoppedAtMs >= start && nowMs >= alarmStoppedAtMs) return false
    return wakeMs == null || nowMs < wakeMs + 6 * 3_600_000L                       // a night that never got its alarm stopped still ends by the late morning
}

/** AP3: Night mode caps the screen brightness at about 20%. */
const val NIGHT_BRIGHTNESS_CAP = 0.2f
