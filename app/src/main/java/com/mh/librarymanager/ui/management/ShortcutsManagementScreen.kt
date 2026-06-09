package com.mh.librarymanager.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.data.store.SearchShortcutStore
import com.mh.librarymanager.ui.search.HebrewKeyboard
import com.mh.librarymanager.ui.search.KeyAction
import com.mh.librarymanager.ui.search.KeyboardEditField
import com.mh.librarymanager.ui.search.SuppressPlatformKeyboardEffect
import kotlinx.coroutines.launch

/**
 * Management → Search shortcuts. Add (up to the cap) and delete the quick tags
 * shown above the public general-search field.
 */
@Composable
fun ShortcutsManagementScreen(
    viewModel: ShortcutsManagementViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var transientMessage by remember { mutableStateOf<String?>(null) }

    val atCap = shortcuts.size >= viewModel.maxShortcuts
    val fullMessage = stringResource(R.string.shortcut_limit_reached, viewModel.maxShortcuts)
    val dupMessage = stringResource(R.string.shortcut_duplicate)
    val blankMessage = stringResource(R.string.shortcut_blank)

    LaunchedEffect(transientMessage) {
        if (transientMessage != null) {
            kotlinx.coroutines.delay(2200)
            transientMessage = null
        }
    }

    SuppressPlatformKeyboardEffect()

    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ManagementHeader(
                title = stringResource(R.string.shortcuts_management_title),
                onBack = onBack,
                onLogout = onLogout,
                primaryAction = stringResource(R.string.shortcut_add),
                onPrimaryAction = if (atCap) null else ({ adding = true }),
            )

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(R.string.shortcuts_management_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.shortcut_count, shortcuts.size, viewModel.maxShortcuts),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (shortcuts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.shortcuts_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        shortcuts.forEach { word ->
                            ShortcutChip(word = word, onRemove = { viewModel.delete(word) })
                        }
                    }
                }
            }
        }

        if (transientMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
                    .fillMaxWidth(),
                color = cs.inverseSurface,
                contentColor = cs.inverseOnSurface,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = transientMessage!!,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }

    if (adding) {
        AddShortcutDialog(
            onDismiss = { adding = false },
            onConfirm = { word ->
                scope.launch {
                    val result = viewModel.add(word)
                    adding = false
                    transientMessage = when (result) {
                        SearchShortcutStore.AddResult.Ok -> null
                        SearchShortcutStore.AddResult.Blank -> blankMessage
                        SearchShortcutStore.AddResult.Duplicate -> dupMessage
                        SearchShortcutStore.AddResult.LimitReached -> fullMessage
                    }
                }
            },
        )
    }
}

@Composable
private fun ShortcutChip(word: String, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.secondaryContainer,
        contentColor = cs.onSecondaryContainer,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = word,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(cs.outlineVariant)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = cs.onSurface, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(true) }

    fun handleKey(action: KeyAction) {
        text = when (action) {
            is KeyAction.Insert -> text.insertShortcut(action.text)
            KeyAction.Backspace -> text.backspaceShortcut()
            KeyAction.ClearField, KeyAction.ClearAll -> TextFieldValue("")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(560.dp).heightIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.shortcut_add),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(14.dp))
                KeyboardEditField(
                    label = stringResource(R.string.shortcut_field_word),
                    value = text,
                    onValueChange = { text = it },
                    isActive = focused,
                    onFocus = { focused = true },
                    onClear = { text = TextFieldValue("") },
                )
                Spacer(modifier = Modifier.height(14.dp))
                HebrewKeyboard(
                    onKey = ::handleKey,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { onConfirm(text.text) },
                        enabled = text.text.isNotBlank(),
                    ) { Text(stringResource(R.string.add)) }
                }
            }
        }
    }
}

private fun TextFieldValue.insertShortcut(insertion: String): TextFieldValue {
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

private fun TextFieldValue.backspaceShortcut(): TextFieldValue {
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
