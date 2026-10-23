package app.efa

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * The two faces of the industrial look (AP1, D-10): DM Sans for titles and numbers, DM Mono for labels. Both are bundled in the
 * app (SIL Open Font Licence, see LICENSES/), so the launcher needs no network (LA4). DM Sans is one variable font, set to each weight.
 */
val Sans = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal, FontStyle.Normal, variationSettings = FontVariation.Settings(FontWeight.Normal, FontStyle.Normal)),
    Font(R.font.dm_sans, FontWeight.Medium, FontStyle.Normal, variationSettings = FontVariation.Settings(FontWeight.Medium, FontStyle.Normal)),
    Font(R.font.dm_sans, FontWeight.SemiBold, FontStyle.Normal, variationSettings = FontVariation.Settings(FontWeight.SemiBold, FontStyle.Normal)),
    Font(R.font.dm_sans, FontWeight.Bold, FontStyle.Normal, variationSettings = FontVariation.Settings(FontWeight.Bold, FontStyle.Normal)),
)

val Mono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

/** A text style in the app's faces. Every piece of text goes through this, so nothing falls back to the system font. */
fun Ts(
    color: Color, size: TextUnit, weight: FontWeight? = null, fontFamily: FontFamily = Sans,
    letterSpacing: TextUnit = TextUnit.Unspecified, lineHeight: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = fontFamily, letterSpacing = letterSpacing, lineHeight = lineHeight)
