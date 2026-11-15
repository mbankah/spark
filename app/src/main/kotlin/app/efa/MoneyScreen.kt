package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.MoneyView
import efa.*
import java.time.format.DateTimeFormatter

/**
 * The money guide with its working (R6 to R9, G6). The app never touches a bank: this is a guide. Before the first live week it says when money
 * starts and shows what would be paid, so the first 14 days are only recorded (G6). The working is laid out as a receipt and a table: what the
 * week pays, then every confirmed block with its heaviness, hours and weight. Each row is also read out as the line it replaces (R8).
 */
@Composable
fun MoneyScreen(v: MoneyView, onBack: () -> Unit) {
    val c = LocalColors.current
    val w = v.week
    val lines = w.working()          // food, one line per confirmed block, confirmed of planned, free to spend, savings
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("Money, a guide only")
        Column(Modifier.fillMaxWidth().card(efa.CardKind.SPEND).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(if (v.live) pounds(w.money.freeToSpendPence) + " free to spend" else "Recording", style = Ts(c.text, 34.sp, FontWeight.Bold, letterSpacing = (-1).sp))
            Bar(if (w.plannedHours > 0) (w.verifiedHours / w.plannedHours).toFloat() else 0f, height = 10.dp)
            BasicText(w.progress() + " this week", style = Ts(c.muted, 14.sp, fontFamily = Mono))
        }
        if (!v.live) Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("Money starts on ${DateTimeFormatter.ofPattern("EEE d MMM").format(v.liveFrom)}.", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Until then it is only recorded, with no penalties. Below is what this week would pay.", style = Ts(c.muted, 14.sp))
        }
        // The receipt: what each part of the week comes to.
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("This week pays")
            Receipt("Food", pounds(FOOD_PENCE), "guaranteed, never tied to work", lines.first())
            Receipt("Free to spend", pounds(w.money.freeToSpendPence), "${pounds(FREE_CAP_PENCE)} x ${fmt1(w.verifiedWeight)} / ${fmt1(w.plannedWeight)}", lines[lines.size - 2], strong = true)
            Receipt("Moved to savings", pounds(w.money.savingsPence), null, lines.last())
        }
        // The table: every confirmed block, grouped, each with heaviness x hours = weight.
        val earned = w.blocks.filter { it.creditedMin > 0 }
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("Confirmed work"); Label("Weight") }
            if (earned.isEmpty()) BasicText("Nothing confirmed yet.", style = Ts(c.muted, 14.sp))
            earned.forEach { BlockRow(it) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.track))
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = lines[lines.size - 3] }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText("Confirmed of planned", style = Ts(c.text, 15.sp, FontWeight.SemiBold))
                BasicText("${fmt1(w.verifiedWeight)} of ${fmt1(w.plannedWeight)}", style = Ts(c.text, 16.sp, FontWeight.Bold, fontFamily = Mono))
            }
        }
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Label("Savings")
                BasicText(pounds(v.savedPence), style = Ts(c.text, 24.sp, FontWeight.Bold, letterSpacing = (-0.5).sp))
            }
            BasicText("Saved so far: ${pounds(v.savedPence)} over ${v.closedWeeks} week${if (v.closedWeeks == 1) "" else "s"}.", style = Ts(c.text, 15.sp))
            BasicText("Each week, what you do not earn of the ${pounds(OTHER_PENCE)} moves to savings, never less than ${pounds(MIN_SAVED_PENCE)}.", style = Ts(c.muted, 13.sp))
        }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

/** A name on the left, a note under it, the amount on the right. Read out as the working line it stands for. */
@Composable
private fun Receipt(name: String, amount: String, note: String?, said: String, strong: Boolean = false) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = said }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(name, style = Ts(c.text, 16.sp, if (strong) FontWeight.Bold else FontWeight.Medium))
            note?.let { BasicText(it, style = Ts(c.muted, 12.sp, fontFamily = Mono)) }
        }
        if (strong) BasicText(amount, style = Ts(c.onAccent, 18.sp, FontWeight.Bold), modifier = Modifier.background(c.accent, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
        else BasicText(amount, style = Ts(c.text, 18.sp, FontWeight.Bold))
    }
}

/** One confirmed block: its title, "heaviness x hours" under it, and its weight. */
@Composable
private fun BlockRow(b: WeekBlock) {
    val c = LocalColors.current
    val hours = fmt1(minOf(b.creditedMin, b.plannedMin) / 60.0)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${b.title}: ${b.heaviness} x ${hours}h = ${fmt1(b.verifiedWeight)}" },
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(b.title.substringBefore(" - "), style = Ts(c.text, 14.sp, FontWeight.Medium))
            BasicText("${b.heaviness} x ${hours}h", style = Ts(c.muted, 12.sp, fontFamily = Mono))
        }
        BasicText(fmt1(b.verifiedWeight), style = Ts(c.text, 15.sp, FontWeight.Bold, fontFamily = Mono))
    }
}
