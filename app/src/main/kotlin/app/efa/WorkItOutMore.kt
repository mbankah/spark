package app.efa

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.*
import efa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val HM_OUT = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun Field(value: String, onChange: (String) -> Unit, hint: String, description: String) {
    val c = LocalColors.current
    androidx.compose.foundation.text.BasicTextField(
        value, onChange, textStyle = Ts(c.text, 16.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent), singleLine = true,
        modifier = Modifier.fillMaxWidth().card().padding(horizontal = 14.dp, vertical = 14.dp).androidSemantics(description),
        decorationBox = { inner -> if (value.isEmpty()) BasicText(hint, style = Ts(c.muted, 16.sp)); inner() },
    )
}

private fun Modifier.androidSemantics(d: String): Modifier = this.then(Modifier.semantics { contentDescription = d })

/** One line of a permission and the way to give it. */
private data class Perm(val name: String, val ok: Boolean, val why: String, val open: Intent?)

private fun permissions(ctx: Context): List<Perm> {
    fun granted(p: String) = ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    // HA11: Notifications and Blocking have cards of their own just above this list, so they are not listed twice
    return listOf(
        Perm("Camera", granted("android.permission.CAMERA"), "The proof photos.", details),
        Perm("Location", granted("android.permission.ACCESS_FINE_LOCATION"), "Lecture attendance and out.", details),
        Perm("Exact alarms", android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms(), "The wake alarm and reminders ring on time.", Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))),
        Perm("Battery: not optimised", pm.isIgnoringBatteryOptimizations(ctx.packageName), "Stops the phone killing Spark at night.", Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)),
        Perm("Greyscale at wind-down", WindDown.available(ctx), "Optional. One command from your PC: " + WindDown.COMMAND, null),
    )
}

/** SH2: which half of the page Right now a piece draws: what is true now (always shown), or the shelves of everything else. */
enum class WioPart { NOW, SHELVES }

/**
 * The rest of Right now: lite day (LT1) and the nap (SL7) are shown; the Emergency door (EM1), "I'm out" (EM2), the lost-phone steps (EM5), the university's routes (HO4), tomorrow's fun (SL4) and
 * the coach (CO3, CO10, CO11) are on shelves. Your reminders (SL6), the family home (HO2) and the permission list (CT4) are in Setup (SH2), where the other settings are.
 */
@Composable
fun WorkItOutMore(db: EfaDb, onOpenPlan: (Instance, Boolean) -> Unit, part: WioPart) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var outUntil by remember { mutableStateOf<Long?>(null) }
    var lite by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf(true) }
    var tone by remember { mutableStateOf(Tone.CALM) }
    var ident by remember { mutableStateOf(listOf("", "", "")) }
    var nap by remember { mutableStateOf(false) }

    LaunchedEffect(tick) {
        withContext(Dispatchers.IO) {
            outUntil = db.meta().get(META_OUT_UNTIL)?.toLongOrNull()?.takeIf { it > System.currentTimeMillis() }
            lite = db.meta().get(META_LITE) == "1"; voice = db.meta().get(META_COACH) != "0"; tone = Tone.of(db.meta().get(META_TONE))
            ident = (db.meta().get(META_IDENTITY).orEmpty().lines() + listOf("", "", "")).take(3)
            nap = napOffer(db.nights().latest()?.sleepMin?.div(60.0), ZonedDateTime.now(LONDON))
        }
    }
    fun put(key: String, value: String) { scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(key, value)); tick++ } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (part == WioPart.NOW) {
            if (nap) Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("AFTER A SHORT NIGHT")
                BasicText("A 20 minute nap before 15:00 is fine today. A longer or later one works against tonight.", style = Ts(c.text, 14.sp))
            }

            // LT1
            Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("LITE DAY")
                BasicText("The home shows three things: now, next and the phone. Blocking stays. Pair it with a lighter day if you need the work set aside.", style = Ts(c.muted, 13.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Lite on", lite, { put(META_LITE, "1") }, "Lite day on"); Chip("Lite off", !lite, { put(META_LITE, "0") }, "Lite day off")
                }
            }
        } else {
            // HA10: the three Emergency buttons are on Home (EM1), so the shelf holds only what Home does not
            Shelf("Out, lost phone, university", "I am out, a lost phone, the university") {
                // EM2: out is honoured only away from home
                Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("I AM OUT")
                    val until = outUntil
                    message?.let { BasicText(it, style = Ts(c.text, 14.sp, FontWeight.Bold)) }       // what out could not do, said where it was asked (it was on the Emergency card, which is on Home now)
                    if (until != null) {
                        BasicText("Out until ${java.time.Instant.ofEpochMilli(until).atZone(LONDON).format(HM_OUT)}. Blocking and the night rule are paused while the phone is away from home.", style = Ts(c.text, 14.sp))
                        Btn("I'm back", { scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(META_OUT_UNTIL, "0")); BlockingState.refresh(ctx); tick++ } }, modifier = Modifier.fillMaxWidth(), description = "I am back")
                    } else {
                        BasicText("Blocking and the night rule pause until the time you choose, but only while the phone is away from home. At home it does nothing.", style = Ts(c.muted, 13.sp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 4, 6).forEach { h -> Chip("${h}h", false, {
                                scope.launch(Dispatchers.IO) {
                                    val fix = LocationFix.current(ctx, 6_000); val home = PlaceRepo(db).home()
                                    val away = fix != null && home != null && distanceM(fix.lat, fix.lon, home.first, home.second) > 250
                                    if (!away) { message = "You are at home, or the place could not be read, so out does not apply."; return@launch }
                                    val now = Instant.now()
                                    val d = DoorRepo(db).decide(gateDoor(Door.OUT, now, h * 60, verified = true), now)
                                    if (d is DoorDecision.Locked) { message = d.reason; return@launch }
                                    DoorRepo(db).record(gateDoor(Door.OUT, now, h * 60, verified = true))
                                    db.meta().put(MetaRow(META_OUT_UNTIL, now.plusSeconds(h * 3600L).toEpochMilli().toString())); BlockingState.refresh(ctx); message = null; tick++
                                }
                            }, "I am out for $h hours") }
                        }
                    }
                }

                // EM5
                Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("IF THE PHONE IS LOST OR BROKEN")
                    LOST_PHONE_STEPS.forEachIndexed { i, s -> BasicText("${i + 1}. $s", style = Ts(c.text, 14.sp)) }
                }

                // HO4
                Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("IF YOU ARE BEHIND OR STRUGGLING: THE UNIVERSITY")
                    SUPPORT_ROUTES.forEach { r -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText("${r.name} (${r.when_})", style = Ts(c.text, 15.sp, FontWeight.Bold)); BasicText(r.what, style = Ts(c.muted, 13.sp))
                    } }
                    BasicText("From the University of Birmingham's own pages. Check them for the current rules.", style = Ts(c.muted, 12.sp))
                }
            }

            // CO3, CO10, CO11
            Shelf("The coach", "Voice, tone and who you are trying to be") {
                Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("THE COACH")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Voice on", voice, { put(META_COACH, "1") }, "Coach voice on"); Chip("Voice off", !voice, { put(META_COACH, "0") }, "Coach voice off")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tone.entries.forEach { t -> Chip(t.label, tone == t, { put(META_TONE, t.name) }, "Tone ${t.label}") } }
                    Btn("Feel the patterns", { scope.launch(Dispatchers.IO) { Haptic.entries.forEach { Coach.haptic(ctx, it); kotlinx.coroutines.delay(900) } } }, modifier = Modifier.fillMaxWidth(), description = "Feel the haptic patterns")
                    BasicText("Three sentences about who you are trying to be. They are quoted back, sparingly, with a fact.", style = Ts(c.muted, 13.sp))
                    ident.forEachIndexed { i, s -> Field(s, { v -> ident = ident.toMutableList().also { it[i] = v } }, "I am someone who ...", "Identity sentence ${i + 1}") }
                    Btn("Save", { put(META_IDENTITY, ident.joinToString("\n") { it.replace("\n", " ") }) }, modifier = Modifier.fillMaxWidth(), description = "Save the sentences")
                }
            }
        }
    }
}

/** SL6, SH2: your own reminders (whatever you and your prescriber decided), now with the other reminders in Setup. Spark logs taken or not taken and gives no advice. */
@Composable
fun OwnRemindersCard(db: EfaDb, embedded: Boolean = false) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var reminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    var reminderLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var rName by remember { mutableStateOf("") }
    var rHour by remember { mutableIntStateOf(8) }
    var rMin by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        withContext(Dispatchers.IO) {
            reminders = decodeReminders(db.meta().get(META_REMINDERS))
            reminderLog = db.meta().get(META_REMINDER_LOG).orEmpty().lines().filter { it.isNotBlank() }.takeLast(7).reversed().map { l -> l.split("|").let { p -> if (p.size == 3) java.time.Instant.ofEpochMilli(p[0].toLongOrNull() ?: 0).atZone(LONDON).format(DateTimeFormatter.ofPattern("EEE HH:mm")) + " " + p[1] + ": " + (if (p[2] == "TAKEN") "taken" else "not taken") else l } }
        }
    }
    fun put(key: String, value: String) { scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(key, value)); tick++ } }
    Column(if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("YOUR REMINDERS")
        BasicText("Whatever you and your prescriber decided. Spark logs taken or not taken for you to show them. It gives no advice on doses or times.", style = Ts(c.muted, 13.sp))
        reminders.forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            BasicText("${r.time.format(HM_OUT)} ${r.name}", style = Ts(c.text, 15.sp), modifier = Modifier.weight(1f))
            Chip("Remove", false, { put(META_REMINDERS, encodeReminders(reminders - r)); scope.launch(Dispatchers.IO) { ReminderAlarm.refresh(ctx) } }, "Remove the reminder ${r.name}")
        } }
        Field(rName, { rName = it }, "Name", "Reminder name")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Chip("-", false, { rHour = (rHour + 23) % 24 }, "Earlier hour"); BasicText("%02d".format(rHour), style = Ts(c.text, 18.sp, FontWeight.Bold)); Chip("+", false, { rHour = (rHour + 1) % 24 }, "Later hour")
            BasicText(":", style = Ts(c.text, 18.sp)); Chip("-", false, { rMin = (rMin + 55) % 60 }, "Earlier minutes"); BasicText("%02d".format(rMin), style = Ts(c.text, 18.sp, FontWeight.Bold)); Chip("+", false, { rMin = (rMin + 5) % 60 }, "Later minutes")
        }
        Btn("Add the reminder", { if (rName.isNotBlank()) { put(META_REMINDERS, encodeReminders(reminders + Reminder(rName.trim(), LocalTime.of(rHour, rMin)))); scope.launch(Dispatchers.IO) { kotlinx.coroutines.delay(300); ReminderAlarm.refresh(ctx) }; rName = "" } }, modifier = Modifier.fillMaxWidth(), description = "Add the reminder")
        reminderLog.forEach { BasicText(it, style = Ts(c.muted, 12.sp)) }
    }
}

/** HO2, SH2: the family home, now with the other places in Setup. */
@Composable
fun FamilyHomeCard(db: EfaDb, embedded: Boolean = false) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var family by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick) { withContext(Dispatchers.IO) { family = db.meta().get(META_FAMILY_HOME) } }
    Column(if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("FAMILY HOME")
        BasicText(if (family == null) "Not set. Stand at the family home and set it, so Spark can tell which home you are at." else "Set. Spark picks flat or family home when you are within 150 m of it.", style = Ts(c.muted, 13.sp))
        Btn("Set the family home to where I am", { scope.launch(Dispatchers.IO) { val f = LocationFix.current(ctx, 8_000); if (f != null) { db.meta().put(MetaRow(META_FAMILY_HOME, "${f.lat}|${f.lon}")); tick++; message = "Family home set." } else message = "The place could not be read. Try near a window." } }, modifier = Modifier.fillMaxWidth(), description = "Set the family home here")
        message?.let { BasicText(it, style = Ts(c.text, 14.sp, FontWeight.Bold)) }
    }
}

/** CT4, SH2: the permission list after a reinstall or a phone update, now with the other permission checks in Setup. */
@Composable
fun PermissionsCard() {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var perms by remember { mutableStateOf<List<Perm>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick) { withContext(Dispatchers.IO) { perms = permissions(ctx) } }
    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("PERMISSIONS (AFTER A REINSTALL OR PHONE UPDATE)")
        perms.forEach { p ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { BasicText((if (p.ok) "On: " else "Off: ") + p.name, style = Ts(c.text, 15.sp, FontWeight.Bold)); BasicText(p.why, style = Ts(c.muted, 12.sp)) }
                if (!p.ok && p.open != null) Chip("Open", false, { try { ctx.startActivity(p.open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); tick++ } catch (e: Exception) { message = "That setting could not be opened." } }, "Open the setting for ${p.name}")
            }
        }
        message?.let { BasicText(it, style = Ts(c.text, 14.sp, FontWeight.Bold)) }
        Btn("Check again", { tick++ }, modifier = Modifier.fillMaxWidth(), description = "Check the permissions again")
    }
}
