package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import efa.parseGitConfig
import efa.MAX_GIT_REPOS
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
@Composable
fun SetupScreen(
    p: PlacesState, onHome: () -> Unit, onVenue: (VenueStatus) -> Unit, onBack: () -> Unit, onGit: (GitConfig.Ok) -> Unit = {},
    blocking: BlockingStatus = BlockingStatus(false, null), onOpenAccessibility: () -> Unit = {}, onChooseList: () -> Unit = {},
    notifications: NotificationsStatus = NotificationsStatus(false, false), onOpenNotificationAccess: () -> Unit = {}, onAllowBufferNotice: () -> Unit = {},
    onImport: () -> Unit = {}, sleep: SleepStatus = SleepStatus(), onMorning: () -> Unit = {}, morningText: String = "", onDaysOff: () -> Unit = {}, onLibrary: () -> Unit = {}, onShopping: () -> Unit = {}, onWidgets: () -> Unit = {}, voiceQuietOff: Boolean = true, onVoiceQuiet: (Boolean) -> Unit = {},
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
            Label("DAYS OFF")
            BasicText("Plan a day off or a weekend, or say you are ill.", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("The cost to the Buffer is shown before you confirm. It takes a typed string that gets longer with each use in 30 days.", style = Ts(c.muted, 14.sp))
            Btn("Plan a day off", onDaysOff, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("VOICE")
            BasicText("Voice quick add", style = Ts(c.text, 16.sp, FontWeight.Bold))
            BasicText("Say a deadline, like “assignment due Friday, heavy”. A card shows what was heard before anything is saved. Voice can open a day off request or the block list but cannot finish one. Typed quick add is always there.", style = Ts(c.muted, 14.sp))
            Chip(if (voiceQuietOff) "Voice is off in lectures and the library" else "Voice is on everywhere", voiceQuietOff, { onVoiceQuiet(!voiceQuietOff) }, if (voiceQuietOff) "Turn voice on in lectures and the library" else "Turn voice off in lectures and the library")
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
