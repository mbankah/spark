package app.efa

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import efa.Palettes
import efa.Tokens

/** AP2: Light, Dark, Night, Colourful, plus Auto. Mode changes colours only; layout is shared, so nothing moves (AP7). */
enum class Mode { AUTO, LIGHT, DARK, NIGHT }

private fun hex(s: String) = Color(s.removePrefix("#").toLong(16) or 0xFF000000)

class Colors(t: Tokens) {
    val bg = hex(t.bg); val card = hex(t.card); val text = hex(t.text); val muted = hex(t.muted)
    val accent = hex(t.accent); val accentText = hex(t.accentText); val track = hex(t.track); val onAccent = hex(t.onAccent)
    private val dark = efa.luminance(t.bg) < 0.2
    /** The solid edge under a card, and the light line along its top (the drawings' inset highlight). Both are flat. */
    val edge = if (dark) Color.Black else track
    val hi = if (dark) Color(0x0FFFFFFF) else Color(0xB3FFFFFF)
}

val LocalColors = staticCompositionLocalOf { Colors(Palettes.coldLight) }

/** Cold is the default palette (Michael, 23 Oct 2026). Auto follows the system. */
fun tokensFor(mode: Mode, systemDark: Boolean): Tokens = when (mode) {
    Mode.LIGHT -> Palettes.coldLight
    Mode.DARK -> Palettes.coldDark
    Mode.NIGHT -> Palettes.night
    Mode.AUTO -> if (systemDark) Palettes.coldDark else Palettes.coldLight
}

@Composable
fun EfaTheme(mode: Mode = Mode.AUTO, content: @Composable () -> Unit) {
    val colors = Colors(tokensFor(mode, isSystemInDarkTheme()))
    CompositionLocalProvider(LocalColors provides colors, content = content)
}
