package app.efa

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.*
import efa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * The link to the Windows service (A2, A4, A6, K4, K9). The phone works out when the listed sites are blocked for the next hours and sends that, signed with the
 * pairing code the PC shows. The service enforces it. Nothing here receives anything from the PC, and nothing leaves the local network unless the person types
 * an address outside it. Failure behaviour (X7): if the PC cannot be reached the phone says so and keeps trying every minute; the PC keeps enforcing the last state it
 * was given, which covers [PC_HOURS_AHEAD] hours.
 */
object PcLink {
    data class Result(val ok: Boolean, val message: String)

    /** Builds the signed state from the blocking snapshot and sends it. [now] is the clock the windows start from. */
    suspend fun push(ctx: Context, now: java.time.ZonedDateTime = BlockingState.now()): Result = withContext(Dispatchers.IO) {
        val db = EfaDb.get(ctx)
        val addr = parsePcAddress(db.meta().get(META_PC_ADDRESS).orEmpty()); val code = db.meta().get(META_PC_CODE).orEmpty().trim()
        if (addr == null || code.isEmpty()) return@withContext Result(false, "No PC linked.")
        val s = BlockingState.snapshot ?: return@withContext Result(false, "The plan is not loaded yet.")
        val owner = APP_DOMAINS.keys.firstOrNull { it in s.list }
        val windows = if (owner == null) emptyList() else pcWindows(now, now.plusHours(PC_HOURS_AHEAD)) { t -> decide(owner, t, s.pattern, s.list, Allowances(), s.verified, s.morning, s.lateness) }
        val body = pcStateJson(Instant.now(), pcDomains(s.list), windows)
        val result = send(addr, code, body)
        db.meta().put(MetaRow(META_PC_LAST, "${System.currentTimeMillis()}|${if (result.ok) "" else result.message}"))
        result
    }

    fun send(addr: PcAddress, code: String, body: String): Result {
        val c = URL("http://${addr.host}:${addr.port}/state").openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 4_000; c.readTimeout = 4_000
            c.setRequestProperty("X-Spark-Signature", pcSignature(code, body)); c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            when (val code2 = c.responseCode) {
                200 -> Result(true, "Sent.")
                400 -> Result(false, "The PC refused it. Check the pairing code.")
                409 -> Result(true, "The PC already has this.")          // a repeat of the same state is not a failure
                else -> Result(false, "The PC answered $code2.")
            }
        } catch (e: java.io.IOException) { Result(false, "Could not reach ${addr.host}. Is the PC on, on the same network, with Spark Guard running?") }
        finally { c.disconnect() }
    }

    /** The minute loop calls this. It does nothing when no PC is linked. */
    suspend fun tick(ctx: Context) { try { if (EfaDb.get(ctx).meta().get(META_PC_ADDRESS) != null) push(ctx) } catch (e: Exception) { /* X7: never breaks blocking */ } }
}

/** Link to a Windows PC: its address and the pairing code it shows. */
@Composable
fun PcScreen(address: String, code: String, status: String, onSave: (String, String) -> Unit, onSend: () -> Unit, onBack: () -> Unit) {
    val c = LocalColors.current
    var a by remember(address) { mutableStateOf(address) }; var k by remember(code) { mutableStateOf(code) }
    val problem = if (a.isNotBlank() && parsePcAddress(a) == null) "That is not an address like 192.168.1.20 or 192.168.1.20:47831." else null
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("WINDOWS PC")
        BasicText("Link a Windows PC", style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText("Install Spark Guard on the PC. It shows an address and a pairing code. Type them here, and this phone tells the PC when the sites of your blocked apps are blocked. The PC blocks them in every browser by changing its hosts file, and the browser extension is a second layer.", style = Ts(c.muted, 14.sp))
        Label("PC ADDRESS")
        BasicTextField(a, { a = it.trim() }, singleLine = true, textStyle = Ts(c.text, 18.sp), cursorBrush = SolidColor(c.text), modifier = Modifier.fillMaxWidth().card().padding(16.dp).semantics { contentDescription = "PC address" })
        Label("PAIRING CODE")
        BasicTextField(k, { k = it.trim() }, singleLine = true, textStyle = Ts(c.text, 18.sp), cursorBrush = SolidColor(c.text), modifier = Modifier.fillMaxWidth().card().padding(16.dp).semantics { contentDescription = "Pairing code" })
        problem?.let { BasicText(it, style = Ts(c.text, 14.sp, FontWeight.Bold)) }
        Btn("Save and send", { if (problem == null && a.isNotBlank() && k.isNotBlank()) onSave(a, k) }, primary = true, modifier = Modifier.fillMaxWidth())
        Btn("Send now", onSend, modifier = Modifier.fillMaxWidth())
        BasicText(status, style = Ts(c.text, 15.sp, FontWeight.Bold))
        BasicText("A work block on the phone is a work block on the PC. Starting one from the PC is not possible: the plan decides. Passing the typed wall on the phone does not open the PC. Encrypted browser DNS, a VPN or a proxy can go around a hosts file, which is what the extension is for.", style = Ts(c.muted, 13.sp))
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}
