package app.efa

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File

/**
 * A map with a fixed cross at the centre. Pan until the cross is on the place, then "Use this point". Nothing reads the phone's
 * location (A8). Tiles come from OpenStreetMap, so the tile server sees which area was viewed; nothing else is sent.
 * Failure behaviour (X7): with no network the tiles stay blank but the cross and the button still work, and the point is still
 * the centre of the view.
 */
@Composable
fun MapPicker(title: String, hint: String, start: Pair<Double, Double>, maxZoom: Double, onPick: (Double, Double) -> Unit, onCancel: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
            tileFileSystemCacheMaxBytes = 20L * 1024 * 1024      // ST4: keep the map cache small
            tileFileSystemCacheTrimBytes = 15L * 1024 * 1024
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 13.0; this.maxZoomLevel = maxZoom
            controller.setZoom(minOf(17.0, maxZoom)); controller.setCenter(GeoPoint(start.first, start.second))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) map.onResume() else if (e == Lifecycle.Event.ON_PAUSE) map.onPause() }
        owner.lifecycle.addObserver(obs); map.onResume()
        onDispose { owner.lifecycle.removeObserver(obs); map.onPause(); map.onDetach() }
    }

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(title, style = TextStyle(c.text, 24.sp, FontWeight.SemiBold))
        BasicText(hint, style = TextStyle(c.muted, 14.sp))
        Box(Modifier.fillMaxWidth().weight(1f).background(c.card, CardShape)) {
            AndroidView({ map }, Modifier.fillMaxSize())
            Canvas(Modifier.size(40.dp).align(Alignment.Center)) {
                val w = 3.dp.toPx(); val m = size.width / 2
                drawLine(c.accent, Offset(m, 0f), Offset(m, size.height), w); drawLine(c.accent, Offset(0f, m), Offset(size.width, m), w)
                drawCircle(c.accent, m * 0.55f, Offset(m, m), style = Stroke(w))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Btn("Use this point", { val p = map.mapCenter; onPick(p.latitude, p.longitude) }, primary = true, modifier = Modifier.weight(1f))
            Btn("Cancel", onCancel, modifier = Modifier.weight(1f))
        }
    }
}
