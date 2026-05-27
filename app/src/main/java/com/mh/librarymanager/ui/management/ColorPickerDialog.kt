package com.mh.librarymanager.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.ui.components.BuiltInColorNames
import com.mh.librarymanager.ui.components.ColorChipStyle
import com.mh.librarymanager.ui.components.colorLabelKey
import com.mh.librarymanager.ui.components.contrastingForeground
import com.mh.librarymanager.ui.components.resolveBookColorStyle
import com.mh.librarymanager.ui.components.toArgbLong

/**
 * Modal that lets staff pick a color for a book.
 *
 * It surfaces every catalog colour (built-in + custom) as a chip and includes
 * an "add new" panel with RGB sliders + a live preview. Adding a colour saves
 * it to the global palette so it is available for every future book.
 *
 * Returns the canonical name string (the name the chip carries in `book.color`).
 */
@Composable
fun ColorPickerDialog(
    initialColorName: String,
    customColors: List<CustomColor>,
    onAddColor: (CustomColor) -> Unit,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    val knownNames = remember(customColors) {
        val names = LinkedHashSet<String>()
        names += BuiltInColorNames
        names += customColors.map { it.name }
        names.toList()
    }

    var creating by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                onClick = onDismiss,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .width(620.dp)
                .heightIn(max = 560.dp)
                .clickable(
                    onClick = {},
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.color_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
                Spacer(modifier = Modifier.height(14.dp))

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (!creating) {
                        ColorPalette(
                            knownNames = knownNames,
                            initialColorName = initialColorName,
                            customColors = customColors,
                            onPick = onPicked,
                            onCreateNew = { creating = true },
                        )
                    } else {
                        AddColorPanel(
                            existingNames = knownNames,
                            onCancel = { creating = false },
                            onConfirm = { newColor ->
                                onAddColor(newColor)
                                creating = false
                                onPicked(newColor.name)
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPalette(
    knownNames: List<String>,
    initialColorName: String,
    customColors: List<CustomColor>,
    onPick: (String) -> Unit,
    onCreateNew: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        FlowGrid(spacing = 10.dp) {
            knownNames.forEach { name ->
                val style: ColorChipStyle = resolveBookColorStyle(
                    colorName = name,
                    customColors = customColors,
                    cardSurface = cs.surface,
                    fallbackBackground = cs.primaryContainer,
                    fallbackForeground = cs.onPrimaryContainer,
                )
                val isSelected = colorLabelKey(name) == colorLabelKey(initialColorName)
                Surface(
                    color = style.background,
                    contentColor = style.foreground,
                    shape = RoundedCornerShape(12.dp),
                    border = if (isSelected) {
                        BorderStroke(2.dp, cs.primary)
                    } else style.border ?: BorderStroke(1.dp, cs.outlineVariant),
                    shadowElevation = if (isSelected) 4.dp else 1.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(name) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onCreateNew,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("+  " + stringResource(R.string.add_color))
            }
        }
    }
}

@Composable
private fun AddColorPanel(
    existingNames: List<String>,
    onCancel: () -> Unit,
    onConfirm: (CustomColor) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var r by remember { mutableStateOf(0x55.toFloat()) }
    var g by remember { mutableStateOf(0x77.toFloat()) }
    var b by remember { mutableStateOf(0xCC.toFloat()) }
    var nameError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(name) { nameError = null }

    val preview = Color(red = r.toInt(), green = g.toInt(), blue = b.toInt())
    val previewFg = contrastingForeground(preview)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = preview,
            contentColor = previewFg,
            modifier = Modifier.fillMaxWidth().height(80.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = name.ifBlank { "Aa  אבג  123" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        SimpleTextField(
            label = stringResource(R.string.color_name),
            value = name,
            placeholder = stringResource(R.string.color_name_placeholder),
            error = nameError,
            onValueChange = { name = it },
        )

        Spacer(modifier = Modifier.height(14.dp))

        ChannelSlider(label = stringResource(R.string.color_red), value = r, color = Color(0xFFE53935)) { r = it }
        ChannelSlider(label = stringResource(R.string.color_green), value = g, color = Color(0xFF2E7D32)) { g = it }
        ChannelSlider(label = stringResource(R.string.color_blue), value = b, color = Color(0xFF1976D2)) { b = it }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "#%02X%02X%02X".format(r.toInt(), g.toInt(), b.toInt()),
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = {
                    val trimmed = name.trim()
                    when {
                        trimmed.isEmpty() -> nameError = "" // shown via SimpleTextField error styling
                        existingNames.any { it.equals(trimmed, ignoreCase = true) } ->
                            nameError = "exists"
                        else -> onConfirm(
                            CustomColor(
                                name = trimmed,
                                argb = preview.copy(alpha = 1f).toArgbLong(),
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
            ) { Text(stringResource(R.string.add)) }
        }

        // Map the error tokens above to localized messages without re-reading state inside Surface.
        when (nameError) {
            "" -> Text(
                text = stringResource(R.string.color_invalid_name),
                color = cs.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            "exists" -> Text(
                text = stringResource(R.string.color_already_exists),
                color = cs.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            else -> Unit
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(14.dp).clip(CircleShape).background(color),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$label  ${value.toInt()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
            ),
        )
    }
}

@Composable
private fun SimpleTextField(
    label: String,
    value: String,
    placeholder: String,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = cs.surface,
            border = BorderStroke(
                width = if (error != null) 2.dp else 1.dp,
                color = if (error != null) cs.error else cs.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                    singleLine = true,
                )
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.outline,
                    )
                }
            }
        }
    }
}

/**
 * Minimal wrap-row layout. Lays children left-to-right (logical), wrapping
 * when they exceed the available width. Avoids depending on the Foundation
 * `FlowRow` experimental API.
 */
@Composable
private fun FlowGrid(
    spacing: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = { content() },
    )
}
