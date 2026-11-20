package app.efa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.VrConfig
import efa.vrPlan

/** One setting with a minus and a plus: [step] at a time inside [range]. [shown] says the value in words (for example No limit). */
@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int, shown: String = value.toString(), onChange: (Int) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(label, style = Ts(c.text, 15.sp), modifier = Modifier.weight(1f))
        Btn("-", { onChange((value - step).coerceAtLeast(range.first)) }, modifier = Modifier.width(52.dp), description = "Less: $label")
        BasicText(shown, style = Ts(c.text, 16.sp, FontWeight.Bold), modifier = Modifier.widthIn(min = 64.dp).semantics { contentDescription = "$label, $shown" })
        Btn("+", { onChange((value + step).coerceAtMost(range.last)) }, modifier = Modifier.width(52.dp), description = "More: $label")
    }
}

/**
 * VR7, VR8: how long each step and each cycle is, and whether the Red Engine has taken the remedial over. A preset sets the five steps, the breaks and the remedial time; the rest is yours to set.
 * The longest cycle shrinks every step in proportion to fit. The Red Engine switch is a plain toggle.
 */
@Composable
fun VrnpmSettingsCard(cfg: VrConfig, onChange: (VrConfig) -> Unit) {
    val c = LocalColors.current
    val plan = vrPlan(cfg); val steps = listOf(plan[0], plan[2], plan[4], plan[6], plan[8]); val total = plan.sum()
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("STUDY CYCLE (VRNPM)")
        BasicText("How long each step and each cycle is", style = Ts(c.text, 16.sp, FontWeight.Bold))
        BasicText("A step can overrun and shows it. These are the times it starts with.", style = Ts(c.muted, 14.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Optimal 105", cfg.preset() == "Optimal", { onChange(VrConfig.OPTIMAL.copy(capMin = cfg.capMin, pagesPerCycle = cfg.pagesPerCycle, redEngine = cfg.redEngine)) }, "Use the optimal cycle: 105 minutes")
            Chip("Ideal 165", cfg.preset() == "Ideal", { onChange(VrConfig.IDEAL.copy(capMin = cfg.capMin, pagesPerCycle = cfg.pagesPerCycle, redEngine = cfg.redEngine)) }, "Use the ideal cycle: 165 minutes")
            Chip("2.5 hours", cfg.preset() == "Session", { onChange(VrConfig.SESSION.copy(capMin = cfg.capMin, pagesPerCycle = cfg.pagesPerCycle, redEngine = cfg.redEngine)) }, "Use the 2.5 hour session: N, P and M, with V and R done by override")
        }
        Stepper("V video (min)", cfg.v, 5..120, 5) { onChange(cfg.copy(v = it)) }
        Stepper("R read (min)", cfg.r, 5..120, 5) { onChange(cfg.copy(r = it)) }
        Stepper("N notes (min)", cfg.n, 5..120, 5) { onChange(cfg.copy(n = it)) }
        Stepper("P practice (min)", cfg.p, 5..120, 5) { onChange(cfg.copy(p = it)) }
        Stepper("M marking (min)", cfg.m, 5..120, 5) { onChange(cfg.copy(m = it)) }
        Stepper("Break between steps (min)", cfg.breakBetween, 0..15, 1) { onChange(cfg.copy(breakBetween = it)) }
        Stepper("Break after notes (min)", cfg.breakAfterN, 0..20, 1) { onChange(cfg.copy(breakAfterN = it)) }
        Stepper("Longest cycle (min in the steps)", cfg.capMin, 0..240, 15, shown = if (cfg.capMin == 0) "No limit" else cfg.capMin.toString()) {
            onChange(cfg.copy(capMin = if (it in 1..44) (if (it < cfg.capMin) 0 else 60) else it))
        }
        Stepper("Pages in a cycle's notes", cfg.pagesPerCycle, 2..10, 1) { onChange(cfg.copy(pagesPerCycle = it)) }
        Stepper("Remedial a cycle (min)", cfg.remedialMin, 0..60, 10, shown = if (cfg.remedialMin == 0) "None" else cfg.remedialMin.toString()) { onChange(cfg.copy(remedialMin = it)) }
        Chip(if (cfg.remind) "Reminder when a step ends: on" else "Reminder when a step ends: off", cfg.remind, { onChange(cfg.copy(remind = !cfg.remind)) },
            if (cfg.remind) "A reminder with a sound when a step ends, saying what is next. Tap to turn it off" else "Turn on a reminder with a sound when a step ends, saying what is next")
        if (cfg.remind) Stepper("Remind again while over (min)", cfg.overEvery, 0..15, 5, shown = if (cfg.overEvery == 0) "Never" else cfg.overEvery.toString()) { onChange(cfg.copy(overEvery = it)) }
        Chip(if (cfg.skipVR) "V and R done by override: on" else "V and R done by override: off", cfg.skipVR, { onChange(cfg.copy(skipVR = !cfg.skipVR)) },
            if (cfg.skipVR) "V and R are done by override: cycles start at notes. Tap to time them again" else "V and R done by override: a cycle starts at notes, and the timer says V and R were not timed")
        Chip(if (cfg.redEngine) "Red Engine built: on, no remedial" else "Red Engine built: off", cfg.redEngine, { onChange(cfg.copy(redEngine = !cfg.redEngine)) },
            if (cfg.redEngine) "The Red Engine is built, so no remedial is booked. Tap to turn it back on" else "The Red Engine is built: stop booking the remedial")
        BasicText(
            "A cycle is ${steps.sum()} min in the steps (V ${steps[0]}, R ${steps[1]}, N ${steps[2]}, P ${steps[3]}, M ${steps[4]}) and $total min with the breaks." +
                if (cfg.capMin > 0 && listOf(cfg.v, cfg.r, cfg.n, cfg.p, cfg.m).sum() > cfg.capMin) " Shrunk to fit the longest cycle." else "",
            style = Ts(c.muted, 13.sp), modifier = Modifier.semantics { contentDescription = "Cycle length. " + "$total minutes with the breaks" },
        )
    }
}
