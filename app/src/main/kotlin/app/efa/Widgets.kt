package app.efa

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.*
import efa.*
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Builds what the widgets say from the blocking snapshot and the database (WG2, WG9). Returns null when there is nothing to build from. */
suspend fun buildWidgetData(ctx: Context, now: ZonedDateTime = BlockingState.now()): WidgetData? {
    val s = BlockingState.snapshot ?: return null
    return try {
        val db = EfaDb.get(ctx)
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        val nn = nowAndNext(s.pattern, now)
        val buffer = bufferNow(now, studyCycles(s.pattern, now) + s.tasks, s.pattern, shortSleep = s.shortSleep)
        val upcoming = (0L..1L).flatMap { slotsFor(s.pattern, now.toLocalDate().plusDays(it), now.zone) }.filter { it.end.isAfter(now) && it.slot.type != "sleep" }.sortedBy { it.start }
        val firstUse = db.meta().get(META_FIRST_USE)?.toLongOrNull() ?: now.toInstant().toEpochMilli()
        val money = RewardsRepo(db).view(s.pattern, firstUse, now)
        val left = ShoppingRepo(db).items().count { !it.pantry && !it.ticked }
        val week = weekGlance(s.pattern, now.toLocalDate(), now.zone)
        WidgetData(buffer, nn.now?.let { "${it.slot.title}, until ${hm.format(it.end)}" }, upcoming.take(4).map { "${hm.format(it.start)} ${it.slot.title}" },
            if (money.live) money.week.money.freeToSpendPence else null, null, left, week, now)
    } catch (e: Exception) { null }          // X7: nothing is better than a wrong number
}

/** WG2, B2: the built-in widgets as Android widgets for a stock launcher. Plain text, no badge. */
abstract class SparkWidget(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                val data = kotlinx.coroutines.runBlocking { buildWidgetData(context) }
                val now = BlockingState.now()
                for (id in ids) manager.updateAppWidget(id, views(context, kind, WidgetSize.TWO_BY_TWO, data, now))
            } catch (e: Exception) { /* a widget that cannot update keeps what it showed, and its time tells you how old that is */ }
            finally { pending.finish() }
        }.start()
    }

    companion object {
        fun views(ctx: Context, kind: WidgetKind, size: WidgetSize, data: WidgetData?, now: ZonedDateTime): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_text)
            val lines = widgetLines(kind, size, data, now)
            val ids = listOf(R.id.w0, R.id.w1, R.id.w2, R.id.w3, R.id.w4, R.id.w5, R.id.w6, R.id.w7)
            ids.forEachIndexed { i, id -> if (i < lines.size) { rv.setTextViewText(id, lines[i]); rv.setViewVisibility(id, View.VISIBLE) } else rv.setViewVisibility(id, View.GONE) }
            val action = when (widgetTarget(kind)) {
                WidgetTarget.QUICK_ADD -> BufferNotifier.ACTION_QUICK_ADD
                else -> "widget_" + widgetTarget(kind).name.lowercase()
            }
            rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, kind.ordinal + 10, Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(BufferNotifier.EXTRA_ACTION, action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return rv
        }

        /** Called every minute by the blocking service, so a widget is never more than a minute old while the service lives (WG9). */
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            for (cls in listOf(BufferWidget::class.java, NowNextWidget::class.java, FreeToSpendWidget::class.java, DayPlanWidget::class.java, ShoppingWidget::class.java, QuickAddWidget::class.java, WeekGlanceWidget::class.java)) {
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
                if (ids.isNotEmpty()) ctx.sendBroadcast(Intent(ctx, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
            }
        }
    }
}

class BufferWidget : SparkWidget(WidgetKind.BUFFER)
class NowNextWidget : SparkWidget(WidgetKind.NOW_NEXT)
class FreeToSpendWidget : SparkWidget(WidgetKind.FREE_TO_SPEND)
class DayPlanWidget : SparkWidget(WidgetKind.DAY_PLAN)
class ShoppingWidget : SparkWidget(WidgetKind.SHOPPING)
class QuickAddWidget : SparkWidget(WidgetKind.QUICK_ADD)
class WeekGlanceWidget : SparkWidget(WidgetKind.WEEK_GLANCE)

/** What the Edit widgets screen shows for one widget (WG3, WG4). */
data class WidgetRowUi(val kind: WidgetKind, val size: WidgetSize, val note: String?, val first: Boolean, val last: Boolean)

/** WG3: add, remove, reorder and resize. Anything that adds or enlarges is marked with the day it starts (WG4). */
@Composable
fun EditWidgetsScreen(
    placed: List<WidgetRowUi>, available: List<WidgetKind>, message: String?,
    onAdd: (WidgetKind) -> Unit, onRemove: (WidgetKind) -> Unit, onMove: (WidgetKind, Int) -> Unit, onSize: (WidgetKind, WidgetSize) -> Unit, onBack: () -> Unit,
) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Label("WIDGETS")
        BasicText("Edit widgets", style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText("Adding or enlarging a widget starts at the next midnight. Removing or shrinking one happens at once. The Buffer always stays.", style = Ts(c.muted, 14.sp))
        message?.let { Column(Modifier.fillMaxWidth().card().padding(16.dp)) { BasicText(it, style = Ts(c.text, 15.sp, FontWeight.Bold)) } }
        placed.forEach { r ->
            Column(Modifier.fillMaxWidth().card().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("${r.kind.label}, ${r.size.label}", style = Ts(c.text, 16.sp, FontWeight.Bold))
                r.note?.let { BasicText(it, style = Ts(c.muted, 13.sp)) }
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!r.first) Chip("Up", false, { onMove(r.kind, -1) }, "Move ${r.kind.label} up")
                    if (!r.last) Chip("Down", false, { onMove(r.kind, 1) }, "Move ${r.kind.label} down")
                    if (r.kind.removable) Chip("Remove", false, { onRemove(r.kind) }, "Remove ${r.kind.label}")
                }
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetSize.entries.forEach { sz -> Chip(sz.label, sz == r.size, { onSize(r.kind, sz) }, "Make ${r.kind.label} ${sz.label}") }
                }
            }
        }
        if (available.isNotEmpty()) {
            Label("ADD")
            available.forEach { k -> Btn("Add ${k.label}", { onAdd(k) }, modifier = Modifier.fillMaxWidth()) }
        }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

/** Reads and saves the placements (WG1, WG3). The first look fills in the default four. */
class WidgetRepo(private val db: EfaDb) {
    suspend fun placements(today: LocalDate): List<Placement> {
        if (db.widgets().all().isEmpty()) save(defaultPlacements(today))
        return db.widgets().all().map { Placement(WidgetKind.valueOf(it.kind), WidgetSize.valueOf(it.size), it.position, LocalDate.parse(it.activeFrom), it.nextSize?.let(WidgetSize::valueOf), it.nextFrom?.let(LocalDate::parse)) }
    }
    suspend fun save(list: List<Placement>) {
        db.widgets().clear()
        list.forEach { db.widgets().insert(WidgetRow(it.kind.name, it.size.name, it.position, it.activeFrom.toString(), it.nextSize?.name, it.nextFrom?.toString())) }
    }
    suspend fun rows(today: LocalDate): List<WidgetRowUi> {
        val l = placements(today).sortedBy { it.position }
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM")
        return l.mapIndexed { i, p ->
            val s = shownOn(p, today)
            WidgetRowUi(p.kind, s.size, when {
                !s.shown -> "Starts ${fmt.format(s.pendingFrom!!)}"
                s.pendingSize != null -> "Becomes ${s.pendingSize!!.label} on ${fmt.format(s.pendingFrom!!)}"
                else -> null
            }, i == 0, i == l.lastIndex)
        }
    }
}
