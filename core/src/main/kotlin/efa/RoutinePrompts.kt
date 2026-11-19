package efa

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

// ---------- CO1, CO2: routines are prompted at their time ----------

/** The routine a calendar block stands for, or null. The morning alarm is the wake; getting ready, the Bible, the evening's shutdown and the study blocks are routines. */
fun routineForSlot(s: PatternSlot): Routine? = when {
    s.cancelled || s.type == REMINDER_TYPE -> null
    s.title.startsWith("Bible") -> BIBLE
    s.title.startsWith("Wake") || s.title.startsWith("Breakfast, dress") -> GETTING_READY
    s.type == "sleep" || s.title.startsWith("Evening prayer") -> WIND_DOWN
    s.type in CUTTABLE_WORK || s.type == "study-standard" || s.type == "study-light" -> STUDY_BLOCK
    else -> null
}

/**
 * CO2: the level a prompt is at, [minutes] after its block started and while its routine has not been started. 0 is the feel, 3 minutes a spoken line, 6 minutes a notification,
 * 10 minutes the gate, which is the one that already holds for that block (K1 work, K3 getting ready, M6 the morning, N2 the night): Spark says so and stops. Never repeated.
 */
fun promptLevelAt(minutes: Long, started: Boolean): PromptLevel? = when {
    started || minutes < 0 -> null
    minutes < 3 -> PromptLevel.HAPTIC
    minutes < 6 -> PromptLevel.SPOKEN
    minutes < 10 -> PromptLevel.NOTIFICATION
    minutes < 30 -> PromptLevel.GATE
    else -> null                                  // after half an hour it stops: a nagging app gets deleted
}

/** CO1: one voice, one prompt at a time. A prompt within [gapSeconds] of the last is dropped, not queued. */
fun mayPrompt(lastAt: Instant?, now: Instant, gapSeconds: Long = 20): Boolean = lastAt == null || Duration.between(lastAt, now).seconds >= gapSeconds

/** CO4: the line for a prompt: the routine and its first step, one short sentence each, the verb first. */
fun promptLine(r: Routine, tier: Tier, level: PromptLevel): String {
    val first = r.stepsFor(tier).firstOrNull()?.say ?: r.title
    return when (level) {
        PromptLevel.HAPTIC -> first
        PromptLevel.SPOKEN -> "${r.title}. $first"
        PromptLevel.NOTIFICATION -> "${r.title} has not started. $first"
        PromptLevel.GATE -> "${r.title} first. ${gateSentence(r)}"
    }
}

private fun gateSentence(r: Routine): String = when (r.id) {
    "study" -> "The work block holds your phone until you start."
    "ready" -> "Getting ready holds your phone until you are ready."
    "winddown" -> "The night rule holds the apps now."
    else -> "Start it when you can."
}

/** Whether the routine has been started in the block: any step fact of it since [blockStart]. */
fun routineStarted(r: Routine, facts: List<Fact>, blockStart: ZonedDateTime): Boolean {
    val ids = r.steps.map { "step:${it.id}" }.toSet()
    return facts.any { it.ref in ids && !it.at.isBefore(blockStart.toInstant()) } || facts.any { it.ref == "routine:${r.id}" && !it.at.isBefore(blockStart.toInstant()) }
}

/** The time available for a routine: from now to the next block, or 60 minutes when nothing follows. */
fun availableMinutes(now: ZonedDateTime, nextStart: ZonedDateTime?): Int = nextStart?.let { Duration.between(now, it).toMinutes().toInt().coerceAtLeast(0) } ?: 60
