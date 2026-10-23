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
) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText("Setup", style = TextStyle(c.text, 28.sp, FontWeight.SemiBold))
        BlockingCard(blocking, onOpenAccessibility, onChooseList)

        Column(Modifier.fillMaxWidth().background(c.card, CardShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("HOME")
            val h = p.home
            BasicText(if (h == null) "Not set. Lecture attendance outside the building check needs it." else "Set, to about 100 m. No street or house is stored.", style = TextStyle(c.text, 16.sp))
            if (h != null) {
                val near = venuesNearHome(h, Venues.buildings)
                if (near.isNotEmpty()) BasicText("Within 250 m of home, so these use the building check only: " + near.joinToString { it.alias } + ".", style = TextStyle(c.muted, 14.sp))
            }
            Btn(if (h == null) "Set home on the map" else "Move home on the map", onHome, primary = h == null, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.fillMaxWidth().background(c.card, CardShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("LECTURE VENUES")
            BasicText("Points come from OpenStreetMap. Tap a venue to place it on the map yourself. Each placement is logged.", style = TextStyle(c.muted, 14.sp))
            p.venues.sortedByDescending { it.point == null }.forEach { v ->
                val text = "${v.venueText}: ${if (v.point == null) "NOT FOUND, place it on the map" else v.source}"
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onVenue(v) }.padding(vertical = 8.dp)
                        .semantics { contentDescription = "$text. Tap to place on the map." },
                ) {
                    BasicText(v.venueText, style = TextStyle(c.text, 16.sp, FontWeight.Medium))
                    BasicText(if (v.point == null) "Not found. Place it on the map." else v.source, style = TextStyle(c.muted, 14.sp))
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
    Column(Modifier.fillMaxWidth().background(c.card, CardShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("GITHUB FOR CODE BLOCKS")
        BasicText(
            if (git == null) "Not set. Code blocks cannot be verified until it is." else "Set for ${git.user}, ${git.repos.size} repo${if (git.repos.size == 1) "" else "s"}.",
            style = TextStyle(c.text, 16.sp),
        )
        BasicText("A code block is proved by one of your commits made during the block, pushed to a public repo listed here. No token or password is stored, so private repos cannot be checked.", style = TextStyle(c.muted, 14.sp))
        Label("USERNAME")
        BasicTextField(user, { user = it; error = null; saved = false }, singleLine = true, textStyle = TextStyle(c.text, 18.sp), cursorBrush = SolidColor(c.text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().background(c.bg, CardShape).padding(14.dp).semantics { contentDescription = "GitHub username" })
        Label("REPOS, ONE PER LINE AS OWNER/NAME (UP TO $MAX_GIT_REPOS)")
        BasicTextField(repos, { repos = it; error = null; saved = false }, textStyle = TextStyle(c.text, 18.sp), cursorBrush = SolidColor(c.text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).background(c.bg, CardShape).padding(14.dp).semantics { contentDescription = "GitHub repos" })
        error?.let { BasicText(it, style = TextStyle(c.text, 16.sp, FontWeight.SemiBold)) }
        if (saved) BasicText("Saved.", style = TextStyle(c.text, 16.sp, FontWeight.SemiBold))
        Btn("Save GitHub", {
            when (val r = parseGitConfig(user, repos)) {
                is GitConfig.Invalid -> { error = r.message; saved = false }
                is GitConfig.Ok -> { onGit(r); error = null; saved = true }
            }
        }, primary = git == null, modifier = Modifier.fillMaxWidth())
    }
}
