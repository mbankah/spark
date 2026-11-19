package app.efa

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.WALKTHROUGH
import efa.WalkArt
import efa.WalkPage
import kotlinx.coroutines.launch

/** Who may turn the first-open walkthrough off: only a test build. In the app it is always on until the walkthrough has been seen once (WK1). */
object WalkthroughGate { @Volatile var enforced: Boolean = true }

/**
 * WK: the walkthrough. Nine pages, one idea each, drawn flat in the app's own theme: no gradient, nothing that loops, and no motion at all when Android's animations are off (AN1).
 * It can be skipped at any time and opened again from Setup. [onFinish] says whether you asked for Setup.
 */
@Composable
fun WalkthroughScreen(firstRun: Boolean, onFinish: (openSetup: Boolean) -> Unit, pages: List<WalkPage> = WALKTHROUGH) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { pages.size })
    val page = pager.currentPage
    val last = page == pages.lastIndex
    BackHandler(enabled = page > 0 || !firstRun) { if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) } else onFinish(false) }
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label("${page + 1} of ${pages.size}")
            if (!last) Box(
                Modifier.heightIn(min = 40.dp).clickable(role = Role.Button) { onFinish(false) }.padding(horizontal = 10.dp, vertical = 8.dp).semantics { contentDescription = "Skip the walkthrough" },
                contentAlignment = Alignment.Center,
            ) { BasicText("Skip", style = Ts(c.muted, 15.sp, FontWeight.Medium)) }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { i -> WalkPageView(pages[i], active = pager.currentPage == i) }
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Walkthrough page ${page + 1} of ${pages.size}" },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.indices.forEach { i ->
                val w by animateFloatAsState(if (page == i) 22f else 8f, motion(Motion.STANDARD), label = "dot")
                Box(Modifier.padding(horizontal = 3.dp).size(width = w.dp, height = 8.dp).background(if (page == i) c.accent else c.track, RoundedCornerShape(50)))
            }
        }
        if (last) {
            Btn("Start", { onFinish(false) }, primary = true, modifier = Modifier.fillMaxWidth(), description = "Start using Spark")
            Btn("Open Setup", { onFinish(true) }, modifier = Modifier.fillMaxWidth(), description = "Finish the walkthrough and open Setup")
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page > 0) Btn("Back", { scope.launch { pager.animateScrollToPage(page - 1) } }, modifier = Modifier.weight(1f), description = "Previous page")
            Btn("Next", { scope.launch { pager.animateScrollToPage(page + 1) } }, primary = true, modifier = Modifier.weight(if (page > 0) 2f else 1f), description = "Next page")
        }
    }
}

@Composable
private fun WalkPageView(p: WalkPage, active: Boolean) {
    val c = LocalColors.current
    val progress by animateFloatAsState(if (active) 1f else 0f, motion(Motion.FILL), label = "page")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(210.dp).card().padding(16.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) { WalkArtView(p.art, progress) }
        Label(p.label)
        BasicText(p.title, style = Ts(c.text, 28.sp, FontWeight.SemiBold, lineHeight = 33.sp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            p.lines.forEach { BasicText(it, style = Ts(c.text, 16.sp, lineHeight = 23.sp)) }
        }
    }
}

// ---------------------------------------------------------------- the pictures: flat, drawn from the theme, each moves once

@Composable
private fun WalkArtView(art: WalkArt, p: Float) {
    when (art) {
        WalkArt.SPARK -> ArtSpark(p)
        WalkArt.BUFFER -> ArtBuffer(p)
        WalkArt.PHONE -> ArtPhone(p)
        WalkArt.PROOF -> ArtProof(p)
        WalkArt.LIFE -> ArtLife(p)
        WalkArt.WALL -> ArtWall(p)
        WalkArt.SLEEP -> ArtSleep(p)
        WalkArt.YOURS -> ArtYours(p)
        WalkArt.START -> ArtStart(p)
    }
}

@Composable
private fun ArtSpark(p: Float) {
    val c = LocalColors.current
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val mid = Offset(size.width / 2, size.height / 2)
            listOf(0.24f, 0.36f, 0.47f).forEachIndexed { i, f ->
                val r = size.minDimension * f * (0.6f + 0.4f * p)
                drawCircle(c.track.copy(alpha = (1f - i * 0.28f) * p), r, mid, style = Stroke(2.dp.toPx()))
            }
        }
        SparkLogo(96.dp)
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float, color: Color) {
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label(label); BasicText(value, style = Ts(c.text, 13.sp, FontWeight.Medium, Mono)) }
        Box(Modifier.fillMaxWidth().height(14.dp).background(c.track, RoundedCornerShape(50))) { Box(Modifier.fillMaxWidth(fraction.coerceIn(0.001f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(50))) }
    }
}

@Composable
private fun ArtBuffer(p: Float) {
    val c = LocalColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        BarRow("HOURS YOU HAVE", "9.0h", 0.9f * p, c.accent)
        BarRow("WORK STILL TO DO", "5.6h", 0.56f * p, c.muted)
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.track))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label("BUFFER")
            BasicText("+${"%.1f".format(3.4f * p)}h", style = Ts(c.accentText, 30.sp, FontWeight.Bold, Mono))
        }
    }
}

@Composable
private fun ArtPhone(p: Float) {
    val c = LocalColors.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 104.dp, height = 170.dp).border(3.dp, c.muted, RoundedCornerShape(18.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until 3) {
                        val open = row == 2   // the bottom row is the never-blocked row: phone, maps, bank
                        val tone = if (open) c.accent else lerp(c.accent, c.track, p)
                        Box(Modifier.size(17.dp).background(tone, CircleShape))
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.size(10.dp).background(c.track, CircleShape)); Label("CLOSED IN A BLOCK") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.size(10.dp).background(c.accent, CircleShape)); Label("NEVER BLOCKED") }
            BasicText("Calls · Maps · Bank", style = Ts(c.text, 13.sp, FontWeight.Medium))
        }
    }
}

private fun DrawScope.bracket(origin: Offset, dx: Float, dy: Float, len: Float, color: Color, w: Float) {
    drawLine(color, origin, Offset(origin.x + dx * len, origin.y), w, StrokeCap.Round); drawLine(color, origin, Offset(origin.x, origin.y + dy * len), w, StrokeCap.Round)
}

@Composable
private fun ArtProof(p: Float) {
    val c = LocalColors.current
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val left = w * 0.22f; val right = w * 0.78f; val top = h * 0.08f; val bottom = h * 0.92f; val sw = 3.dp.toPx(); val len = 26.dp.toPx()
        bracket(Offset(left, top), 1f, 1f, len, c.muted, sw); bracket(Offset(right, top), -1f, 1f, len, c.muted, sw)
        bracket(Offset(left, bottom), 1f, -1f, len, c.muted, sw); bracket(Offset(right, bottom), -1f, -1f, len, c.muted, sw)
        // the page of notes: a few lines
        listOf(0.26f, 0.38f, 0.50f, 0.62f).forEachIndexed { i, y -> drawLine(c.text.copy(alpha = 0.55f), Offset(left + 22.dp.toPx(), h * y), Offset(right - 22.dp.toPx() - (i % 2) * 36.dp.toPx(), h * y), 4.dp.toPx(), StrokeCap.Round) }
        // the tick arrives
        val r = 26.dp.toPx() * p; val mid = Offset(right - 6.dp.toPx(), bottom - 6.dp.toPx())
        if (p > 0.02f) {
            drawCircle(c.accent, r, mid)
            val k = r / 26.dp.toPx()
            drawLine(c.onAccent, Offset(mid.x - 11.dp.toPx() * k, mid.y), Offset(mid.x - 3.dp.toPx() * k, mid.y + 8.dp.toPx() * k), 4.dp.toPx(), StrokeCap.Round)
            drawLine(c.onAccent, Offset(mid.x - 3.dp.toPx() * k, mid.y + 8.dp.toPx() * k), Offset(mid.x + 11.dp.toPx() * k, mid.y - 8.dp.toPx() * k), 4.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun ArtLife(p: Float) {
    val c = LocalColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val unit = maxWidth / 9f
        // a day as blocks; an event arrives and free time gives way first
        val blocks = listOf(Triple(1.3f, c.muted, "routine"), Triple(1.7f, c.accent, "work"), Triple(1.0f, c.accent, "work"), Triple(1.6f * (1f - 0.75f * p) + 0.02f, Color.Transparent, "free"), Triple(1.5f, c.accent, "work"))
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                blocks.forEachIndexed { i, (wt, col, kind) ->
                    if (i == 3) Box(Modifier.size(width = unit * 1.2f * p + 0.1.dp, height = 46.dp).background(c.text, RoundedCornerShape(8.dp)))
                    Box(Modifier.size(width = unit * wt, height = 46.dp).then(if (kind == "free") Modifier.border(2.dp, c.track, RoundedCornerShape(8.dp)) else Modifier.background(col, RoundedCornerShape(8.dp))))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(10.dp).background(c.text, RoundedCornerShape(3.dp))); Label("SOMETHING CAME UP") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(10.dp).border(2.dp, c.track, RoundedCornerShape(3.dp))); Label("FREE GIVES WAY") }
            }
        }
    }
}

@Composable
private fun ArtWall(p: Float) {
    val c = LocalColors.current
    val sample = "k7Qm-2xRt-9Lw-ZpA4-vN8c-3HdY-5sFe-uB6j"
    val shown = (sample.length * p).toInt().coerceIn(0, sample.length)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Label("TYPE THIS BY HAND")
        Box(Modifier.fillMaxWidth().background(c.bg, RoundedCornerShape(10.dp)).border(2.dp, c.track, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 14.dp)) {
            BasicText(sample.take(shown) + if (shown < sample.length) "▌" else "", style = Ts(c.text, 17.sp, FontWeight.Medium, Mono, letterSpacing = 1.sp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("CHARACTERS"); BasicText("${(75 * p).toInt()} of 75", style = Ts(c.text, 13.sp, FontWeight.Medium, Mono)) }
            Box(Modifier.fillMaxWidth().height(8.dp).background(c.track, RoundedCornerShape(50))) { Box(Modifier.fillMaxWidth(p.coerceIn(0.001f, 1f)).fillMaxHeight().background(c.accent, RoundedCornerShape(50))) }
        }
    }
}

@Composable
private fun ArtSleep(p: Float) {
    val c = LocalColors.current
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val centre = Offset(w * 0.5f, h * 0.44f + (1f - p) * 30.dp.toPx()); val r = h * 0.30f
        drawCircle(c.accent, r, centre)
        drawCircle(c.card, r * 0.86f, Offset(centre.x + r * 0.52f, centre.y - r * 0.18f))
        listOf(0.16f to 0.20f, 0.84f to 0.28f, 0.74f to 0.66f, 0.26f to 0.62f, 0.62f to 0.14f).forEach { (x, y) -> drawCircle(c.muted.copy(alpha = p), 3.dp.toPx(), Offset(w * x, h * y)) }
        // the night as a bar: wind-down, sleep, wake
        val by = h * 0.90f; val bh = 10.dp.toPx()
        drawRoundRect(c.track, Offset(w * 0.08f, by), Size(w * 0.84f, bh), CornerRadius(bh / 2))
        drawRoundRect(c.accent, Offset(w * 0.08f, by), Size(w * (0.12f + 0.52f * p), bh), CornerRadius(bh / 2))
    }
}

@Composable
private fun ArtYours(p: Float) {
    val c = LocalColors.current
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val cx = w / 2; val top = h * 0.06f; val bottom = h * 0.94f; val half = h * 0.34f
        val shield = Path().apply {
            moveTo(cx, top); cubicTo(cx + half * 0.5f, top + 10.dp.toPx(), cx + half, top + 18.dp.toPx(), cx + half, top + 44.dp.toPx())
            cubicTo(cx + half, h * 0.62f, cx + half * 0.55f, h * 0.80f, cx, bottom)
            cubicTo(cx - half * 0.55f, h * 0.80f, cx - half, h * 0.62f, cx - half, top + 44.dp.toPx())
            cubicTo(cx - half, top + 18.dp.toPx(), cx - half * 0.5f, top + 10.dp.toPx(), cx, top); close()
        }
        drawPath(shield, c.accent.copy(alpha = 0.18f + 0.82f * p)); drawPath(shield, c.accent, style = Stroke(4.dp.toPx()))
        val k = p; val m = Offset(cx, h * 0.46f)
        drawLine(c.onAccent.copy(alpha = k), Offset(m.x - 20.dp.toPx(), m.y), Offset(m.x - 6.dp.toPx(), m.y + 14.dp.toPx()), 6.dp.toPx(), StrokeCap.Round)
        drawLine(c.onAccent.copy(alpha = k), Offset(m.x - 6.dp.toPx(), m.y + 14.dp.toPx()), Offset(m.x + 22.dp.toPx(), m.y - 14.dp.toPx()), 6.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun ArtStart(p: Float) {
    val c = LocalColors.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        listOf("TODAY", "SCHEDULE", "WORK IT OUT").forEachIndexed { i, name ->
            val on = i == 0
            Box(
                Modifier.size(width = 62.dp, height = 120.dp).background(if (on) c.accent.copy(alpha = 0.18f + 0.82f * p) else c.bg, RoundedCornerShape(12.dp)).border(2.dp, if (on) c.accent else c.track, RoundedCornerShape(12.dp)).padding(6.dp),
                contentAlignment = Alignment.BottomCenter,
            ) { BasicText(name, style = Ts(if (on) c.onAccent else c.muted, 9.sp, FontWeight.Medium, Mono), maxLines = 2) }
        }
        Canvas(Modifier.size(46.dp)) {
            val mid = Offset(size.width / 2, size.height / 2); val r = size.minDimension * 0.28f
            drawCircle(c.muted, r, mid, style = Stroke(5.dp.toPx()))
            for (a in 0 until 8) { val ang = Math.toRadians(a * 45.0 + 90.0 * p); val s = Offset(mid.x + (r + 2.dp.toPx()) * Math.cos(ang).toFloat(), mid.y + (r + 2.dp.toPx()) * Math.sin(ang).toFloat()); val e = Offset(mid.x + (r + 9.dp.toPx()) * Math.cos(ang).toFloat(), mid.y + (r + 9.dp.toPx()) * Math.sin(ang).toFloat()); drawLine(c.muted, s, e, 9.dp.toPx(), StrokeCap.Butt) }
        }
    }
}
