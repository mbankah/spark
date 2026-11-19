package efa

import java.time.Instant

/**
 * FB2: the proof ladder. A step names the tier it needs; a fact of a higher tier always satisfies a lower need.
 * Photos are the proof for now (FB8); NFC is already a rung so a sticker drops in later with no rework.
 */
enum class ProofTier(val rank: Int) { NONE(0), TAP(1), SENSOR(2), NFC(3), PHOTO(4) }

/** FB1: every signal becomes a fact. The producers are the camera, taps, sensors, tags, the calendar and the existing stores. */
enum class FactType(val tier: ProofTier) {
    TAP(ProofTier.TAP),
    STEPS(ProofTier.SENSOR), ACTIVITY(ProofTier.SENSOR), GEOFENCE(ProofTier.SENSOR), LIGHT(ProofTier.SENSOR), CHARGING(ProofTier.SENSOR), SCREEN(ProofTier.SENSOR), APP_FOREGROUND(ProofTier.SENSOR), CLOCK(ProofTier.NONE),
    NFC_TAG(ProofTier.NFC),
    PHOTO_MATCHED(ProofTier.PHOTO),
}

/** [source] names the producer; two weak facts from different sources can stand for one strong one (FB7). */
data class Fact(val type: FactType, val at: Instant, val source: String, val confidence: Double = 1.0, val ref: String = "")

const val STRONG_CONFIDENCE = 0.7
const val WEAK_CONFIDENCE = 0.4

/** FB6: a tag read away from its place is accepted at lower confidence only. */
fun nfcFact(at: Instant, tag: String, atItsPlace: Boolean): Fact = Fact(FactType.NFC_TAG, at, "nfc", if (atItsPlace) 1.0 else 0.5, tag)

/** What a step is waiting for: the window it can be proven in and the tier it needs. [alsoAccepts] lists lower tiers that count too (NFC where the step names it). */
data class StepWindow(val key: String, val opens: Instant, val closes: Instant, val needs: ProofTier, val alsoAccepts: Set<ProofTier> = emptySet(), val types: Set<FactType>? = null)

/** The state of a step. FB3, FB4, FB5. */
enum class StepState {
    PENDING,        // the window has not opened
    OPEN,           // inside the window and not proven: the gate hung on it stays shut (FB4)
    DONE,           // proven in the window
    UNPROVEN,       // the window closed and it was not proven: no reward, unconfirmed time, no blame (FB4)
    NOT_YOUR_FAULT, // a technical failure closed it (FB5, M10)
}

private fun satisfies(w: StepWindow, f: Fact): Boolean {
    if (f.at.isBefore(w.opens) || f.at.isAfter(w.closes)) return false
    if (w.types != null && f.type !in w.types) return false
    val tier = f.type.tier
    return tier.rank >= w.needs.rank || tier in w.alsoAccepts
}

/** FB3, FB7: done when one strong fact, or two weak facts from different sources, are inside the window. A NONE need is done by the window passing. */
fun stepState(w: StepWindow, facts: List<Fact>, now: Instant, techFailed: Boolean = false): StepState {
    if (now.isBefore(w.opens)) return StepState.PENDING
    if (w.needs == ProofTier.NONE && w.alsoAccepts.isEmpty()) return if (now.isAfter(w.closes)) StepState.DONE else StepState.OPEN
    val hits = facts.filter { satisfies(w, it) }
    val strong = hits.any { it.confidence >= STRONG_CONFIDENCE }
    val weak = hits.filter { it.confidence >= WEAK_CONFIDENCE }
    // A weak fact that meets the need is corroborated by any other sensor fact from a different source in the window (a tag read away from its place, with the place fix agreeing).
    val support = if (weak.isEmpty()) emptyList() else facts.filter { !it.at.isBefore(w.opens) && !it.at.isAfter(w.closes) && it.type.tier.rank >= ProofTier.SENSOR.rank && it.confidence >= WEAK_CONFIDENCE }
    val twoWeak = (weak + support).map { it.source }.distinct().size >= 2
    if (strong || twoWeak) return StepState.DONE
    if (now.isAfter(w.closes)) return if (techFailed) StepState.NOT_YOUR_FAULT else StepState.UNPROVEN
    return StepState.OPEN
}

/** FB4: a step that is open holds its gate shut. Only OPEN does; pending and finished steps hold nothing. */
fun holdsGate(state: StepState): Boolean = state == StepState.OPEN

/** FB4: only a proven step pays a reward. */
fun paysReward(state: StepState): Boolean = state == StepState.DONE

/** FB4: an unproven step counts as unconfirmed time for the week; a step closed for a technical failure does not. */
fun countsAsUnconfirmed(state: StepState): Boolean = state == StepState.UNPROVEN
