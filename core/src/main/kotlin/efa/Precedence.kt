package efa

/**
 * DP5: when two things want the same time, this order decides. Safety first, then sleep, then hard commitments, the routine floors (meals, the morning, the shutdown),
 * study, free time, and last the rewards. The yield policy (YP1) and the gate order apply it; this is the one place the order is written down and tested.
 */
enum class Precedence { SAFETY, SLEEP, HARD_COMMITMENT, ROUTINE_FLOOR, STUDY, FREE_TIME, REWARD }

fun precedenceOf(type: String, hard: Boolean = false): Precedence = when {
    type == "sleep" -> Precedence.SLEEP
    hard || type in setOf("lecture", "tutorial", "mytutor", "training", "match", "gym", "church", "errand", "travel") -> Precedence.HARD_COMMITMENT
    type in setOf("routine", "meal", "chore") -> Precedence.ROUTINE_FLOOR
    type in setOf("study", "study-standard", "study-light", "code", "planning") -> Precedence.STUDY
    type == "free" -> Precedence.FREE_TIME
    type == "reward" -> Precedence.REWARD
    else -> Precedence.STUDY
}

/** What yields first: a higher ordinal gives way to a lower one. */
fun yieldsFirst(a: String, b: String): Boolean = precedenceOf(a).ordinal > precedenceOf(b).ordinal
