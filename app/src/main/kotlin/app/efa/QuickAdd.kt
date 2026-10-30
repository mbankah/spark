package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** What quick-add hands back (D2): a title, a deadline and one heaviness slider. */
data class NewTask(val title: String, val due: ZonedDateTime, val heaviness: Int)

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE d MMM")
private val TIMES = listOf(LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(17, 0), LocalTime.of(23, 59))

/**
 * Quick-add. Typed entry always works, with no voice needed (G7). Defaults mean a task takes a few seconds:
 * tomorrow 17:00, heaviness 5. The sum shows what saving does to the Buffer before it is saved (P4, P5).
 * [bufferIfAdded] returns the Buffer label with this task included.
 */
@Composable
fun QuickAddScreen(
    now: ZonedDateTime,
    bufferIfAdded: (NewTask) -> String,
    canvasInstalled: Boolean,
    onCheckCanvas: () -> Unit,
    onSave: (NewTask) -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalColors.current
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(now.toLocalDate().plusDays(1)) }
    var time by remember { mutableStateOf(LocalTime.of(17, 0)) }
    var heaviness by remember { mutableIntStateOf(QUICK_ADD_DEFAULT_HEAVINESS) }
    var message by remember { mutableStateOf<String?>(null) }
    val due = ZonedDateTime.of(day, time, now.zone)
    val draft = NewTask(title.trim(), due, heaviness)

    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText("Quick add", style = Ts(c.text, 28.sp, FontWeight.SemiBold))
        Label("TITLE")
        BasicTextField(
            value = title, onValueChange = { title = it; message = null }, singleLine = true,
            textStyle = Ts(c.text, 20.sp), cursorBrush = SolidColor(c.text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().card().padding(16.dp).semantics { contentDescription = "Title" },
        )
        Label("DEADLINE DAY")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..13).map { now.toLocalDate().plusDays(it.toLong()) }.forEach { d ->
                Chip(dayLabel(now.toLocalDate(), d), d == day, { day = d })
            }
        }
        Label("DEADLINE TIME")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TIMES.forEach { t -> Chip("%02d:%02d".format(t.hour, t.minute), t == time, { time = t }) }
        }
        Label("HEAVINESS ${heaviness} OF 10")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..10).forEach { h -> Chip("$h", h == heaviness, { heaviness = h }, "Heaviness $h of 10") }
        }
        BasicText(CalibrationState.cal.note(heaviness), style = Ts(c.muted, 14.sp))
        Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
            Label("IF YOU SAVE")
            BasicText(if (title.isBlank()) "Add a title to see the Buffer." else bufferIfAdded(draft), style = Ts(c.text, 20.sp, FontWeight.SemiBold))
            BasicText("Due ${DateTimeFormatter.ofPattern("EEE d MMM HH:mm").format(due)}", style = Ts(c.muted, 14.sp))
        }
        message?.let { BasicText(it, style = Ts(c.text, 16.sp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Btn("Save", { if (title.isBlank()) message = "Add a title first." else onSave(draft) }, primary = true, modifier = Modifier.weight(1f))
            Btn("Cancel", onCancel, modifier = Modifier.weight(1f))
        }
        Btn("Check Canvas", { if (canvasInstalled) onCheckCanvas() else message = "The Canvas app is not installed on this phone." }, modifier = Modifier.fillMaxWidth(),
            description = "Check Canvas. Opens the Canvas app so you can see what you have.")
    }
}

private fun dayLabel(today: LocalDate, d: LocalDate) = when (d) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> DAY_FMT.format(d)
}
