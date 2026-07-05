package com.mh.librarymanager.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppLoadingContent
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import com.mh.librarymanager.ui.search.HebrewKeyboard
import com.mh.librarymanager.ui.search.KeyAction
import com.mh.librarymanager.ui.search.KeyboardEditField
import com.mh.librarymanager.ui.search.SuppressPlatformKeyboardEffect
import com.mh.librarymanager.ui.text.AppTextCatalog
import com.mh.librarymanager.ui.text.AppTextSection
import com.mh.librarymanager.ui.text.TextEditKeyboard
import com.mh.librarymanager.ui.text.placeholderTokens
import com.mh.librarymanager.ui.text.stringResource
import com.mh.librarymanager.R

/**
 * Management → App texts. Lets staff replace the visible copy of any string in
 * the app (labels, hints, explanations) without a rebuild. Strings are grouped
 * into collapsible sections; each edit opens a full on-screen keyboard so it
 * works inside the kiosk (which has no system keyboard).
 */
@Composable
fun TextManagementScreen(
    viewModel: TextManagementViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val overrides by viewModel.overrides.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var editingId by remember { mutableStateOf<Int?>(null) }
    var confirmResetAll by remember { mutableStateOf(false) }
    // Section titles are stable, so remember expansion keyed by title.
    val expanded = remember { mutableStateOf(setOf<String>()) }

    SuppressPlatformKeyboardEffect()

    fun entryKey(id: Int): String? =
        runCatching { ctx.resources.getResourceEntryName(id) }.getOrNull()

    fun defaultOf(id: Int): String =
        runCatching { ctx.getString(id) }.getOrElse { "" }

    val customizedCount = overrides.size

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ManagementHeader(
                title = stringResource(R.string.management_texts),
                onBack = onBack,
                onLogout = onLogout,
            )

            if (!loaded) {
                AppLoadingContent(modifier = Modifier.height(200.dp))
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp,
                    vertical = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "intro") {
                    IntroBlock(
                        customizedCount = customizedCount,
                        onResetAll = { confirmResetAll = true },
                    )
                }

                AppTextCatalog.sections.forEach { section ->
                    val isOpen = section.title in expanded.value
                    val sectionOverridden = section.ids.count { id ->
                        entryKey(id)?.let { overrides.containsKey(it) } == true
                    }
                    item(key = "section:${section.title}") {
                        SectionHeaderRow(
                            section = section,
                            open = isOpen,
                            overriddenCount = sectionOverridden,
                            onToggle = {
                                expanded.value = if (isOpen) {
                                    expanded.value - section.title
                                } else {
                                    expanded.value + section.title
                                }
                            },
                        )
                    }
                    if (isOpen) {
                        items(section.ids, key = { "entry:$it" }) { id ->
                            val key = entryKey(id)
                            val default = defaultOf(id)
                            val override = key?.let { overrides[it] }
                            TextEntryRow(
                                effective = override ?: default,
                                isOverridden = override != null,
                                onClick = { if (key != null) editingId = id },
                                onReset = { key?.let(viewModel::reset) },
                            )
                        }
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        val key = entryKey(id)
        if (key == null) {
            editingId = null
        } else {
            TextEditorDialog(
                default = defaultOf(id),
                current = overrides[key] ?: defaultOf(id),
                onDismiss = { editingId = null },
                onReset = {
                    viewModel.reset(key)
                    editingId = null
                },
                onSave = { value ->
                    viewModel.save(key, value)
                    editingId = null
                },
            )
        }
    }

    if (confirmResetAll) {
        ConfirmOverlay(
            title = stringResource(R.string.texts_reset_all_title),
            body = stringResource(R.string.texts_reset_all_body),
            confirmLabel = stringResource(R.string.texts_reset_all_confirm),
            onConfirm = {
                viewModel.resetAll()
                confirmResetAll = false
            },
            onDismiss = { confirmResetAll = false },
        )
    }
}

@Composable
private fun IntroBlock(customizedCount: Int, onResetAll: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.management_texts_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.texts_customized_count, customizedCount),
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.TextMuted,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (customizedCount > 0) {
                TextButton(onClick = onResetAll) {
                    Text(
                        text = stringResource(R.string.texts_reset_all),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    section: AppTextSection,
    open: Boolean,
    overriddenCount: Int,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        color = AppColors.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.Border),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (open) "▾" else "▸",
                color = AppColors.AccentMuted,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${section.ids.size}",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextMuted,
            )
            if (overriddenCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                CustomizedChip(count = overriddenCount)
            }
        }
    }
}

@Composable
private fun CustomizedChip(count: Int) {
    Surface(
        color = AppColors.Accent.copy(alpha = 0.15f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = stringResource(R.string.texts_customized_badge, count),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.Accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TextEntryRow(
    effective: String,
    isOverridden: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .clickable(onClick = onClick),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isOverridden) AppColors.Accent.copy(alpha = 0.5f) else AppColors.BorderLight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = effective.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isOverridden) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.texts_entry_customized),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isOverridden) {
                TextButton(onClick = onReset) {
                    Text(
                        text = stringResource(R.string.texts_reset_one),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextSecondary,
                    )
                }
            }
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.AccentMuted,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TextEditorDialog(
    default: String,
    current: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onSave: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var value by remember {
        mutableStateOf(TextFieldValue(current, selection = androidx.compose.ui.text.TextRange(current.length)))
    }
    val requiredTokens = remember(default) { placeholderTokens(default).toSet() }

    fun handleKey(action: KeyAction) {
        value = when (action) {
            is KeyAction.Insert -> value.insertText(action.text)
            KeyAction.Backspace -> value.backspaceText()
            KeyAction.ClearField, KeyAction.ClearAll -> TextFieldValue("")
        }
    }

    val currentTokens = placeholderTokens(value.text).toSet()
    val missingTokens = requiredTokens - currentTokens
    val extraTokens = currentTokens - requiredTokens
    val tokensOk = missingTokens.isEmpty() && extraTokens.isEmpty()
    val changed = value.text != current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 720.dp)
                .padding(vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.texts_editor_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.texts_editor_default_prefix) + " " + default,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(12.dp))
                KeyboardEditField(
                    label = stringResource(R.string.texts_editor_field),
                    value = value,
                    onValueChange = { value = it },
                    isActive = true,
                    onFocus = {},
                    onClear = { value = TextFieldValue("") },
                    singleLine = false,
                    minHeight = 88.dp,
                )

                if (requiredTokens.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.texts_editor_placeholders_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextMuted,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        requiredTokens.forEach { token ->
                            val present = token in currentTokens
                            PlaceholderChip(
                                token = token,
                                present = present,
                                onClick = { value = value.insertText(token) },
                            )
                        }
                    }
                }

                if (!tokensOk) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            missingTokens.isNotEmpty() ->
                                stringResource(R.string.texts_editor_missing_tokens, missingTokens.joinToString("  "))
                            else ->
                                stringResource(R.string.texts_editor_extra_tokens, extraTokens.joinToString("  "))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextEditKeyboard(
                    onKey = ::handleKey,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).height(280.dp),
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onReset) {
                        Text(
                            text = stringResource(R.string.texts_editor_reset),
                            color = AppColors.TextSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { onSave(value.text.trim()) },
                        enabled = tokensOk && changed && value.text.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.HeroStart,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderChip(token: String, present: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (present) AppColors.Accent.copy(alpha = 0.15f) else cs.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (present) AppColors.Accent.copy(alpha = 0.5f) else AppColors.Border),
    ) {
        Text(
            text = token,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (present) AppColors.Accent else AppColors.TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ConfirmOverlay(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(520.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.error,
                            contentColor = cs.onError,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(confirmLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun TextFieldValue.insertText(insertion: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val newText = buildString {
        append(text, 0, start)
        append(insertion)
        append(text, end, text.length)
    }
    return TextFieldValue(
        text = newText,
        selection = androidx.compose.ui.text.TextRange(start + insertion.length),
    )
}

private fun TextFieldValue.backspaceText(): TextFieldValue {
    if (text.isEmpty()) return this
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    if (start != end) {
        val newText = buildString {
            append(text, 0, start)
            append(text, end, text.length)
        }
        return TextFieldValue(text = newText, selection = androidx.compose.ui.text.TextRange(start))
    }
    if (start == 0) return this
    val newText = buildString {
        append(text, 0, start - 1)
        append(text, start, text.length)
    }
    return TextFieldValue(text = newText, selection = androidx.compose.ui.text.TextRange(start - 1))
}
