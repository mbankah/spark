package efa

import kotlin.math.pow

/** WCAG relative luminance and contrast ratio for #rrggbb. */
fun luminance(hex: String): Double {
    val c = hex.removePrefix("#").chunked(2).map { it.toInt(16) / 255.0 }
        .map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
}

fun contrast(a: String, b: String): Double {
    val la = luminance(a); val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

data class Tokens(val bg: String, val card: String, val text: String, val muted: String, val accent: String, val track: String, val onAccent: String)

/** Mode and palette token sets, copied from the design folder's .dc.html files and the build prompt table. Checked by the AP8 test. */
object Palettes {
    val neutralLight = Tokens("#e9e7e2", "#f6f5f1", "#1b1a18", "#5d5b56", "#ff5a1f", "#cfccc5", "#1b1a18")
    val neutralDark = Tokens("#121211", "#1c1c1a", "#ece9e2", "#a09d95", "#ff6a33", "#34332f", "#121211")
    val coldLight = Tokens("#e3e9ef", "#f3f6f9", "#14202b", "#4f5f6e", "#1f6feb", "#c3ced9", "#ffffff")
    val coldDark = Tokens("#0c1218", "#151d26", "#e4edf5", "#93a3b3", "#3ec6e0", "#28333e", "#0c1218")
    val night = Tokens("#0a0604", "#130c08", "#d9945f", "#b9824f", "#c4501f", "#2a1810", "#d9945f")
    val all = mapOf("neutralLight" to neutralLight, "neutralDark" to neutralDark, "coldLight" to coldLight, "coldDark" to coldDark, "night" to night)
}
