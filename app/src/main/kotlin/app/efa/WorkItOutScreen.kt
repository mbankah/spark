package app.efa

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

private fun clock(min: Int) = "%02d:%02d".format((min / 60) % 24, min % 60)

private fun DayType.nice() = when (this) {
    DayType.UNI -> "Uni day"; DayType.LIBRARY -> "Library day"; DayType.HOME_STUDY -> "Home study day"; DayType.REST -> "Rest day"; DayType.TRAVEL -> "Travel day"
    DayType.EXAM -> "Exam day"; DayType.DEADLINE -> "Deadline day"; DayType.MATCH -> "Match day"; DayType.CHURCH -> "Church day"; DayType.GUESTS -> "Guests day"
    DayType.ILL -> "Ill day"; DayType.LITE -> "Lite day"; DayType.HOLIDAY -> "Holiday"; DayType.OUT -> "Out day"
}

/** One line of text, drawn like the rest of the app. */
@Composable
private fun TextBox(value: String, onChange: (String) -> Unit, hint: String, description: String) {
    val c = LocalColors.current
    BasicTextField(
        value, onChange, textStyle = Ts(c.text, 16.sp), cursorBrush = SolidColor(c.accent), singleLine = true,
        modifier = Modifier.fillMaxWidth().card().padding(horizontal = 14.dp, vertical = 14.dp).semantics { contentDescription = description },
        decorationBox = { inner -> if (value.isEmpty()) BasicText(hint, style = Ts(c.muted, 16.sp)); inner() },
    )
}

/**
 * The third page: Work it out (HS4). It changes nothing. It shows what today looks like, works out a night and shows the numbers, lays out the winter vacation, and holds the small
 * things that help: a place for ideas that change nothing (PK1), how you are (CO5), your rewards (CO7), and what to do when you cannot sleep (SL3).
 * Anything that would change the plan goes to the normal preview and its cost, through [onOpenPlan].
 */
@Composable
fun WorkItOutScreen(db: EfaDb, onOpenPlan: (Instance, Boolean) -> Unit, modifier: Modifier = Modifier, onWall: (String, String, Int, () -> Unit) -> Unit = { _, _, _, _ -> }, moved: List<String> = emptyList()) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var place by remember { mutableStateOf(PlaceKind.FLAT) }
    var dayOk by remember { mutableStateOf<String?>(null) }
    var proposal by remember { mutableStateOf<DayProposal?>(null) }
    var ideas by remember { mutableStateOf<List<String>>(emptyList()) }
    var rewards by remember { mutableStateOf<List<String>>(emptyList()) }
    var awake by remember { mutableStateOf<List<String>>(emptyList()) }
    var lowRun by remember { mutableStateOf(false) }
    var lastMood by remember { mutableStateOf<Int?>(null) }
    var idea by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }
    var awakeItem by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now(LONDON) }

    LaunchedEffect(tick) {
        withContext(Dispatchers.IO) {
            place = db.meta().get(META_PLACE_NOW)?.let { runCatching { PlaceKind.valueOf(it) }.getOrNull() } ?: PlaceKind.FLAT
            dayOk = db.meta().get(META_DAYTYPE_OK)
            proposal = proposeDayType(today, place, null, PlanRepo(db).slots())
            ideas = db.meta().get(META_IDEAS).orEmpty().lines().filter { it.isNotBlank() }
            rewards = db.meta().get(META_REWARDS).orEmpty().lines().filter { it.isNotBlank() }
            awake = db.meta().get(META_AWAKE).orEmpty().lines().filter { it.isNotBlank() }
            val taps = db.meta().get(META_MOOD).orEmpty().lines().mapNotNull { l -> l.split("|").takeIf { it.size == 2 }?.let { p -> p[0].toLongOrNull()?.let { ms -> p[1].toIntOrNull()?.let { v -> MoodTap(Instant.ofEpochMilli(ms), v) } } } }
            lastMood = taps.lastOrNull()?.value
            val sleep = db.nights().all().takeLast(3).map { it.sleepMin / 60.0 }
            lowRun = lowMoodRun(taps, today) || shortNightRun(sleep)
        }
    }
    fun save(key: String, lines: List<String>) { scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(key, lines.joinToString("\n"))); tick++ } }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText("Right now", style = Ts(c.text, 30.sp, FontWeight.Bold, letterSpacing = (-0.8).sp))
        BasicText("What is true now. Nothing here changes your plan until you choose.", style = Ts(c.muted, 14.sp))

        // RS8: what Spark moved on its own, with the reason. It lives here and not on the home page, which is for now and next.
        if (moved.isNotEmpty()) Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("MOVED FOR YOU")
            BasicText(moved.first(), style = Ts(c.text, 16.sp, FontWeight.Bold))
            (moved.drop(1).take(2) + listOfNotNull(moved.size.minus(3).takeIf { it > 0 }?.let { "And $it more." })).forEach { BasicText(it, style = Ts(c.muted, 13.sp)) }
        }

        // CX3: the day type is proposed from where you are and what is on the calendar; you confirm it once
        proposal?.let { p ->
            Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("TODAY LOOKS LIKE")
                BasicText(p.type.nice(), style = Ts(c.text, 22.sp, FontWeight.Bold))
                BasicText(p.reason, style = Ts(c.muted, 13.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("At the flat", place == PlaceKind.FLAT, { scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(META_PLACE_NOW, PlaceKind.FLAT.name)); tick++ } }, "I am at the flat")
                    Chip("At the family home", place == PlaceKind.FAMILY_HOME, { scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(META_PLACE_NOW, PlaceKind.FAMILY_HOME.name)); tick++ } }, "I am at the family home")
                }
                if (dayOk != today.toString()) Btn("Yes, that is today", { scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(META_DAYTYPE_OK, today.toString())); tick++ } }, primary = true, modifier = Modifier.fillMaxWidth(), description = "Confirm today is ${p.type.nice()}")
                else BasicText("Confirmed for today.", style = Ts(c.muted, 13.sp))
            }
        }

        WorkItOutMore(db, onOpenPlan, WioPart.NOW)
        WorkItOutExtra(db, onWall, WioPart.NOW)

        // HA10: what if I sleep later is a choice in the Change door, and the winter vacation is under A week or a period

        Shelf("Calm and notes", "How you are, a place for ideas, your rewards, when you cannot sleep") {
        // CO5, CO6: how are you, and the people who can help after a run of hard days
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("HOW ARE YOU?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { v -> Chip("$v", lastMood == v, { scope.launch(Dispatchers.IO) { db.meta().put(MetaRow(META_MOOD, (db.meta().get(META_MOOD).orEmpty().lines().filter { it.isNotBlank() }.takeLast(200) + "${System.currentTimeMillis()}|$v").joinToString("\n"))); tick++ } }, "Mood $v of 5") }
            }
            BasicText("1 is the hardest, 5 the best. It is for you, to see patterns. Spark does not judge it.", style = Ts(c.muted, 12.sp))
            if (lowRun) {
                BasicText("The last few days have been hard. A lighter day is one tap in Modify Schedule. You can also talk to someone:", style = Ts(c.text, 14.sp, FontWeight.Bold))
                Btn("Plan a lighter day", { onOpenPlan(Instance(Preset.RECOVERY, today, today), true) }, modifier = Modifier.fillMaxWidth(), description = "Plan a lighter day")
                Btn("Fresh start: start the day from now", { onOpenPlan(Instance(Preset.LATE_START, today, today, mapOf("from_now" to "true")), true) }, modifier = Modifier.fillMaxWidth(), description = "Fresh start from now")
                PEOPLE.forEach { BasicText("${it.name}: ${it.how}", style = Ts(c.muted, 13.sp)) }
            }
        }

        // PK1: a place for ideas that changes nothing
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("PARK AN IDEA")
            BasicText("An idea changes nothing until you turn it into a change.", style = Ts(c.muted, 13.sp))
            TextBox(idea, { idea = it }, "The idea, in a line", "Idea")
            Btn("Park it", { if (idea.isNotBlank()) { save(META_IDEAS, (ideas + idea.trim().replace("\n", " ")).takeLast(100)); idea = "" } }, modifier = Modifier.fillMaxWidth(), description = "Park this idea")
            ideas.takeLast(5).reversed().forEach { i -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BasicText(i, style = Ts(c.text, 14.sp), modifier = Modifier.weight(1f)); Chip("Drop", false, { save(META_IDEAS, ideas - i) }, "Drop the idea $i")
            } }
        }

        // CO7: your own rewards
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("YOUR REWARDS")
            BasicText("Five of your own. One is offered when a block is proven.", style = Ts(c.muted, 13.sp))
            rewards.forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BasicText(r, style = Ts(c.text, 15.sp), modifier = Modifier.weight(1f)); Chip("Remove", false, { save(META_REWARDS, rewards - r) }, "Remove the reward $r")
            } }
            if (rewards.size < 5) { TextBox(reward, { reward = it }, "A reward", "Reward"); Btn("Add", { if (reward.isNotBlank()) { save(META_REWARDS, rewards + reward.trim()); reward = "" } }, modifier = Modifier.fillMaxWidth(), description = "Add this reward") }
            if (rewards.isNotEmpty()) { Btn("Pick one", { picked = rewardPick(rewards, System.currentTimeMillis()) }, modifier = Modifier.fillMaxWidth(), description = "Pick a reward"); picked?.let { BasicText(it, style = Ts(c.text, 18.sp, FontWeight.Bold)) } }
        }

        // SL3: when you cannot sleep
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("CANNOT SLEEP")
            listOf("Get up.", "Low light.", "No phone.", "Back to bed at 20 minutes.").forEach { BasicText(it, style = Ts(c.text, 15.sp)) }
            awake.forEach { a -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BasicText(a, style = Ts(c.text, 15.sp), modifier = Modifier.weight(1f)); Chip("Remove", false, { save(META_AWAKE, awake - a) }, "Remove $a")
            } }
            TextBox(awakeItem, { awakeItem = it }, "Something calm that is yours", "Your own calm thing")
            Btn("Add to the list", { if (awakeItem.isNotBlank()) { save(META_AWAKE, (awake + awakeItem.trim()).takeLast(10)); awakeItem = "" } }, modifier = Modifier.fillMaxWidth(), description = "Add to the cannot-sleep list")
        }
        }
        // CT1, CT2, PK2, SL9: your data survives, and when something does not work you carry on
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var backupMsg by remember { mutableStateOf<String?>(null) }
        var lastBackup by remember { mutableStateOf("") }
        var confirmRestore by remember { mutableStateOf(false) }
        var note by remember { mutableStateOf("") }
        var grey by remember { mutableStateOf(false) }
        LaunchedEffect(tick, backupMsg) { withContext(Dispatchers.IO) { lastBackup = Backup.lastBackupText(ctx); grey = WindDown.available(ctx) } }
        val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            confirmRestore = false
            if (uri != null) scope.launch(Dispatchers.IO) {
                val msg = Backup.restore(ctx, uri); backupMsg = msg
                if (msg.startsWith("Restored")) { kotlinx.coroutines.delay(800); val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName); i?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK); ctx.startActivity(i); Runtime.getRuntime().exit(0) }
            }
        }
        Shelf("Your data and help", "Back up, restore, and say when something does not work") {
        Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("YOUR DATA AND HELP")
            BasicText(lastBackup, style = Ts(c.muted, 13.sp))
            Btn("Back up now", { scope.launch(Dispatchers.IO) { backupMsg = Backup.write(ctx)?.let { "Saved $it in Downloads/Spark." } ?: "The backup could not be written. Nothing else is affected." } }, modifier = Modifier.fillMaxWidth(), description = "Back up now")
            if (!confirmRestore) Btn("Restore from a backup file", { confirmRestore = true }, modifier = Modifier.fillMaxWidth(), description = "Restore from a backup file")
            else { BasicText("This replaces everything on this phone with the file. A safety copy of what is here is kept first.", style = Ts(c.text, 13.sp, FontWeight.Bold)); Btn("Choose the file", { pick.launch(arrayOf("*/*")) }, primary = true, modifier = Modifier.fillMaxWidth(), description = "Choose the backup file") }
            TextBox(note, { note = it }, "What did not work? (optional)", "What did not work")
            Btn("This doesn't work", { scope.launch(Dispatchers.IO) { val saved = Backup.report(ctx, note); if (saved != null) note = ""; backupMsg = if (saved != null) "Saved $saved in Downloads/Spark/reports. Carry on; it will be fixed." else "The report could not be written." } }, modifier = Modifier.fillMaxWidth(), description = "This doesn't work: save a report")
            backupMsg?.let { BasicText(it, style = Ts(c.text, 14.sp, FontWeight.Bold)) }
            BasicText(if (grey) "Greyscale at wind-down is on." else "Greyscale at wind-down needs one command from your PC, once: " + WindDown.COMMAND, style = Ts(c.muted, 12.sp))
        }
        }
        WorkItOutMore(db, onOpenPlan, WioPart.SHELVES)
        WorkItOutExtra(db, onWall, WioPart.SHELVES)
        Spacer(Modifier.height(12.dp))
    }
}
