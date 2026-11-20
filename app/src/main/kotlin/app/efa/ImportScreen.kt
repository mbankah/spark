package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.ImportLogRow
import efa.ImportIssue
import efa.ImportLimits
import efa.Verification
import efa.summaryLines

/** Where the import screen is (IM5). Nothing is applied until [Checked] is confirmed. */
sealed interface ImportUi {
    data object Idle : ImportUi
    data class Checked(val name: String, val verification: Verification, val fileHash: String, val text: String = "", val mode: efa.ImportMode = efa.ImportMode.ADD) : ImportUi
    data class Done(val lines: List<String>) : ImportUi
    data class Failed(val message: String) : ImportUi
}

/**
 * Import a timetable file (IM1 to IM12): choose a file, see every problem with its line and column or a preview with the Buffer before
 * and after, then apply. A file that loosens something sends you to the typed wall first ([onApply] decides). Earlier imports are listed
 * and can be removed, which also needs the wall.
 */
@Composable
fun ImportScreen(
    ui: ImportUi, imports: List<ImportLogRow>,
    onChoose: () -> Unit, onCopyFormat: () -> Unit, onApply: () -> Unit, onRemove: (ImportLogRow) -> Unit, onBack: () -> Unit,
    onMode: (efa.ImportMode) -> Unit = {}, meter: String? = null, economy: String? = null,
) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("TIMETABLE")
        BasicText("Import a timetable file", style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText("A CSV file from an AI, your university or you. Spark checks every line first and changes nothing until you press Apply.", style = Ts(c.muted, 14.sp))
        Btn("Choose a file", onChoose, primary = true, modifier = Modifier.fillMaxWidth())
        Btn("Copy the format for an AI", onCopyFormat, modifier = Modifier.fillMaxWidth(), description = "Copy the format for an AI. Paste it into an AI app with your timetable.")

        when (ui) {
            ImportUi.Idle -> {}
            is ImportUi.Failed -> Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("NOT IMPORTED")
                BasicText(ui.message, style = Ts(c.text, 16.sp, FontWeight.Bold))
                BasicText("Nothing was changed.", style = Ts(c.muted, 14.sp))
            }
            is ImportUi.Done -> Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("DONE")
                ui.lines.forEach { BasicText(it, style = Ts(c.text, 15.sp)) }
            }
            is ImportUi.Checked -> Checked(ui, onApply, onMode, meter, economy)
        }

        if (imports.isNotEmpty()) {
            Label("EARLIER IMPORTS")
            imports.forEach { i ->
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(i.name, style = Ts(c.text, 16.sp, FontWeight.Bold))
                    BasicText(i.summary, style = Ts(c.muted, 13.sp))
                    if (System.currentTimeMillis() - i.atMs < 24L * 3_600_000L) BasicText("Within 24 hours: removing it is free and gives the count back.", style = Ts(c.muted, 12.sp))
                    Btn("Remove", { onRemove(i) }, description = "Remove the import ${i.name}. Needs the typed wall.")
                }
            }
        }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Checked(ui: ImportUi.Checked, onApply: () -> Unit, onMode: (efa.ImportMode) -> Unit, meter: String?, economy: String?) {
    val c = LocalColors.current
    val v = ui.verification
    // IX2: the mode is chosen here, never by the file
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("HOW TO USE THIS FILE")
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            efa.ImportMode.entries.forEach { m -> Chip(m.label, ui.mode == m, { onMode(m) }, m.label) }
        }
        BasicText(if (ui.mode == efa.ImportMode.ADD) "The file's events go on top of your plan." else "The file becomes the plan for its days. Lectures, their study cycles, meals, sleep, football, church, paid sessions and protected free time stay.", style = Ts(c.muted, 13.sp))
        meter?.let { BasicText(it, style = Ts(c.muted, 12.sp)) }
        economy?.let { BasicText(it, style = Ts(c.text, 13.sp, FontWeight.Bold)) }
    }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("FILE")
        BasicText(ui.name, style = Ts(c.text, 16.sp, FontWeight.Bold))
        if (!v.ok) {
            BasicText("${v.errors.size} problem${if (v.errors.size == 1) "" else "s"}. Nothing was changed. Fix the file and choose it again.", style = Ts(c.text, 15.sp, FontWeight.Bold))
            v.errors.take(ImportLimits.MAX_ISSUES_SHOWN).forEach { Issue(it) }
            if (v.errors.size > ImportLimits.MAX_ISSUES_SHOWN) BasicText("And ${v.errors.size - ImportLimits.MAX_ISSUES_SHOWN} more.", style = Ts(c.muted, 13.sp))
        } else {
            // IM13: the preview is short at the top and the long lists fold, so the Apply button is always on the first screen
            BasicText("No problems found.", style = Ts(c.text, 15.sp, FontWeight.Bold))
            if (v.needsWall) BasicText("${v.loosening.size} thing${if (v.loosening.size == 1) "" else "s"} loosen, so applying needs the typed wall.", style = Ts(c.muted, 14.sp))
        }
    }
    if (v.ok && !v.changesNothing) Btn(if (v.needsWall) "Apply, with the wall" else "Apply", onApply, primary = true, modifier = Modifier.fillMaxWidth())
    if (v.ok) {
        val lines = v.summaryLines()
        if (lines.isNotEmpty()) Fold("WHAT IT DOES", lines.size, startOpen = lines.size <= 6) { lines.forEach { BasicText(it, style = Ts(c.text, 15.sp), modifier = Modifier.semantics { contentDescription = it }) } }
        if (v.needsWall) Fold("THIS FILE LOOSENS", v.loosening.size) { v.loosening.forEach { BasicText(it, style = Ts(c.text, 14.sp)) } }
        if (v.warnings.isNotEmpty()) Fold("WARNINGS", v.warnings.size) { v.warnings.forEach { Issue(it) } }
    }
}

/** IM13: a card whose long list folds. The header says how many lines are inside and whether it is open; tapping it opens or folds it. */
@Composable
fun Fold(title: String, count: Int, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    var open by remember { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open }
                .semantics { contentDescription = "$title, $count line${if (count == 1) "" else "s"}. ${if (open) "Open. Tap to fold." else "Folded. Tap to open."}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(title)
            Spacer(Modifier.weight(1f))
            BasicText(if (open) "Fold" else "Show $count", style = Ts(c.muted, 14.sp, FontWeight.Bold))
        }
        if (open) content()
    }
}

@Composable
private fun Issue(i: ImportIssue) {
    BasicText(i.text(), style = Ts(LocalColors.current.text, 14.sp), modifier = Modifier.semantics { contentDescription = i.text() })
}

/** The typed wall for a file that loosens, or for removing an import (IM6, IM12). */
@Composable
fun ImportWallGate(title: String, why: String, onPassed: () -> Unit, onCancel: () -> Unit, length: Int = efa.WallLength.IMPORT_LOOSEN, details: List<String> = emptyList()) {
    val c = LocalColors.current
    val target = androidx.compose.runtime.remember { efa.newWallString(length) }
    // IM13: the screen scrolls and the words are short, so the field can always be reached, with the keyboard open or not. The long list folds below it.
    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(title, style = Ts(c.text, 28.sp, FontWeight.SemiBold))
        BasicText(why, style = Ts(c.text, 16.sp))
        Column(Modifier.fillMaxWidth().card().padding(16.dp)) { WallField(target, onPassed) }
        Btn("Cancel", onCancel, modifier = Modifier.fillMaxWidth())
        if (details.isNotEmpty()) Fold("WHAT IT LOOSENS", details.size) { details.forEach { BasicText(it, style = Ts(c.text, 14.sp)) } }
    }
}
