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
import app.efa.data.MoneyView
import efa.*
import java.time.format.DateTimeFormatter

/**
 * The money guide with its working (R6 to R9, G6). The app never touches a bank: this is a guide. Before the first live week it says when money
 * starts and shows what would be paid, so the first 14 days are only recorded (G6).
 */
@Composable
fun MoneyScreen(v: MoneyView, onBack: () -> Unit) {
    val c = LocalColors.current
    val w = v.week
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("MONEY, A GUIDE ONLY")
        BasicText(if (v.live) pounds(w.money.freeToSpendPence) + " free to spend" else "Recording", style = Ts(c.text, 32.sp, FontWeight.Bold))
        BasicText(w.progress() + " this week", style = Ts(c.muted, 15.sp, fontFamily = Mono))
        if (!v.live) Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
            BasicText("Money starts on ${DateTimeFormatter.ofPattern("EEE d MMM").format(v.liveFrom)}. Until then it is only recorded, with no penalties. Below is what this week would pay.", style = Ts(c.text, 15.sp))
        }
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("THE WORKING")
            w.working().forEach { BasicText(it, style = Ts(c.text, 14.sp)) }
        }
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("SAVINGS")
            BasicText("Saved so far: ${pounds(v.savedPence)} over ${v.closedWeeks} week${if (v.closedWeeks == 1) "" else "s"}.", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("At the end of each week, what you did not earn of the ${pounds(OTHER_PENCE)} moves to savings. At least ${pounds(MIN_SAVED_PENCE)} is saved every week.", style = Ts(c.muted, 13.sp))
        }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}
