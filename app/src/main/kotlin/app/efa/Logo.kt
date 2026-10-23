package app.efa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Spark mark: a flame with a smaller flame cut out of it. Inside the app it is drawn flat in the current theme's accent, so
 * it changes with Light, Dark and Night like every other element. The ledger allows no gradient in the interface except the dial
 * ring, so the gradient version lives only in the app icon and brand files (tools/gen-logo.py). No animation.
 * Same geometry as brand/spark-icon.svg, in a 512 unit square.
 */
@Composable
fun SparkLogo(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Canvas(modifier.size(size).semantics { contentDescription = "Spark" }) {
        val u = this.size.width / 512f
        val flame = Path().apply {
            fillType = PathFillType.EvenOdd
            moveTo(256f * u, 52f * u)
            cubicTo(268f * u, 140f * u, 388f * u, 186f * u, 388f * u, 322f * u)
            cubicTo(388f * u, 404f * u, 330f * u, 462f * u, 256f * u, 462f * u)
            cubicTo(182f * u, 462f * u, 124f * u, 404f * u, 124f * u, 322f * u)
            cubicTo(124f * u, 262f * u, 152f * u, 222f * u, 186f * u, 186f * u)
            cubicTo(190f * u, 224f * u, 206f * u, 244f * u, 228f * u, 250f * u)
            cubicTo(214f * u, 176f * u, 232f * u, 108f * u, 256f * u, 52f * u)
            close()
            moveTo(256f * u, 262f * u)
            cubicTo(262f * u, 312f * u, 320f * u, 332f * u, 320f * u, 388f * u)
            cubicTo(320f * u, 426f * u, 292f * u, 450f * u, 256f * u, 450f * u)
            cubicTo(220f * u, 450f * u, 192f * u, 426f * u, 192f * u, 388f * u)
            cubicTo(192f * u, 346f * u, 230f * u, 330f * u, 256f * u, 262f * u)
            close()
        }
        drawPath(flame, c.accent)
    }
}
