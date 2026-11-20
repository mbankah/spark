package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** WT1: what the wake time card shows: the next alarm in words, whether a time was chosen, the time the picker starts at, and the last answer. */
data class WakeUi(val text: String = "", val chosen: Boolean = false, val hour: Int = 10, val minute: Int = 30, val message: String? = null)

/** CD1: the things that can change the plan, each said the way it happens. Every one leads to the screen that previews it, prices it and applies it. */
enum class ChangeChoice(val title: String, val say: String, val group: String) {
    OVER("It is running over", "This is taking longer than planned. Choose how much more time.", "RIGHT NOW"),
    LATE("I started late", "Start the rest of today from now.", "RIGHT NOW"),
    CAME_UP("Something came up", "Say what happened and Spark finds a place for it.", "RIGHT NOW"),
    DEADLINE("A new deadline", "Add work that is due, with how heavy it is.", "COMING UP"),
    DAY_OFF("A day off or a weekend", "Plan a day off or say you are ill. The cost to the Buffer is shown first.", "COMING UP"),
    LIBRARY("A library period", "Work blocks move to the library for 1 to 14 days.", "COMING UP"),
    FILE("A file from my timetable", "Lectures that moved, deadlines, assignment weeks and catch-up weeks.", "COMING UP"),
    PERIOD("A week or a period", "Assignment week, catch-up, a period off, a focus week, the Admin library and the essentials.", "COMING UP"),
}

/**
 * CD1 to CD3: Change the plan. One place for everything that moves the plan, so a change is said, not hunted for in Setup. Each choice opens the screen that shows what it would do before anything
 * moves, what it costs (the wall, the Buffer) and applies it. The wake time for tonight is part of the same place.
 */
@Composable
fun ChangeDoorScreen(onChoose: (ChangeChoice) -> Unit, onBack: () -> Unit, wake: WakeUi = WakeUi(), onWakeSet: (Int, Int) -> Unit = { _, _ -> }, onWakeClear: () -> Unit = {}) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText("Change the plan", style = Ts(c.text, 28.sp, FontWeight.SemiBold))
        BasicText("Say what is happening. Each one shows what it does before anything moves.", style = Ts(c.muted, 15.sp))
        listOf("RIGHT NOW", "COMING UP").forEach { g ->
            Label(g)
            ChangeChoice.entries.filter { it.group == g }.forEach { ch -> ChoiceRow(ch.title, ch.say) { onChoose(ch) } }
        }
        WakeCard(wake, onWakeSet, onWakeClear)
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChoiceRow(title: String, say: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxWidth().card().clip(CardShape).clickable(role = Role.Button, onClick = onClick).padding(16.dp).semantics { contentDescription = "$title. $say" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(title, style = Ts(c.text, 17.sp, FontWeight.Bold))
        BasicText(say, style = Ts(c.muted, 14.sp))
    }
}

/** WT1: choosing when the next alarm rings. */
@Composable
fun WakeCard(wake: WakeUi, onWakeSet: (Int, Int) -> Unit, onWakeClear: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("WAKE TIME")
        BasicText(wake.text.ifBlank { "Next alarm not worked out yet." }, style = Ts(c.text, 16.sp, FontWeight.Bold))
        BasicText("Choose when the next alarm rings. The normal alarm is about 8 hours after you fall asleep, and the latest it can be is 12:00. A later time than the normal one needs the wall. The morning starts when you wake: Wake, Bible, breakfast and the day follow it, and anything fixed before it is missed and comes back as work.", style = Ts(c.muted, 14.sp))
        var minutes by remember(wake.hour, wake.minute) { mutableIntStateOf(wake.hour * 60 + wake.minute) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("− 15", { minutes = (minutes - 15 + 1440) % 1440 }, description = "Wake time 15 minutes earlier")
            BasicText("%02d:%02d".format(minutes / 60, minutes % 60), style = Ts(c.text, 34.sp, FontWeight.Bold, fontFamily = Mono), modifier = Modifier.weight(1f).semantics { contentDescription = "Wake time %02d:%02d".format(minutes / 60, minutes % 60) })
            Btn("+ 15", { minutes = (minutes + 15) % 1440 }, description = "Wake time 15 minutes later")
        }
        Btn("Set this wake time", { onWakeSet(minutes / 60, minutes % 60) }, primary = true, modifier = Modifier.fillMaxWidth(), description = "Set the wake time to %02d:%02d".format(minutes / 60, minutes % 60))
        if (wake.chosen) Btn("Use the normal alarm", onWakeClear, modifier = Modifier.fillMaxWidth())
        wake.message?.let { BasicText(it, style = Ts(c.text, 14.sp, FontWeight.SemiBold)) }
    }
}
