package efa

// ---- VR15: the end of the block, and stopping a cycle ----

const val VR_BLOCKEND_KIND = "vr.blockend"
const val VR_BLOCKEND_STOP = "stop"
const val VR_BLOCKEND_RUN_ON = "run_on"
const val VR_BLOCKEND_MOVE = "move"

/** VR15: a cycle that began inside its block and is still running now that the block has ended. One started after the block ended was chosen, so it is not asked about. */
fun vrOverBlockEnd(run: VrRun?, blockEndMs: Long, nowMs: Long): Boolean =
    run != null && vrView(run, nowMs).status == VrStatus.RUNNING && nowMs >= blockEndMs && (run.boundaries.firstOrNull() ?: Long.MAX_VALUE) < blockEndMs

/** VR15: ends a cycle that is running, now. The steps not reached are left at no time, and a pause that is open is closed. A cycle that is not running is returned as it was. */
fun vrStop(run: VrRun, nowMs: Long): VrRun {
    val phases = vrPhases(run.planMinutes)
    if (run.boundaries.isEmpty() || run.boundaries.size > phases.size) return run
    val closed = if (run.paused) run.copy(pauses = run.pauses + nowMs) else run
    return closed.copy(boundaries = closed.boundaries + List(phases.size + 1 - closed.boundaries.size) { nowMs })
}

/** VR15: the minutes the cycle still has to run as planned, from the step in hand to the last one, to the next 5 (0 when it is not running). */
fun vrRemainingMin(run: VrRun, nowMs: Long): Int {
    val v = vrView(run, nowMs); if (v.status != VrStatus.RUNNING) return 0
    val later = vrPhases(run.planMinutes).drop(v.phaseIndex + 1).sumOf { it.plannedMin }
    val total = (v.remainingMs + 59_999L) / 60_000L + later
    return ((total + 4) / 5 * 5).toInt()
}

/** VR15: the question asked when a cycle is still running at the end of its block. [blockKey] makes it one question for one block; [endLabel] is the block's end as a clock time. */
fun vrBlockEndConfirm(blockKey: String, blockTitle: String, endLabel: String, run: VrRun, nowMs: Long): Confirm {
    val v = vrView(run, nowMs); val left = vrRemainingMin(run, nowMs)
    val where = if (v.isBreak) "a break" else v.title.substringBefore(" ").take(1)
    return Confirm(
        id = "$VR_BLOCKEND_KIND:$blockKey", kind = VR_BLOCKEND_KIND, title = "The study block has ended",
        body = "${blockTitle.substringBefore(" - ")} ended at $endLabel and the cycle is still on $where, about $left min to go. What now?",
        options = listOf(
            ConfirmOption(VR_BLOCKEND_STOP, "Stop the cycle"),
            ConfirmOption(VR_BLOCKEND_RUN_ON, "Let it run on"),
            ConfirmOption(VR_BLOCKEND_MOVE, "Move the rest of the day", opensApp = true),
        ),
        raisedMs = nowMs,
    )
}

// ---- SR1: a held start earns a little open phone ----

/** SR1: minutes of earned open phone for a held start (a block or a VRNPM cycle started with the hold), a small reward that is not proof. */
const val START_REWARD_MIN = 2

/** SR1: at most this many starts a day are paid, so a start with no work behind it can buy at most 10 minutes. */
const val START_REWARD_DAILY_CAP = 5

/** SR1: the earnings of the starts made since [dayStartMs]: the first [START_REWARD_DAILY_CAP], each [START_REWARD_MIN] minutes. */
fun startEarnings(startsMs: List<Long>, dayStartMs: Long): List<Earning> = startsMs.filter { it >= dayStartMs }.sorted().take(START_REWARD_DAILY_CAP).map { Earning(it, START_REWARD_MIN) }
