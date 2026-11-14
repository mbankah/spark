package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.ModifierRow
import efa.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

/** Modify Schedule: what is running, and the presets to start. */
@Composable
fun ModifyHomeScreen(active: List<ModifierRow>, today: LocalDate, onPick: (Preset) -> Unit, onRemove: (ModifierRow) -> Unit, onAdmin: () -> Unit, onEssentials: () -> Unit, message: String?, onBack: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("MODIFY SCHEDULE")
        BasicText("Modify Schedule", style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText("Pick a preset. It adapts your schedule, and you see what it does and what it costs before anything changes.", style = Ts(c.muted, 14.sp))
        message?.let { Column(Modifier.fillMaxWidth().card().padding(16.dp)) { BasicText(it, style = Ts(c.text, 15.sp, FontWeight.Bold)) } }

        Label("ACTIVE AND PLANNED")
        if (active.isEmpty()) BasicText("Nothing yet.", style = Ts(c.muted, 14.sp))
        active.forEach { r ->
            val runningNow = !LocalDate.parse(r.startDate).isAfter(today) && !LocalDate.parse(r.endDate).isBefore(today)
            Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(Preset.of(r.preset)?.label ?: r.preset, style = Ts(c.text, 17.sp, FontWeight.Bold))
                BasicText("${DAY.format(LocalDate.parse(r.startDate))} to ${DAY.format(LocalDate.parse(r.endDate))}${if (runningNow) ", running now" else ", planned"}", style = Ts(c.muted, 14.sp))
                Btn("End and remove", { onRemove(r) }, description = "Remove the ${Preset.of(r.preset)?.label} that runs ${r.startDate} to ${r.endDate}")
            }
        }

        Label("PRESETS")
        Preset.entries.forEach { p -> Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(p.label, style = Ts(c.text, 17.sp, FontWeight.Bold)); BasicText(p.blurb, style = Ts(c.muted, 14.sp))
            Btn("Set up ${p.label}", { onPick(p) }, description = "Set up ${p.label}")
        } }

        Label("ALSO")
        Btn("Admin library: upkeep jobs", onAdmin, modifier = Modifier.fillMaxWidth())
        Btn("Essentials: apps never blocked", onEssentials, modifier = Modifier.fillMaxWidth())
        BasicText("Not built yet: Revision, Routine, Recovery, Away, Guests, Placement, Fresh start, Custom and screen-time limits.", style = Ts(c.muted, 13.sp))
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

private fun bufText(b: BufferState) = when (b) { is BufferState.Value -> b.result.label() + (b.result.deadlineLabel?.let { " to $it" } ?: ""); BufferState.NoDeadline -> "No deadline"; else -> "Not available" }

/** One setting, drawn from its definition. */
@Composable
fun SettingRow(def: SettingDef, value: String, today: LocalDate, onChange: (String) -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(def.label, style = Ts(c.text, 15.sp, FontWeight.Bold))
        if (def.help.isNotEmpty()) BasicText(def.help, style = Ts(c.muted, 12.sp))
        when (def.kind) {
            SettingKind.INT -> {
                val v = value.toIntOrNull() ?: def.default.toInt(); val step = if ((def.max ?: 10.0) > 60) 5 else 1
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Chip("-", false, { onChange(((v - step).coerceAtLeast((def.min ?: Int.MIN_VALUE.toDouble()).toInt())).toString()) }, "Less ${def.label}")
                    BasicText("$v", style = Ts(c.text, 18.sp, FontWeight.Bold))
                    Chip("+", false, { onChange(((v + step).coerceAtMost((def.max ?: Int.MAX_VALUE.toDouble()).toInt())).toString()) }, "More ${def.label}")
                }
            }
            SettingKind.DECIMAL -> {
                val v = value.toDoubleOrNull() ?: def.default.toDouble(); val step = if ((def.max ?: 10.0) <= 1.0) 0.05 else 0.5
                fun fmt(d: Double) = String.format(java.util.Locale.UK, if (step < 0.1) "%.2f" else "%.1f", d)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Chip("-", false, { onChange(fmt((v - step).coerceAtLeast(def.min ?: 0.0))) }, "Less ${def.label}")
                    BasicText(fmt(v), style = Ts(c.text, 18.sp, FontWeight.Bold))
                    Chip("+", false, { onChange(fmt((v + step).coerceAtMost(def.max ?: 1e9))) }, "More ${def.label}")
                }
            }
            SettingKind.CHOICE -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                def.choices.forEach { ch -> Chip(ch, ch == value, { onChange(ch) }, "${def.label}: $ch") }
            }
            SettingKind.BOOL -> Chip(if (value == "true") "On" else "Off", value == "true", { onChange(if (value == "true") "false" else "true") }, "${def.label}: ${if (value == "true") "on" else "off"}")
            SettingKind.TIME -> {
                val t = runCatching { LocalTime.parse(value) }.getOrElse { LocalTime.parse(def.default) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Chip("-30m", false, { onChange(t.minusMinutes(30).toString()) }, "Earlier ${def.label}")
                    BasicText(t.toString(), style = Ts(c.text, 18.sp, FontWeight.Bold))
                    Chip("+30m", false, { onChange(t.plusMinutes(30).toString()) }, "Later ${def.label}")
                }
            }
            SettingKind.TEXT -> BasicTextField(value, { onChange(it) }, singleLine = false, textStyle = Ts(c.text, 16.sp), cursorBrush = SolidColor(c.text),
                modifier = Modifier.fillMaxWidth().card().padding(12.dp).semantics { contentDescription = def.label })
            SettingKind.DATE -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..120).map { today.plusDays(it.toLong()) }.forEach { d -> Chip(DAY.format(d), d.toString() == value, { onChange(d.toString()) }, "${def.label} ${DAY.format(d)}") }
            }
        }
    }
}

/** A preset's form: dates, Recommended, settings, items, then a preview and Apply. */
@Composable
fun ModifyFormScreen(
    draft: Instance, today: LocalDate, preview: ModPreview?, recommended: Map<String, Rec>, message: String?,
    onChange: (Instance) -> Unit, onRecommend: () -> Unit, onPreview: () -> Unit, onApply: () -> Unit, onBack: () -> Unit,
) {
    val c = LocalColors.current
    val preset = draft.preset
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("MODIFY SCHEDULE")
        BasicText(preset.label, style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText(preset.blurb, style = Ts(c.muted, 14.sp))
        message?.let { Column(Modifier.fillMaxWidth().card().padding(16.dp)) { BasicText(it, style = Ts(c.text, 15.sp, FontWeight.Bold)) } }

        if (preset != Preset.ASSIGNMENT) {
            Label("DATES")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..60).map { today.plusDays(it.toLong()) }.forEach { d -> Chip(if (d == today) "Today" else DAY.format(d), d == draft.start, { onChange(draft.copy(start = d, end = d.plusDays((draft.days - 1).toLong()))) }, "Starts ${DAY.format(d)}") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 5, 7, 10, 14).forEach { n -> Chip(if (n == 1) "1 day" else "$n days", n == draft.days, { onChange(draft.copy(end = draft.start.plusDays((n - 1).toLong()))) }, "For $n days") }
            }
            BasicText("${DAY.format(draft.start)} to ${DAY.format(draft.end)}", style = Ts(c.text, 15.sp, FontWeight.Bold))
        }

        Btn("Recommended", onRecommend, modifier = Modifier.fillMaxWidth(), description = "Recommended. Fills in the settings from your own plan and times, with a reason for each.")
        if (recommended.isNotEmpty()) Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("RECOMMENDED BECAUSE")
            recommended.forEach { (k, r) -> BasicText("${k.replace('_', ' ')}: ${r.value}. ${r.reason}", style = Ts(c.text, 13.sp)) }
        }

        val defs = ModDefs.settings(preset)
        if (defs.isNotEmpty()) Label("SETTINGS")
        defs.forEach { d -> SettingRow(d, draft.settings[d.key] ?: d.default, today) { v -> onChange(draft.copy(settings = draft.settings + (d.key to v))) } }

        for (itemDef in ModDefs.items(preset)) {
            Label(itemDef.label.uppercase() + "S")
            draft.items.forEachIndexed { n, item ->
                Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText("${itemDef.label} ${n + 1}", style = Ts(c.text, 17.sp, FontWeight.Bold))
                    itemDef.settings.forEach { d -> SettingRow(d, item[d.key] ?: d.default, today) { v -> onChange(draft.copy(items = draft.items.mapIndexed { i, m -> if (i == n) m + (d.key to v) else m })) } }
                    Btn("Remove ${itemDef.label.lowercase()} ${n + 1}", { onChange(draft.copy(items = draft.items.filterIndexed { i, _ -> i != n })) }, description = "Remove ${itemDef.label} ${n + 1}")
                }
            }
            if (draft.items.size < itemDef.max) Btn("Add ${itemDef.label.lowercase()}", { onChange(draft.copy(items = draft.items + mapOf("title" to ""))) }, modifier = Modifier.fillMaxWidth(), description = "Add ${itemDef.label}")
        }

        Btn("Preview what it does", onPreview, primary = true, modifier = Modifier.fillMaxWidth())
        preview?.let { p ->
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("PREVIEW")
                if (p.problems.isNotEmpty()) { BasicText("It cannot be applied:", style = Ts(c.text, 15.sp, FontWeight.Bold)); p.problems.forEach { BasicText(it, style = Ts(c.text, 14.sp)) } }
                else {
                    p.compiled.lines.forEach { BasicText(it, style = Ts(c.text, 14.sp)) }
                    p.compiled.problems.forEach { BasicText(it, style = Ts(c.text, 14.sp, FontWeight.Bold)) }
                    BasicText("Buffer now: ${bufText(p.before)}", style = Ts(c.muted, 13.sp)); BasicText("Buffer with this: ${bufText(p.after)}", style = Ts(c.muted, 13.sp, FontWeight.Bold))
                    if (p.wallNeeded) { BasicText("This loosens something, so it needs the typed wall:", style = Ts(c.text, 14.sp, FontWeight.Bold)); p.compiled.loosening.forEach { BasicText(it, style = Ts(c.text, 13.sp)) } }
                    Btn(if (p.wallNeeded) "Apply, with the wall" else "Apply", onApply, primary = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

private fun freqText(f: Freq): String = when (f) {
    Freq.Daily -> "every day"; Freq.Weekdays -> "weekdays"; is Freq.Weekly -> "weekly, ${f.day.name.take(3).lowercase()}"; is Freq.TimesPerWeek -> "${f.n} times a week"
    is Freq.EveryNDays -> "every ${f.n} days"; is Freq.Fortnightly -> "fortnightly, ${f.day.name.take(3).lowercase()}"; is Freq.Monthly -> "monthly, day ${f.dayOfMonth}"; Freq.AsNeeded -> "when needed"
}

/** The Admin library: a load set, each job on or off, the daily cap, Lighter and the switch for trimming work (which needs the wall). */
@Composable
fun AdminScreen(state: AdminState, problems: List<String>, onLoadSet: (LoadSet) -> Unit, onToggle: (AdminJob) -> Unit, onCap: (Int) -> Unit, onLighter: () -> Unit, onTrim: () -> Unit, onBack: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Label("ADMIN LIBRARY")
        BasicText("Upkeep jobs", style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText("The bed, the kitchen, the bathroom, laundry, bins, the shop, money, post and more. Pick a load and they are placed in free time, spread over the week. Nothing is placed until you pick one. Admin never changes the Buffer.", style = Ts(c.muted, 14.sp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadSet.entries.forEach { l -> Chip(l.label, state.loadSet == l, { onLoadSet(l) }, "Load: ${l.label}") }
        }
        BasicText(if (state.loadSet == null) "Not set up: nothing is placed." else "Load: ${state.loadSet?.label}.", style = Ts(c.text, 15.sp, FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Chip("-15", false, { onCap(state.capMin - 15) }, "Lower the daily cap"); BasicText("${state.capMin} minutes a day at most", style = Ts(c.text, 15.sp, FontWeight.Bold)); Chip("+15", false, { onCap(state.capMin + 15) }, "Raise the daily cap")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (state.lighter) "Lighter: on" else "Lighter: off", state.lighter, onLighter, "Lighter uses each job's shortest length")
            Chip(if (state.trimWork) "May trim work: on" else "May trim work: off", state.trimWork, onTrim, "May trim work. Needs the typed wall to turn on.")
        }
        if (problems.isNotEmpty()) Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Label("COULD NOT BE PLACED")
            problems.take(12).forEach { BasicText(it, style = Ts(c.text, 13.sp)) }
            if (problems.size > 12) BasicText("And ${problems.size - 12} more. A fuller week needs a higher cap, Lighter, or trimming work.", style = Ts(c.muted, 12.sp))
        }
        Area.entries.forEach { area ->
            Label(area.label.uppercase())
            AdminLibrary.jobs.filter { it.area == area }.forEach { job ->
                Column(Modifier.fillMaxWidth().card().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(job.title, style = Ts(c.text, 15.sp, FontWeight.Bold))
                    BasicText("${freqText(job.freq)}, ${state.minutesFor(job)} min${if (job.set == JobSet.TRIGGERED) ", set off by an event (not placed by itself)" else ""}", style = Ts(c.muted, 12.sp))
                    if (job.set != JobSet.TRIGGERED) Chip(if (state.enabled(job)) "On" else "Off", state.enabled(job), { onToggle(job) }, "${job.title}: ${if (state.enabled(job)) "on" else "off"}")
                }
            }
        }
        BasicText("Not built yet: editing a job's frequency, window or checklist, adding your own job, and triggered jobs.", style = Ts(c.muted, 12.sp))
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}
