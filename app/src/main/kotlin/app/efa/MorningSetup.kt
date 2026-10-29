package app.efa

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.RefKind

/** What a reference photo looks like to the person: when it was taken and where the picture is, or null when none is set. */
data class RefInfo(val takenText: String, val path: String, val stale: Boolean = false)

data class MorningSetupStatus(val night: RefInfo? = null, val standing: RefInfo? = null, val ready: RefInfo? = null, val alarmText: String? = null)

/**
 * The morning set up (M1, M4, M8): the photo of tomorrow's setup taken the night before, the optional standing reference, and the ready spot.
 * Photos are taken with the in-app camera only (A7) and a new one replaces the old.
 */
@Composable
fun MorningSetupScreen(s: MorningSetupStatus, message: String?, onTake: (String) -> Unit, onClearStanding: () -> Unit, onBack: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("MORNING")
        BasicText("Set up mornings", style = Ts(c.text, 28.sp, FontWeight.Bold))
        s.alarmText?.let { BasicText(it, style = Ts(c.text, 16.sp, FontWeight.Bold)) }
        BasicText("The alarm stops with a live photo that matches a reference, and social stays blocked until you photograph the last getting-ready step. Photos stay on this phone.", style = Ts(c.muted, 14.sp))
        message?.let { Column(Modifier.fillMaxWidth().card().padding(16.dp)) { BasicText(it, style = Ts(c.text, 15.sp, FontWeight.Bold)) } }
        RefRow("TONIGHT'S SETUP", "The night before, set up your study space and breakfast equipment, then photograph it from where you will stand in the morning. Pick a spot you cannot see from your bed.", s.night, "Take tonight's setup photo", { onTake(RefKind.NIGHT) })
        RefRow("READY SPOT", "The last getting-ready step at a fixed spot, such as the bag by the door. Taken once. This photo ends the social block.", s.ready, "Take the ready spot photo", { onTake(RefKind.READY) })
        RefRow("STANDING REFERENCE (OPTIONAL)", "If your setup moves around, add a fixed spot the alarm can also match.", s.standing, "Take a standing reference", { onTake(RefKind.STANDING) })
        if (s.standing != null) Btn("Remove the standing reference", onClearStanding, modifier = Modifier.fillMaxWidth())
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RefRow(title: String, why: String, info: RefInfo?, action: String, onTake: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(title)
        BasicText(if (info == null) "Not set." else "Set ${info.takenText}." + if (info.stale) " Too old to count: take tonight's." else "", style = Ts(c.text, 16.sp, FontWeight.Bold))
        BasicText(why, style = Ts(c.muted, 13.sp))
        if (info != null) {
            val bmp = remember(info.path, info.takenText) { BitmapFactory.decodeFile(info.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }
            if (bmp != null) Image(bmp, "The saved photo for $title", Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(CardShape).semantics { contentDescription = "The saved photo for $title" }, contentScale = ContentScale.Crop)
        }
        Btn(action, onTake, primary = info == null, modifier = Modifier.fillMaxWidth())
    }
}
