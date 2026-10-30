package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.CAL_LESS_CHOICES
import efa.CAL_MORE_CHOICES
import efa.TookReport
import efa.WallLength
import efa.fmt1

/** A finished task waiting to be asked about: what Spark estimated and how it is corrected so far (B5, E9). */
data class ReviewTask(val id: Long, val title: String, val estimatedHours: Double, val note: String)

/**
 * "How long did it really take?" for a finished task (B5). Saying it took as long as planned, or longer, is free. Saying it took less loosens the Buffer for
 * everything of that size, so it goes through the typed wall (R10), the same as any other loosening.
 */
@Composable
fun ReviewScreen(task: ReviewTask, onReport: (TookReport) -> Unit, onBack: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("HOW LONG DID IT TAKE")
        BasicText(task.title, style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText("Spark planned ${fmt1(task.estimatedHours)}h for it. Your answer quietly corrects the next estimates of the same size. ${task.note}", style = Ts(c.muted, 14.sp))
        Btn("About as planned", { onReport(TookReport.AsPlanned) }, primary = true, modifier = Modifier.fillMaxWidth())
        Label("IT NEEDED MORE")
        CAL_MORE_CHOICES.forEach { m -> Btn("It needed ${if (m < 60) "$m minutes" else "${m / 60} hour${if (m == 60) "" else "s"}"} more", { onReport(TookReport.MoreMinutes(m)) }, modifier = Modifier.fillMaxWidth()) }
        BasicText("It goes back in the Buffer with that time added.", style = Ts(c.muted, 13.sp))
        Label("IT TOOK LESS")
        CAL_LESS_CHOICES.forEach { f -> Btn("It took ${(f * 100).toInt()}% of the time: type ${WallLength.CALIBRATE_LESS} characters", { onReport(TookReport.LessFraction(f)) }, modifier = Modifier.fillMaxWidth(), description = "It took ${(f * 100).toInt()} percent of the planned time. Needs the typed wall.") }
        BasicText("Saying it took less makes future estimates smaller and the Buffer looser, so it needs the typed wall.", style = Ts(c.muted, 13.sp))
        Btn("Ask me later", onBack, modifier = Modifier.fillMaxWidth())
    }
}
