package efa

import java.time.Duration
import java.time.ZonedDateTime

/** Block types that can hold a task's work. Study cycles keep their own blocks (D1) and are not placed here. */
val TASK_SLOT_TYPES = setOf("study", "code")

/** A task still to do: [remainingMin] is what is left after proven work (RS6). */
data class OpenTask(val id: Long, val title: String, val due: ZonedDateTime, val remainingMin: Int, val priority: Int = 0)

/** Part of a task placed in one block. A task bigger than one block is split across several (RS2). */
data class Placed(val slot: DatedSlot, val task: OpenTask, val minutes: Int)

data class Allocation(val placed: List<Placed>, val unplaced: Map<Long, Int>) {
    fun inSlot(s: DatedSlot) = placed.filter { it.slot.start == s.start && it.slot.slot == s.slot }
}

/** The smallest piece of a task worth its own place in a block. Smaller remainders only go where a block's tail is left. */
const val MIN_CHUNK_MIN = 15

/**
 * RS2: earliest deadline first, each task into the earliest blocks that end before its deadline, split across blocks when it does not
 * fit in one. A task's [OpenTask.priority] (higher first) breaks ties between equal deadlines, so an assignment week puts assignment
 * work first and a catch-up week puts catch-up first (PW2). Whatever cannot be placed before its deadline is [Allocation.unplaced]
 * and is shown, never dropped (RS4). Blocks already started count only from [now].
 */
fun allocateTasks(slots: List<PatternSlot>, tasks: List<OpenTask>, now: ZonedDateTime, horizonDays: Int = 14): Allocation {
    val zone = now.zone
    val free = (0L..horizonDays.toLong()).flatMap { slotsFor(slots, now.toLocalDate().plusDays(it - 1), zone) }
        .filter { it.slot.type in TASK_SLOT_TYPES && it.end.isAfter(now) }
        .map { it to Duration.between(maxOf(it.start, now), it.end).toMinutes().toInt() }
        .sortedBy { it.first.start }.toMutableList()
    val placed = ArrayList<Placed>(); val unplaced = LinkedHashMap<Long, Int>()
    for (t in tasks.filter { it.remainingMin > 0 }.sortedWith(compareBy<OpenTask> { it.due }.thenByDescending { it.priority }.thenBy { it.id })) {
        var need = t.remainingMin
        for (i in free.indices) {
            if (need <= 0) break
            val (slot, room) = free[i]
            if (room < 1 || slot.end.isAfter(t.due)) continue
            val take = minOf(room, need)
            if (take < MIN_CHUNK_MIN && take < need) continue            // do not leave a sliver of a task that has more to place
            placed += Placed(slot, t, take); need -= take; free[i] = slot to (room - take)
        }
        if (need > 0) unplaced[t.id] = need
    }
    return Allocation(placed, unplaced)
}

/**
 * RS6: proven minutes in a task block reduce the open tasks, earliest deadline first, then by priority. Returns the minutes to add to
 * each task. Nothing is credited without the proof that reached this call (P8).
 */
fun distributeCredit(open: List<OpenTask>, creditedMin: Int): Map<Long, Int> {
    var left = creditedMin
    val out = LinkedHashMap<Long, Int>()
    for (t in open.sortedWith(compareBy<OpenTask> { it.due }.thenByDescending { it.priority }.thenBy { it.id })) {
        if (left <= 0) break
        val take = minOf(left, t.remainingMin)
        if (take > 0) { out[t.id] = take; left -= take }
    }
    return out
}
