package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import efa.GitConfig
import efa.AppearanceMode
import efa.parseGitConfig
import efa.MAX_GIT_REPOS
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.VenueStatus
import efa.Venues
import efa.venuesNearHome

/** What the setup screen shows about places. */
data class PlacesState(val home: Pair<Double, Double>?, val venues: List<VenueStatus>, val git: GitConfig.Ok? = null)

/**
 * Setup for places (L4 to L8). Home is set on a map and saved at about 100 m resolution. Every timetable venue shows where its
 * point came from, and any venue can be placed on the map; a venue OpenStreetMap could not match is flagged first (L5).
 */
/** WT1: what the wake time card shows: the next alarm in words, whether a time was chosen, the time the picker starts at, and the last answer. */
data class WakeUi(val text: String = "", val chosen: Boolean = false, val hour: Int = 10, val minute: Int = 30, val message: String? = null)

@Composable
fun SetupScreen(
    p: PlacesState, onHome: () -> Unit, onVenue: (VenueStatus) -> Unit, onBack: () -> Unit, onGit: (GitConfig.Ok) -> Unit = {},
    blocking: BlockingStatus = BlockingStatus(false, null), onOpenAccessibility: () -> Unit = {}, onChooseList: () -> Unit = {},
    notifications: NotificationsStatus = NotificationsStatus(false, false), onOpenNotificationAccess: () -> Unit = {}, onAllowBufferNotice: () -> Unit = {},
    onImport: () -> Unit = {}, sleep: SleepStatus = SleepStatus(), onMorning: () -> Unit = {}, morningText: String = "", onDaysOff: () -> Unit = {}, onLibrary: () -> Unit = {}, onShopping: () -> Unit = {}, onWidgets: () -> Unit = {}, onPc: () -> Unit = {}, onModify: () -> Unit = {}, healthText: String = "", onHealth: () -> Unit = {}, look: efa.LauncherLook = efa.LauncherLook(), onLook: (Boolean?, Boolean?) -> Unit = { _, _ -> }, appearance: AppearanceMode = AppearanceMode.AUTO, onAppearance: (AppearanceMode) -> Unit = {}, onMyUoB: () -> Unit = {}, myUoBNote: String? = null, pcText: String = "", browsers: List<efa.BrowserCoverage> = emptyList(), voiceQuietOff: Boolean = true, onVoiceQuiet: (Boolean) -> Unit = {},
    codeReminders: Boolean = true, onCodeReminders: (Boolean) -> Unit = {},
    photoReminders: Boolean = true, onPhotoReminders: (Boolean) -> Unit = {},
    mealChecks: Boolean = true, onMealChecks: (Boolean) -> Unit = {},
    quietMode: Boolean = false, onQuietMode: (Boolean) -> Unit = {},
    wake: WakeUi = WakeUi(), onWakeSet: (Int, Int) -> Unit = { _, _ -> }, onWakeClear: () -> Unit = {},
) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText("Setup", style = Ts(c.text, 28.sp, FontWeight.SemiBold))
        BlockingCard(blocking, onOpenAccessibility, onChooseList)
        NotificationsCard(notifications, onOpenNotificationAccess, onAllowBufferNotice)
        SleepCard(sleep, blocking.serviceOn)

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("MODIFY SCHEDULE")
            BasicText("Presets that adapt your schedule", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Assignments planned in anticipation, a catch-up week, a period off, a focus week, a lecture-free lighter period, the Admin library and the essentials.", style = Ts(c.muted, 14.sp))
            Btn("Modify Schedule", onModify, primary = true, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("DAYS OFF")
            BasicText("Plan a day off or a weekend, or say you are ill.", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("The cost to the Buffer is shown before you confirm. It takes a typed string that gets longer with each use in 30 days.", style = Ts(c.muted, 14.sp))
            Btn("Plan a day off", onDaysOff, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("BROWSERS")
            BasicText("Websites of blocked apps", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("In work blocks Spark reads the address bar of a browser it knows and closes the sites of the apps you blocked, private tabs included. A browser it does not know, a web page inside another app, or a mirror site can slip through.", style = Ts(c.muted, 14.sp))
            if (browsers.isEmpty()) BasicText("No browser found on this phone.", style = Ts(c.muted, 14.sp))
            browsers.forEach { b -> BasicText("${b.name}: " + if (b.covered) "covered" else "NOT covered, so its sites are not blocked", style = Ts(c.text, 15.sp, if (b.covered) FontWeight.Normal else FontWeight.Bold)) }
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("TYPE TO OPEN")
            BasicText("App icons and a grid", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Both are off. The home screen stays text only. A change, on or off, starts tomorrow, so it is decided in advance.", style = Ts(c.muted, 14.sp))
            val today = java.time.ZonedDateTime.now(efa.LONDON).toLocalDate(); val e = look.effective(today)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val icons = e.pendingIcons ?: e.icons; val grid = e.pendingGrid ?: e.grid
                Chip(if (icons) "Icons: on" else "Icons: off", icons, { onLook(!icons, null) }, if (icons) "Turn icons off from tomorrow" else "Turn icons on from tomorrow")
                Chip(if (grid) "Grid: on" else "Grid: off", grid, { onLook(null, !grid) }, if (grid) "Turn the grid off from tomorrow" else "Turn the grid on from tomorrow")
            }
            look.note(today)?.let { BasicText("Pending: $it.", style = Ts(c.text, 14.sp, FontWeight.Bold)) }
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("APPEARANCE")
            BasicText("Light, Dark, Night, Colourful or Auto", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Only the colours change. Nothing moves and no rule changes. Night is warm and dim. Auto uses Night from the evening cutoff until the alarm is stopped, otherwise Light by day and Dark in the evening.", style = Ts(c.muted, 14.sp))
            androidx.compose.foundation.layout.Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceMode.entries.forEach { m -> Chip(m.label, m == appearance, { onAppearance(m) }, "Appearance ${m.label}") }
            }
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("WATCH SLEEP, OPTIONAL")
            BasicText("Health Connect", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("If a watch or band writes sleep to Health Connect, Spark shows its night beside the phone's. It never changes what the phone worked out.", style = Ts(c.muted, 14.sp))
            Btn("Allow reading sleep", onHealth, modifier = Modifier.fillMaxWidth())
            if (healthText.isNotEmpty()) BasicText(healthText, style = Ts(c.text, 14.sp, FontWeight.Bold))
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("UNIVERSITY")
            BasicText("See the university's record", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Opens the MyUoB app. Spark cannot read it, and it counts for nothing as proof.", style = Ts(c.muted, 14.sp))
            Btn("Check MyUoB", onMyUoB, modifier = Modifier.fillMaxWidth())
            myUoBNote?.let { BasicText(it, style = Ts(c.text, 14.sp, FontWeight.Bold)) }
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("WINDOWS PC")
            BasicText("Block the same sites on your PC", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText(pcText.ifEmpty { "No PC linked. Install Spark Guard on the PC first." }, style = Ts(c.muted, 14.sp))
            Btn("Link a Windows PC", onPc, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("VOICE")
            BasicText("Voice quick add", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Say a deadline, like “assignment due Friday, heavy”. A card shows what was heard before anything is saved. Voice can open a day off request or the block list but cannot finish one. Typed quick add is always there.", style = Ts(c.muted, 14.sp))
            Chip(if (voiceQuietOff) "Voice is off in lectures and the library" else "Voice is on everywhere", voiceQuietOff, { onVoiceQuiet(!voiceQuietOff) }, if (voiceQuietOff) "Turn voice on in lectures and the library" else "Turn voice off in lectures and the library")
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("WAKE TIME")
            BasicText(wake.text.ifBlank { "Next alarm not worked out yet." }, style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Choose when the next alarm rings. The normal alarm is about 8 hours after you fall asleep, and the latest it can be is 12:00. A later time than the normal one needs the wall. Blocks you sleep through are missed and come back as work.", style = Ts(c.muted, 14.sp))
            var minutes by remember(wake.hour, wake.minute) { androidx.compose.runtime.mutableIntStateOf(wake.hour * 60 + wake.minute) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn("− 15", { minutes = (minutes - 15 + 1440) % 1440 }, description = "Wake time 15 minutes earlier")
                BasicText("%02d:%02d".format(minutes / 60, minutes % 60), style = Ts(c.text, 34.sp, FontWeight.Bold, fontFamily = Mono), modifier = Modifier.weight(1f).semantics { contentDescription = "Wake time %02d:%02d".format(minutes / 60, minutes % 60) })
                Btn("+ 15", { minutes = (minutes + 15) % 1440 }, description = "Wake time 15 minutes later")
            }
            Btn("Set this wake time", { onWakeSet(minutes / 60, minutes % 60) }, primary = true, modifier = Modifier.fillMaxWidth(), description = "Set the wake time to %02d:%02d".format(minutes / 60, minutes % 60))
            if (wake.chosen) Btn("Use the normal alarm", onWakeClear, modifier = Modifier.fillMaxWidth())
            wake.message?.let { BasicText(it, style = Ts(c.text, 14.sp, FontWeight.SemiBold)) }
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("QUIET MODE")
            BasicText("Hold everything except essentials", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("In a work block and at night, every notification is held for the digest, except your essentials, calls, alarms, codes and bank alerts, directions, downloads and the phone's own messages. Turning it off needs the wall.", style = Ts(c.muted, 14.sp))
            Chip(if (quietMode) "Quiet mode: on" else "Quiet mode: off", quietMode, { onQuietMode(!quietMode) }, if (quietMode) "Turn quiet mode off" else "Turn quiet mode on")
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("PHOTO REMINDERS")
            BasicText("When to take a photo", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("A sound when a photo opens and five minutes before it stops counting in full. The home screen always shows the next one.", style = Ts(c.muted, 14.sp))
            Chip(if (photoReminders) "Reminders: on" else "Reminders: off", photoReminders, { onPhotoReminders(!photoReminders) }, if (photoReminders) "Turn the photo reminders off" else "Turn the photo reminders on")
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("MEALS")
            BasicText("Did you eat?", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("A question ten minutes after each meal, with Ate and Not yet. It changes nothing in your plan and costs nothing.", style = Ts(c.muted, 14.sp))
            Chip(if (mealChecks) "Questions: on" else "Questions: off", mealChecks, { onMealChecks(!mealChecks) }, if (mealChecks) "Turn the meal questions off" else "Turn the meal questions on")
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("LECTURE CODE")
            BasicText("Attendance code reminders", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("A reminder at every lecture hour and at each lecture's end, at home or in the room.", style = Ts(c.muted, 14.sp))
            Chip(if (codeReminders) "Reminders: on" else "Reminders: off", codeReminders, { onCodeReminders(!codeReminders) }, if (codeReminders) "Turn the attendance code reminders off" else "Turn the attendance code reminders on")
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("WIDGETS")
            BasicText("Edit widgets", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Add, remove, reorder and resize the text widgets. Adding or enlarging starts at midnight.", style = Ts(c.muted, 14.sp))
            Btn("Edit widgets", onWidgets, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("SHOPPING")
            BasicText("Shopping list", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("The weekly basket and the pantry, with prices you can correct from a receipt.", style = Ts(c.muted, 14.sp))
            Btn("Open the shopping list", onShopping, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("LIBRARY")
            BasicText("Plan a library period", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Work blocks move to the library for 1 to 14 days, with a walk each way. Meals and football stay put.", style = Ts(c.muted, 14.sp))
            Btn("Plan a library period", onLibrary, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("MORNING")
            BasicText(morningText.ifBlank { "Not set up." }, style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("A photo of tomorrow's setup stops the alarm, and social stays blocked until the ready photo.", style = Ts(c.muted, 14.sp))
            Btn("Set up mornings", onMorning, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("TIMETABLE")
            BasicText("Adapt the timetable from a CSV file.", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Lectures that moved, deadlines, assignment weeks and catch-up weeks. Spark checks the file and shows the Buffer before and after, and changes nothing until you say so.", style = Ts(c.muted, 14.sp))
            Btn("Import a timetable file", onImport, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("HOME")
            val h = p.home
            BasicText(if (h == null) "Not set. Lecture attendance outside the building check needs it." else "Set, to about 100 m. No street or house is stored.", style = Ts(c.text, 16.sp))
            if (h != null) {
                val near = venuesNearHome(h, Venues.buildings)
                if (near.isNotEmpty()) BasicText("Within 250 m of home, so these use the building check only: " + near.joinToString { it.alias } + ".", style = Ts(c.muted, 14.sp))
            }
            Btn(if (h == null) "Set home on the map" else "Move home on the map", onHome, primary = h == null, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("LECTURE VENUES")
            BasicText("Points come from OpenStreetMap. Tap a venue to place it on the map yourself. Each placement is logged.", style = Ts(c.muted, 14.sp))
            p.venues.sortedByDescending { it.point == null }.forEach { v ->
                val text = "${v.venueText}: ${if (v.point == null) "NOT FOUND, place it on the map" else v.source}"
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onVenue(v) }.padding(vertical = 8.dp)
                        .semantics { contentDescription = "$text. Tap to place on the map." },
                ) {
                    BasicText(v.venueText, style = Ts(c.text, 16.sp, FontWeight.Medium))
                    BasicText(if (v.point == null) "Not found. Place it on the map." else v.source, style = Ts(c.muted, 14.sp))
                }
            }
        }
        GitCard(p.git, onGit)
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * G1: the GitHub username and public repos whose commits prove code blocks. Public repos only: no token or password is asked for
 * or stored, so a private repo cannot be checked.
 */
@Composable
private fun GitCard(git: GitConfig.Ok?, onGit: (GitConfig.Ok) -> Unit) {
    val c = LocalColors.current
    var user by remember(git) { mutableStateOf(git?.user ?: "") }
    var repos by remember(git) { mutableStateOf(git?.repos?.joinToString("\n") ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("GITHUB FOR CODE BLOCKS")
        BasicText(
            if (git == null) "Not set. Code blocks cannot be verified until it is." else "Set for ${git.user}, ${git.repos.size} repo${if (git.repos.size == 1) "" else "s"}.",
            style = Ts(c.text, 16.sp),
        )
        BasicText("A code block is proved by one of your commits made during the block, pushed to a public repo listed here. No token or password is stored, so private repos cannot be checked.", style = Ts(c.muted, 14.sp))
        Label("USERNAME")
        BasicTextField(user, { user = it; error = null; saved = false }, singleLine = true, textStyle = Ts(c.text, 18.sp), cursorBrush = SolidColor(c.text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().background(c.bg, CardShape).padding(14.dp).semantics { contentDescription = "GitHub username" })
        Label("REPOS, ONE PER LINE AS OWNER/NAME (UP TO $MAX_GIT_REPOS)")
        BasicTextField(repos, { repos = it; error = null; saved = false }, textStyle = Ts(c.text, 18.sp), cursorBrush = SolidColor(c.text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).background(c.bg, CardShape).padding(14.dp).semantics { contentDescription = "GitHub repos" })
        error?.let { BasicText(it, style = Ts(c.text, 16.sp, FontWeight.SemiBold)) }
        if (saved) BasicText("Saved.", style = Ts(c.text, 16.sp, FontWeight.SemiBold))
        Btn("Save GitHub", {
            when (val r = parseGitConfig(user, repos)) {
                is GitConfig.Invalid -> { error = r.message; saved = false }
                is GitConfig.Ok -> { onGit(r); error = null; saved = true }
            }
        }, primary = git == null, modifier = Modifier.fillMaxWidth())
    }
}

/** What the sleep log has so far (layer 2). Record only: nothing here is a rule. */
data class SleepStatus(val nights: Int = 0, val lastDate: String? = null, val lastSleepMin: Int = 0, val lastStart: String = "", val lastEnd: String = "", val lastChecks: Int = 0, val lastGaps: Int = 0, val events: Int = 0, val sinceText: String? = null, val awakeInBedMin: Int? = null)

@Composable
fun SleepCard(s: SleepStatus, serviceOn: Boolean) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("SLEEP LOG")
        if (!serviceOn) BasicText("Off. Logging runs with the blocking service, so turn that on first.", style = Ts(c.text, 16.sp, FontWeight.Bold))
        else if (s.lastDate == null) BasicText("No night recorded yet. A night needs the screen off for an hour.", style = Ts(c.text, 16.sp, FontWeight.Bold))
        else {
            BasicText("Last night: ${s.lastSleepMin / 60}h ${s.lastSleepMin % 60}m asleep, ${s.lastStart} to ${s.lastEnd}.", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText((if (s.lastChecks == 0) "No night checks." else "${s.lastChecks} night check${if (s.lastChecks == 1) "" else "s"}, taken off.") + " Logging gaps: ${s.lastGaps}.", style = Ts(c.muted, 14.sp))
            s.awakeInBedMin?.let { BasicText("Awake in bed before the alarm photo: $it min.", style = Ts(c.muted, 14.sp)) }   // S4
        }
        BasicText("Record only, with no rules attached. It notes the screen, charging, movement and light, never a microphone, and keeps raw events 30 days and a summary line per night.", style = Ts(c.muted, 13.sp))
        if (serviceOn) BasicText("${s.nights} night${if (s.nights == 1) "" else "s"} kept, ${s.events} recent events${s.sinceText?.let { ", logging since $it" } ?: ""}.", style = Ts(c.muted, 13.sp))
    }
}
