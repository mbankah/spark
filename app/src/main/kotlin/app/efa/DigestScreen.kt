package app.efa

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.Digest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The digest of held notifications (LA2): one screen, in time order, with the app name, shown after the block. It has no count and no
 * badge (WG7). Items whose app is still blocked wait, and the screen says until when. "Seen" keeps them for 30 days, never deletes
 * them at once.
 */
@Composable
fun DigestScreen(d: Digest, zone: ZoneId, onOpenApp: (String) -> Unit, onSeen: () -> Unit, onBack: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("DIGEST")
        BasicText("Held notifications", style = Ts(c.text, 28.sp, FontWeight.Bold))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (d.ready.isEmpty()) {
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(
                        if (d.waitingUntil != null) "Still blocked until ${HM.format(d.waitingUntil)}." else "Nothing is waiting.",
                        style = Ts(c.text, 18.sp, FontWeight.Bold),
                    )
                    if (d.waitingUntil != null) BasicText("Held notifications from your blocked apps appear here after the block. Nothing is deleted.", style = Ts(c.muted, 14.sp))
                }
            }
            d.ready.forEach { i ->
                val time = HM.format(Instant.ofEpochMilli(i.atMs).atZone(zone))
                Column(
                    Modifier.fillMaxWidth().card().clip(CardShape).clickable(role = Role.Button) { onOpenApp(i.pkg) }.padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics { contentDescription = "$time, ${i.label}. ${i.title}. ${i.text}. Tap to open ${i.label}." },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Label("$time  ${i.label}")
                    if (i.title.isNotBlank()) BasicText(i.title, style = Ts(c.text, 16.sp, FontWeight.Bold))
                    if (i.text.isNotBlank()) BasicText(i.text, style = Ts(c.muted, 14.sp))
                }
            }
            if (d.ready.isNotEmpty() && d.waitingUntil != null)
                BasicText("More are held until ${HM.format(d.waitingUntil)}, while their apps are still blocked.", style = Ts(c.muted, 13.sp))
        }
        if (d.ready.isNotEmpty()) Btn("Seen", onSeen, primary = true, modifier = Modifier.fillMaxWidth(), description = "Mark these notifications as seen")
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}
