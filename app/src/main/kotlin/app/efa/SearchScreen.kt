package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.matchApps

/**
 * Type to open (U6, LA5): the keyboard opens at once, a few letters find an app, and results update on every key. There is no list to
 * browse and no recent list, which would be a grid by another name. A blocked app is shown as plain muted text with the reason (U7);
 * opening it ends at the blocked card like every other route (LA8).
 * [blockedReason] returns the reason an app is blocked right now, or null.
 */
@Composable
fun SearchScreen(apps: List<AppInfo>, blockedReason: (String) -> String?, onOpen: (AppInfo) -> Unit, onBack: () -> Unit, icons: Boolean = false, grid: Boolean = false, iconFor: (String) -> androidx.compose.ui.graphics.ImageBitmap? = { null }) {
    val c = LocalColors.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val labels = remember(apps) { apps.map { it.label } }
    // U6: with the grid off there is nothing to browse; with it on (U9), an empty box shows every app as a grid.
    val hits = remember(query, apps, grid) { if (query.isBlank() && grid) apps else matchApps(query, labels).map { apps[it] } }

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).imePadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label("Type to open")
        BasicTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            textStyle = Ts(c.text, 22.sp, FontWeight.Medium), cursorBrush = SolidColor(c.text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().card().padding(16.dp).focusRequester(focus).semantics { contentDescription = "Type the name of an app" },
        )
        if (grid) androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(3), Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            gridItems(hits, key = { it.pkg }) { a -> AppTile(a, blockedReason(a.pkg), icons, iconFor, onOpen, compact = true) }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits, key = { it.pkg }) { a -> AppTile(a, blockedReason(a.pkg), icons, iconFor, onOpen, compact = false) }
        }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AppTile(a: AppInfo, reason: String?, icons: Boolean, iconFor: (String) -> androidx.compose.ui.graphics.ImageBitmap?, onOpen: (AppInfo) -> Unit, compact: Boolean) {
    val c = LocalColors.current
    val image = if (icons) remember(a.pkg) { iconFor(a.pkg) } else null
    val body: @Composable () -> Unit = {
        if (image != null) androidx.compose.foundation.Image(image, null, Modifier.size(if (compact) 40.dp else 32.dp))      // decorative: the label is the name
        Column {
            BasicText(a.label, style = Ts(if (reason == null) c.text else c.muted, if (compact) 14.sp else 18.sp, FontWeight.Medium), maxLines = 2)
            if (reason != null) BasicText("Blocked: $reason", style = Ts(c.muted, 12.sp))
        }
    }
    val mod = Modifier.fillMaxWidth().card().clip(CardShape).clickable(role = Role.Button) { onOpen(a) }.padding(horizontal = 12.dp, vertical = 12.dp)
        .semantics { contentDescription = if (reason == null) "${a.label}. Opens the app." else "${a.label}. Blocked: $reason." }
    if (compact) Column(mod, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { body() }
    else Row(mod, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { body() }
}
