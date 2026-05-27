package com.mh.librarymanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.domain.CustomColor

/**
 * Visual style for a book-color chip. Calculated once per (name, palette)
 * pair so both the search results and the management cards render identically.
 */
data class ColorChipStyle(
    val background: Color,
    val foreground: Color,
    val border: BorderStroke? = null,
)

/**
 * Built-in catalog colors. The Hebrew labels here match the bundled xlsx
 * exactly (after [colorLabelKey] folding), and the foreground was chosen so
 * white-on-dark / dark-on-light stays AA-readable.
 */
private val BuiltInPalette: Map<String, ColorChipStyle> = mapOf(
    "כחול" to ColorChipStyle(Color(0xFF1976D2), Color.White),
    "אדום" to ColorChipStyle(Color(0xFFE53935), Color.White),
    "ירוק" to ColorChipStyle(Color(0xFF2E7D32), Color.White),
    "צהוב" to ColorChipStyle(Color(0xFFF9A825), Color(0xFF3E2723)),
    "סגול" to ColorChipStyle(Color(0xFF7B1FA2), Color.White),
    "חום" to ColorChipStyle(Color(0xFF795548), Color.White),
    "אפור" to ColorChipStyle(Color(0xFF757575), Color.White),
    "ורוד" to ColorChipStyle(Color(0xFFE91E63), Color.White),
    "כתום" to ColorChipStyle(Color(0xFFFF9800), Color(0xFF3E2723)),
    // "לבן" is rendered specially against the card surface so we don't bake
    // a background colour here — see [resolveBookColorStyle].
)

/** Hebrew labels in canonical display order. */
val BuiltInColorNames: List<String> = BuiltInPalette.keys.toList() + "לבן"

/**
 * Exact catalog color label — do not use [com.mh.librarymanager.search.HebrewText.normalize]
 * because it folds final letters (ן→נ) and would break "לבן" → "לבנ".
 */
fun colorLabelKey(raw: String): String {
    val out = StringBuilder()
    for (ch in raw.trim()) {
        if (ch.code in 0x0591..0x05C7) continue
        if (ch.code in 0x200E..0x200F) continue
        out.append(ch)
    }
    return out.toString()
}

/**
 * Resolves the chip style for a stored book.color string against the built-in
 * palette first, then any user-defined entries, finally falling back to a
 * neutral surface tone supplied by the caller.
 */
fun resolveBookColorStyle(
    colorName: String,
    customColors: List<CustomColor>,
    cardSurface: Color,
    fallbackBackground: Color,
    fallbackForeground: Color,
): ColorChipStyle {
    val key = colorLabelKey(colorName)
    if (key.isEmpty()) {
        return ColorChipStyle(fallbackBackground, fallbackForeground)
    }
    BuiltInPalette[key]?.let { return it }
    if (key == "לבן") {
        return ColorChipStyle(
            background = cardSurface,
            foreground = Color(0xFF424242),
            border = BorderStroke(1.5.dp, Color(0xFF757575)),
        )
    }
    customColors.firstOrNull { colorLabelKey(it.name) == key }?.let { custom ->
        val bg = colorFromArgbLong(custom.argb)
        return ColorChipStyle(bg, contrastingForeground(bg))
    }
    return ColorChipStyle(fallbackBackground, fallbackForeground)
}

fun colorFromArgbLong(argb: Long): Color = Color(
    red = ((argb shr 16) and 0xFF).toInt(),
    green = ((argb shr 8) and 0xFF).toInt(),
    blue = (argb and 0xFF).toInt(),
    alpha = ((argb shr 24) and 0xFF).toInt(),
)

fun Color.toArgbLong(): Long {
    val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
    val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
    val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
    val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/** Returns white or near-black depending on what reads better on [background]. */
fun contrastingForeground(background: Color): Color {
    // sRGB relative luminance — accurate enough for chip text contrast.
    val r = background.red
    val g = background.green
    val b = background.blue
    val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return if (lum > 0.55f) Color(0xFF1B1B1B) else Color.White
}
