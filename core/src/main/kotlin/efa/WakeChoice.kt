package efa

import java.time.ZonedDateTime

/**
 * CW1: choosing when the next alarm rings also moves the morning. A wake later than the day's Wake block is a Late start for that date, so the Wake, Bible, breakfast and the rest follow it, and it
 * is checked and priced the way any Late start is. The Late start it makes carries this tag so choosing again, or going back to the normal alarm, can take it away.
 */
const val WAKE_CHOICE_SRC = "wake_choice"

/** A wake within this many minutes of the planned Wake block is the same morning. */
const val WAKE_CHOICE_SLACK_MIN = 5

/** CW1: the Late start that a chosen wake makes, or null when the morning does not need to move: no Wake block that day, or a wake that is not later than it. */
fun wakeChoiceInstance(plannedWakeMin: Int?, chosen: ZonedDateTime): Instance? {
    if (plannedWakeMin == null) return null
    val chosenMin = chosen.hour * 60 + chosen.minute
    if (chosenMin <= plannedWakeMin + WAKE_CHOICE_SLACK_MIN) return null
    return Instance(Preset.LATE_START, chosen.toLocalDate(), chosen.toLocalDate(), mapOf("wake" to "%02d:%02d".format(chosen.hour, chosen.minute), "src" to WAKE_CHOICE_SRC))
}

/** CW1: what a chosen wake would do to the morning: the Late start it needs (null when the morning stays), why it cannot stand, and whether it needs the wall. */
data class WakeChoicePlan(val inst: Instance?, val problems: List<String>, val wallNeeded: Boolean)

/** CW1: whether [inst] is the Late start a chosen wake made. */
fun isWakeChoice(inst: Instance?): Boolean = inst != null && inst.preset == Preset.LATE_START && inst.settings["src"] == WAKE_CHOICE_SRC

/** CW1: the minute the Wake block of [date] starts at, from the calendar, or null when that day has none. */
fun plannedWakeMin(slots: List<PatternSlot>, date: java.time.LocalDate, zone: java.time.ZoneId = LONDON): Int? =
    slotsFor(slots, date, zone).firstOrNull { !it.slot.cancelled && it.slot.title.startsWith("Wake") }?.slot?.startMin
