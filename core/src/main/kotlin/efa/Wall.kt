package efa

import java.security.SecureRandom

const val WALL_BASE = 75

/** Confirmed by Michael 23 Oct 2026 (Q-G5). */
object WallLength {
    /** H1: 75, 100, 130, 165, 205, ... the step grows 25, 30, 35, ... Uses = days off used in the last 30 days. */
    fun dayOff(usesInLast30Days: Int, sameDay: Boolean = false): Int {
        require(usesInLast30Days >= 0)
        var len = WALL_BASE; var step = 25
        repeat(usesInLast30Days) { len += step; step += 5 }
        return len + if (sameDay) 40 else 0         // H3, H6 surcharge
    }
    /** N4: +25 for each extra override in the same night. nth is 1 for the first. */
    fun nightOverride(nth: Int): Int { require(nth >= 1); return WALL_BASE + 25 * (nth - 1) }
    const val BLOCKLIST_EDIT = WALL_BASE   // M7, K2
    const val OPEN_BLOCKED_APP = WALL_BASE // LA9
    const val FREE_SLOT_CHANGE = WALL_BASE // F4
    const val TAMPER = WALL_BASE           // K6, X5
}

/** Fresh random string every time. Easily confused characters left out; no spaces. */
fun newWallString(length: Int, rng: SecureRandom = SecureRandom()): String {
    val alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789"
    return buildString { repeat(length) { append(alphabet[rng.nextInt(alphabet.length)]) } }
}

/**
 * The typed-input rule. Only one character at a time is accepted, so a paste or autofill (many characters at once)
 * is rejected, and any wrong character restarts from zero.
 * Limit: a keyboard app or automation that sends one character at a time can still type it. Android cannot prevent
 * that; see docs/DECISIONS.md D-12.
 */
class WallEntry(val target: String) {
    var typed: Int = 0; private set
    var restarts: Int = 0; private set
    val done: Boolean get() = typed == target.length

    /** Returns true if the input was accepted as progress. */
    fun input(chars: String): Boolean {
        if (done) return false
        if (chars.length != 1) { restart(); return false }
        return if (chars[0] == target[typed]) { typed++; true } else { restart(); false }
    }
    private fun restart() { typed = 0; restarts++ }
}
