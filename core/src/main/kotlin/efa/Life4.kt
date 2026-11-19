package efa

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

// ---------- MX6: screen-time limits while a period is off ----------

/** MX6: during a period off the apps on the block list get a daily budget: open for [minutes] after the first open, then closed until midnight. */
data class ScreenLimit(val apps: Set<String>, val minutes: Int)

enum class LimitPhase { FREE, OPEN, CLOSED }

data class LimitStatus(val phase: LimitPhase, val until: ZonedDateTime?)

/** The budget runs from the first open of the day; it is spent after [minutes] and the apps stay closed until the next midnight. A start from an earlier day is free again. */
fun limitStatus(startedAt: ZonedDateTime?, now: ZonedDateTime, minutes: Int): LimitStatus {
    if (startedAt == null || minutes <= 0) return LimitStatus(LimitPhase.FREE, null)
    val spent = startedAt.plusMinutes(minutes.toLong())
    val midnight = startedAt.toLocalDate().plusDays(1).atStartOfDay(startedAt.zone)
    return when { now.isBefore(startedAt) || !now.isBefore(midnight) -> LimitStatus(LimitPhase.FREE, null); now.isBefore(spent) -> LimitStatus(LimitPhase.OPEN, spent); else -> LimitStatus(LimitPhase.CLOSED, midnight) }
}

// ---------- CE7, EM4: break glass ----------

/** CE7: the longest string. It lifts one lock and is allowed once in 30 days; it never touches sleep protection (N7 stays). */
const val BREAK_GLASS_WALL = 150

/**
 * CE7: what break glass does to the counted changes: it takes away the most recent loosening this week (so the lockdown on the fourth lifts) and the most recent import (so an import
 * cooldown ends). The WhatsApp cooldown and a pool lock are lifted by the app. Nothing here changes sleep.
 */
fun breakGlassHistory(history: List<ChangeEntry>, now: Instant, zone: ZoneId = LONDON): List<ChangeEntry> {
    val from = weekStart(now, zone)
    val week = history.filter { !it.at.isBefore(from) }
    val lastLoosen = week.filter { it.cls == ChangeClass.LOOSEN }.maxByOrNull { it.at }
    val lastImport = week.filter { it.import }.maxByOrNull { it.at }
    return history.filter { it !== lastLoosen && it !== lastImport }
}

// ---------- SL2, FB: NFC tags as an option ----------

/** SL2, FB6: the places a tag can stand for. NFC is an option: off by default, and photos stay the proof until it is on and a tag is registered. */
enum class NfcPurpose(val label: String, val steps: Set<String>) {
    DESK("Desk", setOf("study.sit")),
    BIBLE_SEAT("Bible seat", setOf("bible.sit")),
    BATHROOM("Bathroom", setOf("ready.shower", "cook.shower")),
    CHARGER("Charger", setOf("wd.dock")),
    DOOR("Front door", setOf("ready.pocket")),
}

/** A tag is known by a hash of its id, never the id itself. */
fun tagKey(id: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(id).take(8).joinToString("") { "%02x".format(it) }

fun encodeTags(m: Map<String, NfcPurpose>): String = m.entries.joinToString("\n") { "${it.key}|${it.value.name}" }

fun decodeTags(s: String?): Map<String, NfcPurpose> = s.orEmpty().lines().mapNotNull { l ->
    val p = l.split("|"); if (p.size != 2) null else runCatching { p[0] to NfcPurpose.valueOf(p[1]) }.getOrNull()
}.toMap()

/** The fact a tap makes: confidence 1.0 at its place, lower if the phone is somewhere else (FB6). The purpose goes in the ref. */
fun tapFact(purpose: NfcPurpose, at: Instant, atItsPlace: Boolean = true): Fact = nfcFact(at, purpose.name, atItsPlace)

// ---------- FB1: facts kept as lines ----------

fun encodeFact(f: Fact): String = listOf(f.at.toEpochMilli(), f.type.name, f.source, f.confidence, f.ref.replace("|", " ").replace("\n", " ")).joinToString("|")

fun decodeFact(line: String): Fact? = runCatching {
    val p = line.split("|", limit = 5)
    Fact(FactType.valueOf(p[1]), Instant.ofEpochMilli(p[0].toLong()), p[2], p[3].toDouble(), p.getOrElse(4) { "" })
}.getOrNull()

// ---------- RT: the runner picks what to show and when a routine is done ----------

/** What a runner needs to know about one step right now. */
data class RunStep(val step: Step, val done: Boolean, val needsPhoto: Boolean, val tagPurpose: NfcPurpose?)

/** A step is done if a fact in [facts] satisfies its window, or if a registered tag for it was tapped ([tapped] holds the purposes tapped in the window). */
fun runSteps(routine: Routine, tier: Tier, facts: List<Fact>, tapped: Set<NfcPurpose>, nfcOn: Boolean): List<RunStep> =
    routine.stepsFor(tier).map { s ->
        val purpose = NfcPurpose.entries.firstOrNull { s.id in it.steps }
        val viaTag = nfcOn && purpose != null && purpose in tapped && (s.proof.rank <= ProofTier.NFC.rank || ProofTier.NFC in s.alsoAccepts)
        val viaFact = facts.any { it.ref == "step:${s.id}" }
        RunStep(s, viaTag || viaFact, s.proof == ProofTier.PHOTO, if (nfcOn) purpose else null)
    }

/** A routine is done when every step that is not a photo step is done. Photo steps are taken on the home screen with the usual proof. */
fun routineDone(steps: List<RunStep>): Boolean = steps.filter { !it.needsPhoto }.all { it.done }
