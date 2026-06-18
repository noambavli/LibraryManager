package com.mh.librarymanager.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hebrew on-screen keyboard. Always rendered LTR so the physical keyboard
 * positions of standard Israeli layouts are preserved, regardless of the
 * surrounding RTL app direction.
 *
 * No system IME is ever invoked; every key dispatches a [KeyAction] which the
 * caller (typically the view-model) applies to the currently focused field.
 */
@Composable
fun HebrewKeyboard(
    onKey: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val gap = if (maxWidth < 520.dp) 6.dp else 8.dp
                val keyTextSize = when {
                    maxHeight < 240.dp -> 16.sp
                    maxHeight < 340.dp -> 18.sp
                    maxWidth < 500.dp -> 19.sp
                    else -> 21.sp
                }
                val actionTextSize = when {
                    maxHeight < 240.dp -> 13.sp
                    maxHeight < 340.dp -> 14.sp
                    else -> 15.sp
                }
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    KeyRow(Modifier.fillMaxWidth().weight(1f)) {
                        NUMBERS_ROW.forEach { CharKey(it, onKey, keyTextSize) }
                    }
                    KeyRow(Modifier.fillMaxWidth().weight(1f)) {
                        HEB_TOP_ROW.forEach { CharKey(it, onKey, keyTextSize) }
                    }
                    KeyRow(Modifier.fillMaxWidth().weight(1f)) {
                        HEB_MIDDLE_ROW.forEach { CharKey(it, onKey, keyTextSize) }
                    }
                    KeyRow(Modifier.fillMaxWidth().weight(1f)) {
                        HEB_BOTTOM_ROW.forEach { CharKey(it, onKey, keyTextSize) }
                    }
                    KeyRow(Modifier.fillMaxWidth().weight(1f)) {
                        SYMBOL_ROW.forEach { CharKey(it, onKey, keyTextSize) }
                    }
                    KeyRow(Modifier.fillMaxWidth().weight(1f)) {
                        ActionKey(
                            label = "ניקוי הכל",
                            weight = 1.6f,
                            textSize = actionTextSize,
                            onClick = { onKey(KeyAction.ClearAll) },
                            accent = false,
                        )
                        ActionKey(
                            label = "ניקוי שדה",
                            weight = 1.4f,
                            textSize = actionTextSize,
                            onClick = { onKey(KeyAction.ClearField) },
                            accent = false,
                        )
                        ActionKey(
                            label = "רווח",
                            weight = 4f,
                            textSize = actionTextSize,
                            onClick = { onKey(KeyAction.Insert(" ")) },
                            accent = true,
                        )
                        ActionKey(
                            label = "⌫",
                            weight = 1.6f,
                            textSize = keyTextSize,
                            onClick = { onKey(KeyAction.Backspace) },
                            accent = false,
                        )
                    }
                }
            }
        }
    }
}

private val NUMBERS_ROW = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val HEB_TOP_ROW = listOf("ק", "ר", "א", "ט", "ו", "ן", "ם", "פ", "׳", "״")
private val HEB_MIDDLE_ROW = listOf("ש", "ד", "ג", "כ", "ע", "י", "ח", "ל", "ך", "ף")
private val HEB_BOTTOM_ROW = listOf("ז", "ס", "ב", "ה", "נ", "מ", "צ", "ת", "ץ")
private val SYMBOL_ROW = listOf("(", ")", "-", ".", ",", "/", ":")

@Composable
private fun KeyRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun RowScope.CharKey(
    label: String,
    onKey: (KeyAction) -> Unit,
    textSize: androidx.compose.ui.unit.TextUnit,
) {
    KeyButton(
        label = label,
        weight = 1f,
        textSize = textSize,
        onClick = { onKey(KeyAction.Insert(label)) },
        accent = false,
    )
}

@Composable
private fun RowScope.ActionKey(
    label: String,
    weight: Float,
    textSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
    accent: Boolean,
) {
    KeyButton(label = label, weight = weight, textSize = textSize, onClick = onClick, accent = accent)
}

@Composable
private fun RowScope.KeyButton(
    label: String,
    weight: Float,
    textSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
    accent: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (accent) cs.primary else cs.surface
    val fg = if (accent) cs.onPrimary else cs.onSurface
    val outline = if (accent) Color.Transparent else cs.outlineVariant

    Surface(
        modifier = Modifier
            .weight(weight)
            .fillMaxSize(),
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (accent) 0.dp else 0.dp,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, outline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = textSize,
                fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}
