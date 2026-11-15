package efa

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * PH1: when to take which photo, in one place. The rules are the ones the verifier already applies (W1, Y1, L1, B7, Q-G15); this only
 * lays them out in time so the home screen, the schedule and a notification can all say the same thing.
 *
 *  - A lecture or tutorial (ATTENDANCE) or another fixed commitment (WINDOW): the photo is taken inside its own time, from its start to its end.
 *  - A study block (WORK): it opens when the block ends, counts in full for 15 minutes, then still counts as recovered time (up to an hour a night) until the next wake.
 *  - A code block (COMMIT): the same times, but the proof is a pushed commit, not a photo.
 *  - The night photo (NIGHT): the setup, taken in the two hours before the night cutoff.
 */
enum class PhotoKind { ATTENDANCE, WINDOW, WORK, COMMIT, NIGHT }

/** UPCOMING: not open yet. DUE: open and counts in full. LATE: still counts, as recovered time. DONE. MISSED: no longer possible. */
enum class PhotoState { UPCOMING, DUE, LATE, DONE, MISSED }

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private fun ZonedDateTime.hm() = HM.format(this)

data class PhotoDue(
    val key: String, val kind: PhotoKind, val title: String, val place: String,
    val opens: ZonedDateTime, val dueBy: ZonedDateTime, val lateUntil: ZonedDateTime?, val state: PhotoState, val slot: DatedSlot?,
) {
    val noun: String get() = if (kind == PhotoKind.COMMIT) "commit" else "photo"
    val open: Boolean get() = state == PhotoState.DUE || state == PhotoState.LATE

    /** The big words: what to do. */
    fun headline(): String = when (state) {
        PhotoState.DUE -> "Take the $noun now"
        PhotoState.LATE -> "Still counts, late"
        PhotoState.UPCOMING -> if (kind == PhotoKind.COMMIT) "Commit at ${opens.hm()}" else "Photo at ${opens.hm()}"
        PhotoState.DONE -> "Done"
        PhotoState.MISSED -> "Missed"
    }

    /** A few words for a row of the schedule: what to do and when. */
    fun tag(): String = when (state) {
        PhotoState.DONE -> "$noun done".replaceFirstChar { it.uppercase() }
        PhotoState.MISSED -> "$noun missed".replaceFirstChar { it.uppercase() }
        PhotoState.DUE -> "Take it now"
        PhotoState.LATE -> "Late, still counts"
        PhotoState.UPCOMING -> when (kind) {
            PhotoKind.ATTENDANCE, PhotoKind.WINDOW -> "Photo ${opens.hm()} to ${dueBy.hm()}"
            PhotoKind.WORK -> "Photo at ${opens.hm()}"
            PhotoKind.COMMIT -> "Commit by ${dueBy.hm()}"
            PhotoKind.NIGHT -> "Photo by ${dueBy.hm()}"
        }
    }

    /** What it is for and by when, in one line. */
    fun detail(): String = when (kind) {
        PhotoKind.ATTENDANCE -> "$title." + (if (place.isNotBlank()) " Take it at $place" else " Take it there") + when (state) { PhotoState.UPCOMING -> ", from ${opens.hm()} to ${dueBy.hm()}."; else -> ", before ${dueBy.hm()}." }
        PhotoKind.WINDOW -> "$title. Take it " + when (state) { PhotoState.UPCOMING -> "from ${opens.hm()} to ${dueBy.hm()}."; else -> "before ${dueBy.hm()}." }
        PhotoKind.WORK -> when (state) {
            PhotoState.UPCOMING -> "$title ends at ${opens.hm()}. Take its photo then; it counts in full until ${dueBy.hm()}."
            PhotoState.DUE -> "$title ended at ${opens.hm()}. Take its photo before ${dueBy.hm()} to count in full."
            PhotoState.LATE -> "$title ended at ${opens.hm()}. It now counts as recovered time" + (lateUntil?.let { ", until ${it.hm()}." } ?: ".")
            else -> "$title."
        }
        PhotoKind.COMMIT -> when (state) {
            PhotoState.UPCOMING -> "$title ends at ${opens.hm()}. Push a commit made during it."
            PhotoState.DUE -> "$title ended at ${opens.hm()}. Push your commit before ${dueBy.hm()}, then check."
            PhotoState.LATE -> "$title ended at ${opens.hm()}. A commit now counts as recovered time" + (lateUntil?.let { ", until ${it.hm()}." } ?: ".")
            else -> "$title."
        }
        PhotoKind.NIGHT -> "Photograph your setup" + when (state) { PhotoState.UPCOMING -> " from ${opens.hm()}, before ${dueBy.hm()}."; PhotoState.LATE -> ", late. Your sleep is still tracked."; else -> " before ${dueBy.hm()}, your night cutoff." }
    }
}

const val NIGHT_PHOTO_LEAD_HOURS = 2L

/** The photo (or commit) a block needs and where it stands at [now], or null if the block needs no proof. */
fun photoForSlot(ds: DatedSlot, pattern: List<PatternSlot>, now: ZonedDateTime, verified: Set<String>): PhotoDue? {
    val proof = proofFor(ds.slot)
    val key = blockKey(ds)
    val done = key in verified
    val name = ds.slot.title.substringBefore(" - ")
    return when (proof) {
        Proof.NONE -> null
        Proof.ATTENDANCE, Proof.WINDOW_PHOTO -> {
            val state = when { done -> PhotoState.DONE; now.isBefore(ds.start) -> PhotoState.UPCOMING; now.isBefore(ds.end) -> PhotoState.DUE; else -> PhotoState.MISSED }
            PhotoDue(key, if (proof == Proof.ATTENDANCE) PhotoKind.ATTENDANCE else PhotoKind.WINDOW, name, ds.slot.venue, ds.start, ds.end, null, state, ds)
        }
        Proof.WORK_PHOTO, Proof.GIT_COMMIT -> {
            val dueBy = ds.end.plus(ON_TIME_GRACE); val wake = nextWake(pattern, ds.end)
            val state = when {
                done -> PhotoState.DONE
                now.isBefore(ds.end) -> PhotoState.UPCOMING
                !now.isAfter(dueBy) -> PhotoState.DUE
                wake != null && !now.isAfter(wake) -> PhotoState.LATE
                else -> PhotoState.MISSED
            }
            PhotoDue(key, if (proof == Proof.WORK_PHOTO) PhotoKind.WORK else PhotoKind.COMMIT, name, "", ds.end, dueBy, wake, state, ds)
        }
    }
}

/**
 * Every photo around [now], from yesterday evening to tomorrow. [verified] are the block keys already proved; [nightPhotoMs] is when the
 * last night photo was taken. Sorted by when it opens.
 */
fun photoPlan(pattern: List<PatternSlot>, now: ZonedDateTime, verified: Set<String>, nightPhotoMs: Long? = null): List<PhotoDue> {
    val fromSlots = (-1L..1L).flatMap { slotsFor(pattern, now.toLocalDate().plusDays(it), now.zone) }.mapNotNull { photoForSlot(it, pattern, now, verified) }
    val night = (-1L..1L).mapNotNull { d ->
        val wake = slotsFor(pattern, now.toLocalDate().plusDays(d), now.zone).filter { it.slot.title == "Wake, shower" }.minByOrNull { it.start }?.start ?: return@mapNotNull null
        val cutoff = nightCutoff(wake); val opens = cutoff.minusHours(NIGHT_PHOTO_LEAD_HOURS)
        if (wake.isBefore(now.minusHours(1)) || opens.isAfter(now.plusHours(30))) return@mapNotNull null
        val taken = nightPhotoMs != null && nightPhotoMs >= opens.toInstant().toEpochMilli()
        val state = when { taken -> PhotoState.DONE; now.isBefore(opens) -> PhotoState.UPCOMING; !now.isAfter(cutoff) -> PhotoState.DUE; now.isBefore(wake) -> PhotoState.LATE; else -> PhotoState.MISSED }
        PhotoDue("night|${wake.toLocalDate()}", PhotoKind.NIGHT, "Tonight's setup", "", opens, cutoff, wake, state, null)
    }
    return (fromSlots + night).sortedBy { it.opens }
}

/** The photo to show first: one that is open now (the soonest to run out), else the next to open. null when there is none left. */
fun photoFocus(plan: List<PhotoDue>): PhotoDue? =
    plan.filter { it.open }.minByOrNull { it.dueBy } ?: plan.filter { it.state == PhotoState.UPCOMING }.minByOrNull { it.opens }

/** A moment to make a sound: a photo opening, and five minutes before it stops counting in full. */
data class PhotoReminder(val at: ZonedDateTime, val key: String, val title: String, val text: String, val lastCall: Boolean)

fun photoReminders(plan: List<PhotoDue>): List<PhotoReminder> = plan.filter { it.state != PhotoState.DONE && it.state != PhotoState.MISSED }.flatMap { p ->
    val opening = PhotoReminder(p.opens, p.key, if (p.kind == PhotoKind.COMMIT) "Commit due" else "Photo due", p.copy(state = PhotoState.DUE).detail(), false)
    val last = p.dueBy.minusMinutes(5)
    if (last.isAfter(p.opens)) listOf(opening, PhotoReminder(last, p.key, "5 minutes left", p.copy(state = PhotoState.DUE).detail(), true)) else listOf(opening)
}.sortedBy { it.at }

fun nextPhotoReminder(plan: List<PhotoDue>, after: ZonedDateTime): PhotoReminder? = photoReminders(plan).firstOrNull { it.at.isAfter(after) }

/** Minutes left to take it in full, or null if it is not open or has no limit. */
fun PhotoDue.minutesLeft(now: ZonedDateTime): Long? = if (state == PhotoState.DUE) Duration.between(now, dueBy).toMinutes().coerceAtLeast(0) else null
