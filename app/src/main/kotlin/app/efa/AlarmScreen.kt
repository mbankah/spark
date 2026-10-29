package app.efa

import androidx.compose.foundation.background
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
import efa.Offer
import efa.StopMethod

/** What the alarm screen shows (M2, M10). [secondsLeft] counts down the short wait. */
data class AlarmUi(val time: String, val offer: Offer, val message: String?, val secondsLeft: Int? = null)

/**
 * The alarm screen: the photo first (M2), then after two failed attempts the steps, then a short wait (M10). The photo is the strong check;
 * steps and waiting are accepted as weaker and are marked so. Calls and messages are never locked by this screen (M10).
 */
@Composable
fun AlarmScreen(ui: AlarmUi, onTakePhoto: () -> Unit, onCameraBroken: () -> Unit, onStop: (StopMethod) -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label("ALARM")
        BasicText(ui.time, style = Ts(c.text, 88.sp, FontWeight.Bold, letterSpacing = (-3.5).sp, lineHeight = 88.sp))
        ui.message?.let { Column(Modifier.fillMaxWidth().card().padding(16.dp)) { BasicText(it, style = Ts(c.text, 16.sp, FontWeight.Bold)) } }
        Spacer(Modifier.weight(1f))
        when (val o = ui.offer) {
            is Offer.Photo -> {
                BasicText(if (o.attempt == 1) "Take the photo of your setup to stop the alarm." else "Try again. Take the photo of your setup, from where you stood the night before.", style = Ts(c.text, 20.sp, FontWeight.Bold))
                BasicText("Attempt ${o.attempt} of ${efa.PHOTO_ATTEMPTS}.", style = Ts(c.muted, 14.sp))
                Btn("Take the photo", onTakePhoto, primary = true, modifier = Modifier.fillMaxWidth())
                Btn("The camera is not working", onCameraBroken, modifier = Modifier.fillMaxWidth())
            }
            is Offer.Steps -> {
                BasicText("Walk ${o.need} steps to stop the alarm.", style = Ts(c.text, 20.sp, FontWeight.Bold))
                BasicText("${o.have} of ${o.need} steps. This is the weaker check, so it is marked.", style = Ts(c.muted, 14.sp), modifier = Modifier.semantics { contentDescription = "${o.have} of ${o.need} steps" })
            }
            is Offer.Wait -> {
                val s = ui.secondsLeft ?: 0
                BasicText("The alarm can be stopped in %d:%02d.".format(s / 60, s % 60), style = Ts(c.text, 20.sp, FontWeight.Bold))
                BasicText("Steps cannot be counted on this phone, so there is a short wait. This is the weakest check, so it is marked.", style = Ts(c.muted, 14.sp))
            }
            is Offer.CanStop -> {
                BasicText(if (o.method == StopMethod.STEPS) "Steps done." else "The wait is over.", style = Ts(c.text, 20.sp, FontWeight.Bold))
                Btn("Stop the alarm", { onStop(o.method) }, primary = true, modifier = Modifier.fillMaxWidth(), description = "Stop the alarm. Marked as a weaker check.")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            BasicText("Calls and messages still work.", style = Ts(c.muted, 13.sp))
        }
    }
}

/**
 * The ready photo (M8): the last getting-ready step at its fixed spot. It ends the social block, an event and not a timer. A photo that does not
 * match gets a second attempt, then a short wait (M10); steps are not used here, so the wait is the next rung. Marked as weaker when it was the wait.
 */
@Composable
fun ReadyScreen(ui: AlarmUi, onTakePhoto: () -> Unit, onCameraBroken: () -> Unit, onDone: (StopMethod) -> Unit, onBack: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Label("MORNING")
        BasicText("Ready?", style = Ts(c.text, 36.sp, FontWeight.Bold))
        BasicText("Photograph the last getting-ready step at its spot, such as the bag by the door. That ends the social block.", style = Ts(c.muted, 15.sp))
        ui.message?.let { Column(Modifier.fillMaxWidth().card().padding(16.dp)) { BasicText(it, style = Ts(c.text, 16.sp, FontWeight.Bold)) } }
        Spacer(Modifier.weight(1f))
        when (val o = ui.offer) {
            is Offer.Photo -> {
                BasicText("Attempt ${o.attempt} of ${efa.PHOTO_ATTEMPTS}.", style = Ts(c.muted, 14.sp))
                Btn("Take the ready photo", onTakePhoto, primary = true, modifier = Modifier.fillMaxWidth())
                Btn("The camera is not working", onCameraBroken, modifier = Modifier.fillMaxWidth())
            }
            is Offer.Wait -> {
                val s = ui.secondsLeft ?: 0
                BasicText("You can end the block in %d:%02d.".format(s / 60, s % 60), style = Ts(c.text, 20.sp, FontWeight.Bold))
                BasicText("The photo did not work, so there is a short wait. This is the weaker check, so it is marked.", style = Ts(c.muted, 14.sp))
            }
            is Offer.CanStop -> Btn("End the social block", { onDone(o.method) }, primary = true, modifier = Modifier.fillMaxWidth(), description = "End the social block. Marked as a weaker check.")
            is Offer.Steps -> {}
        }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}
