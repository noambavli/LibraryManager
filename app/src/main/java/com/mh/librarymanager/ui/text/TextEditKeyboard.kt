package com.mh.librarymanager.ui.text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mh.librarymanager.ui.search.KeyAction

/**
 * Detects Java/Android format placeholders (`%1$s`, `%2$d`, `%d`, …) in a
 * string, in first-seen order without duplicates. The text editor surfaces
 * these as one-tap chips so management inserts them verbatim rather than typing
 * `%`, `$` and digits by hand and risking a broken template.
 */
private val PLACEHOLDER_REGEX = Regex("%(\\d+\\$)?[a-zA-Z]")

fun placeholderTokens(text: String): List<String> {
    val seen = LinkedHashSet<String>()
    PLACEHOLDER_REGEX.findAll(text).forEach { seen.add(it.value) }
    return seen.toList()
}

private enum class EditKbMode { HEBREW, ENGLISH, SYMBOLS }

private val DIGITS_ROW = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

private val HEB_ROWS = listOf(
    listOf("ק", "ר", "א", "ט", "ו", "ן", "ם", "פ", "׳", "״"),
    listOf("ש", "ד", "ג", "כ", "ע", "י", "ח", "ל", "ך", "ף"),
    listOf("ז", "ס", "ב", "ה", "נ", "מ", "צ", "ת", "ץ"),
)

private val ENG_ROWS = listOf(
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("z", "x", "c", "v", "b", "n", "m"),
)

private val SYMBOL_ROWS = listOf(
    listOf("%", "$", "#", "@", "&", "*", "+", "=", "~", "^"),
    listOf("\"", "'", "״", "׳", "!", "?", ";", ":", "•", "…"),
    listOf("(", ")", "[", "]", "{", "}", "<", ">", "|", "\\"),
    listOf("-", "_", ".", ",", "/", "·", "־", "–", "@", "№"),
)

/**
 * Full on-screen keyboard for the text-management editor. Unlike the search
 * keyboard it also offers English letters, symbols (`%`, `$`, quotes…), a shift
 * key, and an explicit new-line key — everything needed to reproduce any app
 * string. No system IME is ever involved; every key emits a [KeyAction].
 */
@Composable
fun TextEditKeyboard(
    onKey: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(EditKbMode.HEBREW) }
    var shift by remember { mutableStateOf(false) }

    // The keyboard is laid out LTR so key positions stay stable regardless of
    // the surrounding RTL direction, matching the search keyboard.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val letterRows = when (mode) {
                    EditKbMode.HEBREW -> HEB_ROWS
                    EditKbMode.ENGLISH -> ENG_ROWS
                    EditKbMode.SYMBOLS -> SYMBOL_ROWS
                }

                KeyRow(Modifier.fillMaxWidth().weight(1f)) {
                    DIGITS_ROW.forEach { CharKey(it, onKey) }
                }
                letterRows.forEach { row ->
                    KeyRow(Modifier.fillMaxWidth().weight(1f)) {
                        if (mode == EditKbMode.ENGLISH && shift) {
                            row.forEach { CharKey(it.uppercase(), onKey) }
                        } else {
                            row.forEach { CharKey(it, onKey) }
                        }
                    }
                }

                // Action row: mode switching, shift (English only), space,
                // new line and backspace.
                KeyRow(Modifier.fillMaxWidth().weight(1f)) {
                    ActionKey(
                        label = when (mode) {
                            EditKbMode.HEBREW -> "ABC"
                            EditKbMode.ENGLISH -> "אבג"
                            EditKbMode.SYMBOLS -> "אבג"
                        },
                        weight = 1.4f,
                        onClick = {
                            mode = when (mode) {
                                EditKbMode.HEBREW -> EditKbMode.ENGLISH
                                EditKbMode.ENGLISH -> EditKbMode.HEBREW
                                EditKbMode.SYMBOLS -> EditKbMode.HEBREW
                            }
                        },
                    )
                    ActionKey(
                        label = if (mode == EditKbMode.SYMBOLS) "123" else "#+=",
                        weight = 1.2f,
                        onClick = {
                            mode = if (mode == EditKbMode.SYMBOLS) EditKbMode.HEBREW else EditKbMode.SYMBOLS
                        },
                    )
                    if (mode == EditKbMode.ENGLISH) {
                        ActionKey(
                            label = if (shift) "⇧" else "⇧",
                            weight = 1.1f,
                            onClick = { shift = !shift },
                            accent = shift,
                        )
                    }
                    ActionKey(
                        label = "רווח",
                        weight = 3.2f,
                        onClick = { onKey(KeyAction.Insert(" ")) },
                        accent = true,
                    )
                    ActionKey(
                        label = "↵",
                        weight = 1.1f,
                        onClick = { onKey(KeyAction.Insert("\n")) },
                    )
                    ActionKey(
                        label = "⌫",
                        weight = 1.3f,
                        onClick = { onKey(KeyAction.Backspace) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.CharKey(label: String, onKey: (KeyAction) -> Unit) {
    KeyButton(label = label, weight = 1f, textSize = 19.sp, onClick = { onKey(KeyAction.Insert(label)) })
}

@Composable
private fun RowScope.ActionKey(
    label: String,
    weight: Float,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    KeyButton(label = label, weight = weight, textSize = 15.sp, onClick = onClick, accent = accent)
}

@Composable
private fun RowScope.KeyButton(
    label: String,
    weight: Float,
    textSize: TextUnit,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (accent) cs.primary else cs.surface
    val fg = if (accent) cs.onPrimary else cs.onSurface
    val outline = if (accent) Color.Transparent else cs.outlineVariant

    Surface(
        modifier = Modifier.weight(weight).fillMaxSize(),
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, outline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
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
