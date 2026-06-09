package com.mh.librarymanager.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A search input field driven entirely by the in-app keyboard.
 *
 * - Native cursor and selection still work (tap to position, long-press to
 *   select), because we keep [BasicTextField] underneath.
 * - Text edits from the system IME are ignored; tap/drag still moves the cursor.
 * - A no-op text toolbar avoids the copy/paste bar opening the system keyboard.
 * - When the field becomes the active field, it grabs focus visually and lets
 *   the parent know via [onFocus], so subsequent keyboard taps target it.
 */
@Composable
fun KeyboardEditField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isActive: Boolean,
    onFocus: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp? = null,
) {
    val cs = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val borderColor by animateColorAsState(
        targetValue = if (isActive) cs.primary else cs.outlineVariant,
        animationSpec = tween(140),
        label = "borderColor",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isActive) cs.surface else cs.surface,
        animationSpec = tween(140),
        label = "containerColor",
    )

    LaunchedEffect(isActive) {
        if (isActive) {
            focusRequester.requestFocus()
            // The platform IME session is already swallowed app-wide by
            // NoSystemKeyboard; this single hide is a cheap safety net.
            keyboardController?.hide()
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides NoOpTextToolbar) {
    val baseHeight = if (compact) 42.dp else 52.dp
    val fieldHeight = minHeight ?: baseHeight
    val labelStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    val inputStyle = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    val clearSize = if (compact) 30.dp else 36.dp
    val lineHeightPx = with(density) {
        val lh = inputStyle.lineHeight
        if (lh != TextUnit.Unspecified) lh.roundToPx()
        else (inputStyle.fontSize.value * density.fontScale * 1.4f).roundToInt()
    }

    if (!singleLine) {
        LaunchedEffect(value.text, value.selection.start) {
            val cursor = value.selection.start.coerceIn(0, value.text.length)
            val linesBeforeCursor = value.text.substring(0, cursor).count { it == '\n' }
            val target = (linesBeforeCursor * lineHeightPx).coerceAtMost(scrollState.maxValue)
            scrollState.scrollTo(target)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = labelStyle,
            color = if (isActive) cs.primary else cs.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                start = 4.dp,
                end = 4.dp,
                bottom = if (compact) 2.dp else 4.dp,
            ),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(fieldHeight),
            color = containerColor,
            shape = RoundedCornerShape(if (compact) 8.dp else 10.dp),
            border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        ) {
            Row(
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = if (compact) 8.dp else 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (singleLine) Modifier else Modifier.fillMaxHeight())
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.text.isEmpty() && !isActive) {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { newValue ->
                            if (newValue.text == value.text) {
                                onValueChange(newValue)
                            } else {
                                keyboardController?.hide()
                                onValueChange(
                                    TextFieldValue(
                                        text = value.text,
                                        selection = newValue.selection,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (singleLine) {
                                    Modifier
                                } else {
                                    Modifier
                                        .fillMaxHeight()
                                        .verticalScroll(scrollState)
                                        .padding(vertical = 4.dp)
                                },
                            )
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    onFocus()
                                    keyboardController?.hide()
                                }
                            },
                        textStyle = LocalTextStyle.current.merge(
                            TextStyle(
                                color = cs.onSurface,
                                fontWeight = FontWeight.Medium,
                            ),
                        ).merge(inputStyle),
                        singleLine = singleLine,
                        cursorBrush = SolidColor(cs.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.None,
                        ),
                    )
                }

                if (value.text.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(clearSize),
                    ) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(clearSize))
                }
            }
        }
    }
    }
}

/** Prevents the copy/paste toolbar from opening the system keyboard on double-tap. */
private object NoOpTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit

    override fun hide() = Unit
}
