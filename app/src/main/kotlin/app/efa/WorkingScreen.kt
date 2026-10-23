package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.BufferResult

/** B3: tapping the Buffer shows every task, its heaviness, its hours and the sum. Nothing is hidden (P4). */
@Composable
fun WorkingScreen(r: BufferResult, onBack: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText("Buffer working", style = Ts(c.text, 28.sp, FontWeight.SemiBold))
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("THE TIGHTEST DEADLINE SETS THE NUMBER")
            BasicText(r.working(), style = Ts(c.text, 15.sp, fontFamily = Mono))
        }
        BasicText(
            "Available time is study and project blocks before the deadline. Breaks, meals and travel are not counted. " +
                (if (r.shortSleep) "Short night: hard work x1.5, easy work x1.15." else "A short night would make remaining work heavier."),
            style = Ts(c.muted, 14.sp),
        )
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}
