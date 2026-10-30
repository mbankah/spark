package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.DayOffPreview
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What the days off screen shows: the days that can be picked, what is selected, the preview and what is already planned. */
data class DayOffUi(
    val today: LocalDate, val selected: List<LocalDate>, val preview: DayOffPreview?, val usesIn30: Int,
    val claimable: List<LocalDate>, val upcoming: List<String>, val message: String? = null, val claiming: Boolean = false,
)

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * Plan a day off (C1, C4, H1 to H6, X3). Pick the days, see what they cost the Buffer before anything is typed, then type the string, which is longer
 * for each use in the last 30 days. A same-day request, an illness and a claim for a day that has passed all cost the 40 extra characters (H3, H6).
 */
@Composable
fun DayOffScreen(ui: DayOffUi, onPick: (List<LocalDate>) -> Unit, onIll: () -> Unit, onClaim: (LocalDate) -> Unit, onPlan: () -> Unit, onBack: () -> Unit) {
    val c = LocalColors.current
    val p = ui.preview
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("PLANNED CHANGES")
        BasicText("Plan a day off", style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText("A day off takes the day's work out of the time you have and keeps sleep, meals and food money. Nothing counts as lost, and the day is left out of the money. What it takes is a typed string that gets longer each time you use one in 30 days.", style = Ts(c.muted, 14.sp))
        ui.message?.let { Column(Modifier.fillMaxWidth().card().padding(16.dp)) { BasicText(it, style = Ts(c.text, 15.sp, FontWeight.Bold)) } }

        Label("PICK DAYS")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..13).map { ui.today.plusDays(it.toLong()) }.forEach { d ->
                val label = when (d) { ui.today -> "Today"; ui.today.plusDays(1) -> "Tomorrow"; else -> DAY.format(d) }
                Chip(label, ui.selected == listOf(d), { onPick(listOf(d)) }, "Day off $label")
            }
        }
        val saturdays = (0..13).map { ui.today.plusDays(it.toLong()) }.filter { it.dayOfWeek == java.time.DayOfWeek.SATURDAY }
        if (saturdays.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            saturdays.forEach { s -> Chip("Weekend ${DateTimeFormatter.ofPattern("d MMM").format(s)}", ui.selected == listOf(s, s.plusDays(1)), { onPick(listOf(s, s.plusDays(1))) }, "Weekend off from ${DAY.format(s)}, one use") }
        }
        Btn("I am ill today", onIll, modifier = Modifier.fillMaxWidth(), description = "I am ill today. Today becomes a day off and costs the same string with the 40 extra.")

        if (p != null) Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("WHAT IT COSTS")
            p.lines.forEach { BasicText(it, style = Ts(c.text, 15.sp, if (it == p.lines.first()) FontWeight.Bold else FontWeight.Normal)) }
            if (p.work.isNotEmpty()) BasicText("The lectures and study blocks of the day come back as ${p.work.size} piece${if (p.work.size == 1) "" else "s"} of work, due by the end of the next working day.", style = Ts(c.muted, 13.sp))
            BasicText("The string is ${p.wall} characters (${ui.usesIn30} used in the last 30 days${if (ui.selected.firstOrNull() == ui.today) ", plus 40 for asking on the day" else ""}).", style = Ts(c.muted, 13.sp))
            Btn("Plan it: type ${p.wall} characters", onPlan, primary = true, modifier = Modifier.fillMaxWidth())
        }

        if (ui.claimable.isNotEmpty()) {
            Label("FORGOT TO ASK?")
            BasicText("A day that has passed can be claimed with the same string plus 40. It holds only if the work that came back from that day is done by its due times. Until then the time stays lost.", style = Ts(c.muted, 13.sp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.claimable.forEach { d -> Chip(DAY.format(d), false, { onClaim(d) }, "Claim a day off for ${DAY.format(d)}") }
            }
        }
        if (ui.upcoming.isNotEmpty()) Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("PLANNED")
            ui.upcoming.forEach { BasicText(it, style = Ts(c.text, 15.sp)) }
        }
        BasicText("A night of 3 hours of sleep or less makes the next day a day off with no typing. It counts as a use, so the next one costs more.", style = Ts(c.muted, 13.sp))
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}
