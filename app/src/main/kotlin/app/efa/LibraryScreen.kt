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
import efa.LibraryHours
import efa.LibraryPreview
import efa.minutesText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What the library screen shows: the opening hours (null until asked, O11), the dates chosen, the preview and the periods already planned. */
data class LibraryUi(
    val today: LocalDate, val hours: LibraryHours?, val editingHours: Boolean, val draftOpen: Int, val draftClose: Int,
    val start: LocalDate?, val days: Int, val preview: LibraryPreview?, val planned: List<Pair<Long, String>>, val message: String? = null,
)

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")
val LIBRARY_LENGTHS = listOf(1, 2, 3, 5, 7, 14)

/**
 * Plan a library period (C1, C2, O11, O13, PW1). The opening hours are asked for once and kept. Pick the first day and how many days, see what it does,
 * then plan it. Meals, football and lectures stay where they are.
 */
@Composable
fun LibraryScreen(
    ui: LibraryUi, onHours: (Int, Int) -> Unit, onEditHours: () -> Unit, onDraft: (Int, Int) -> Unit, onPick: (LocalDate, Int) -> Unit,
    onPlan: () -> Unit, onRemove: (Long) -> Unit, onBack: () -> Unit,
) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("PLANNED CHANGES")
        BasicText("Plan a library period", style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText("Work blocks move to the library, with a 15 minute walk each way that is taken from the work. Meals, football, lectures and routines stay where they are. Blocking works as in any work block.", style = Ts(c.muted, 14.sp))
        ui.message?.let { Column(Modifier.fillMaxWidth().card().padding(16.dp)) { BasicText(it, style = Ts(c.text, 15.sp, FontWeight.Bold)) } }

        if (ui.hours == null || ui.editingHours) {
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("OPENING HOURS, ASKED ONCE")
                BasicText("Spark cannot look up the library's hours, so tell it once. Check the library's own page.", style = Ts(c.muted, 13.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Opens earlier", { onDraft((ui.draftOpen - 30).coerceAtLeast(0), ui.draftClose) }, description = "Opens 30 minutes earlier")
                    Btn("Opens later", { onDraft((ui.draftOpen + 30).coerceAtMost(ui.draftClose - 30), ui.draftClose) }, description = "Opens 30 minutes later")
                }
                BasicText("Opens ${minutesText(ui.draftOpen)}", style = Ts(c.text, 16.sp, FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Closes earlier", { onDraft(ui.draftOpen, (ui.draftClose - 30).coerceAtLeast(ui.draftOpen + 30)) }, description = "Closes 30 minutes earlier")
                    Btn("Closes later", { onDraft(ui.draftOpen, (ui.draftClose + 30).coerceAtMost(24 * 60)) }, description = "Closes 30 minutes later")
                }
                BasicText("Closes ${minutesText(ui.draftClose)}", style = Ts(c.text, 16.sp, FontWeight.Bold))
                Btn("Save hours", { onHours(ui.draftOpen, ui.draftClose) }, primary = true, modifier = Modifier.fillMaxWidth())
            }
        } else {
            BasicText("Library hours: ${ui.hours.text()}", style = Ts(c.text, 15.sp, FontWeight.Bold))
            Btn("Change hours", onEditHours, modifier = Modifier.fillMaxWidth())

            Label("FIRST DAY")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..13).map { ui.today.plusDays(it.toLong()) }.forEach { d ->
                    val label = when (d) { ui.today -> "Today"; ui.today.plusDays(1) -> "Tomorrow"; else -> DAY.format(d) }
                    Chip(label, ui.start == d, { onPick(d, ui.days) }, "Library starts $label")
                }
            }
            Label("HOW MANY DAYS")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LIBRARY_LENGTHS.forEach { n -> Chip(if (n == 1) "1 day" else "$n days", ui.days == n, { onPick(ui.start ?: ui.today, n) }, "Library for $n day${if (n == 1) "" else "s"}") }
            }
            ui.preview?.let { p ->
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("WHAT IT DOES")
                    p.lines.forEach { BasicText(it, style = Ts(c.text, 15.sp, if (it == p.lines.first()) FontWeight.Bold else FontWeight.Normal)) }
                    Btn("Plan the library period", onPlan, primary = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (ui.planned.isNotEmpty()) Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("PLANNED")
            ui.planned.forEach { (id, text) ->
                BasicText(text, style = Ts(c.text, 15.sp))
                Btn("Remove", { onRemove(id) }, description = "Remove the library period $text")
            }
        }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}
