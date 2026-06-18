package com.mh.librarymanager.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.data.store.SearchMatchingStore
import com.mh.librarymanager.domain.MatchingDirection
import com.mh.librarymanager.domain.SearchMatching
import com.mh.librarymanager.ui.components.AppLoadingContent
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import com.mh.librarymanager.ui.search.HebrewKeyboard
import com.mh.librarymanager.ui.search.KeyAction
import com.mh.librarymanager.ui.search.KeyboardEditField
import com.mh.librarymanager.ui.search.SuppressPlatformKeyboardEffect
import kotlinx.coroutines.launch

/**
 * Management → Search matchings (synonyms). Add, edit, delete and reorder rules
 * that link a shortcut/abbreviation to its ordered full words.
 */
@Composable
fun SearchMatchingsManagementScreen(
    viewModel: SearchMatchingsManagementViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val matchings by viewModel.matchings.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // null = closed; a SearchMatching = editor open (existing id => edit, else add).
    var editing by remember { mutableStateOf<SearchMatching?>(null) }
    var transientMessage by remember { mutableStateOf<String?>(null) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    val limitMessage = stringResource(R.string.matching_limit_reached, SearchMatchingStore.MAX_ENTRIES)
    val blankMessage = stringResource(R.string.matching_blank_shortcut)
    val noWordsMessage = stringResource(R.string.matching_no_words_error)

    LaunchedEffect(transientMessage) {
        if (transientMessage != null) {
            kotlinx.coroutines.delay(2200)
            transientMessage = null
        }
    }

    SuppressPlatformKeyboardEffect()

    val cs = MaterialTheme.colorScheme
    AppScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ManagementHeader(
                    title = stringResource(R.string.matchings_management_title),
                    onBack = onBack,
                    onLogout = onLogout,
                    primaryAction = stringResource(R.string.matching_add),
                    onPrimaryAction = {
                        dialogError = null
                        editing = SearchMatching(shortcut = "", words = emptyList())
                    },
                )

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        text = stringResource(R.string.matchings_management_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.matchings_count, matchings.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                when {
                    !loaded -> AppLoadingContent(modifier = Modifier.fillMaxSize())
                    matchings.isEmpty() -> Text(
                        text = stringResource(R.string.matchings_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp, end = 20.dp, bottom = 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(matchings, key = { it.id }) { matching ->
                            MatchingRow(
                                matching = matching,
                                onEdit = {
                                    dialogError = null
                                    editing = matching
                                },
                                onToggleDirection = {
                                    scope.launch {
                                        viewModel.save(matching.copy(direction = matching.direction.toggled()))
                                    }
                                },
                            )
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
    }

    editing?.let { current ->
        val isExisting = matchings.any { it.id == current.id }
        MatchingEditorDialog(
            initial = current,
            errorMessage = dialogError,
            onDismiss = {
                dialogError = null
                editing = null
            },
            onDelete = if (isExisting) {
                {
                    viewModel.delete(current.id)
                    dialogError = null
                    editing = null
                }
            } else null,
            onSave = { result ->
                scope.launch {
                    when (viewModel.save(result)) {
                        SearchMatchingStore.SaveResult.Ok -> {
                            dialogError = null
                            editing = null
                        }
                        SearchMatchingStore.SaveResult.BlankShortcut -> dialogError = blankMessage
                        SearchMatchingStore.SaveResult.NoWords -> dialogError = noWordsMessage
                        SearchMatchingStore.SaveResult.LimitReached -> {
                            dialogError = null
                            editing = null
                            transientMessage = limitMessage
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun MatchingRow(
    matching: SearchMatching,
    onEdit: () -> Unit,
    onToggleDirection: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleGlyphButton(glyph = "✎", background = cs.secondaryContainer, onClick = onEdit)
            Spacer(modifier = Modifier.width(14.dp))
            Surface(
                color = cs.primaryContainer,
                contentColor = cs.onPrimaryContainer,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = matching.shortcut,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                matching.words.forEach { word -> WordTag(word) }
            }
            Spacer(modifier = Modifier.width(14.dp))
            DirectionPill(direction = matching.direction, onClick = onToggleDirection)
        }
    }
}

@Composable
private fun WordTag(word: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.secondaryContainer,
        contentColor = cs.onSecondaryContainer,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Text(
            text = word,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DirectionPill(direction: MatchingDirection, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bidirectional = direction == MatchingDirection.Bidirectional
    val glyph = if (bidirectional) "⇄" else "→"
    val label = stringResource(
        if (bidirectional) R.string.matching_direction_bidirectional
        else R.string.matching_direction_words_to_shortcut,
    )
    Surface(
        color = if (bidirectional) cs.tertiaryContainer else cs.surfaceVariant,
        contentColor = if (bidirectional) cs.onTertiaryContainer else cs.onSurfaceVariant,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = glyph, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

private enum class EditorFocus { Shortcut, Word }

@Composable
private fun MatchingEditorDialog(
    initial: SearchMatching,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (SearchMatching) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    var shortcut by remember(initial.id) {
        mutableStateOf(TextFieldValue(initial.shortcut, TextRange(initial.shortcut.length)))
    }
    var wordInput by remember(initial.id) { mutableStateOf(TextFieldValue("")) }
    val words = remember(initial.id) { mutableStateListOf<String>().apply { addAll(initial.words) } }
    var direction by remember(initial.id) { mutableStateOf(initial.direction) }
    var focus by remember(initial.id) { mutableStateOf(EditorFocus.Shortcut) }

    BackHandler(onBack = onDismiss)

    fun commitPendingWord() {
        val w = wordInput.text.trim()
        if (w.isEmpty()) return
        if (words.none { it.equals(w, ignoreCase = true) }) words.add(w)
        wordInput = TextFieldValue("")
    }

    fun handleKey(action: KeyAction) {
        when (focus) {
            EditorFocus.Shortcut -> shortcut = when (action) {
                is KeyAction.Insert -> shortcut.insertAt(action.text)
                KeyAction.Backspace -> shortcut.backspaceAt()
                KeyAction.ClearField, KeyAction.ClearAll -> TextFieldValue("")
            }
            EditorFocus.Word -> wordInput = when (action) {
                is KeyAction.Insert -> wordInput.insertAt(action.text)
                KeyAction.Backspace -> wordInput.backspaceAt()
                KeyAction.ClearField, KeyAction.ClearAll -> TextFieldValue("")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.94f)
                .widthIn(max = 640.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (onDelete != null) R.string.matching_edit_title else R.string.matching_add,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    CircleGlyphButton(
                        glyph = "×",
                        background = cs.surfaceVariant,
                        onClick = onDismiss,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    KeyboardEditField(
                        label = stringResource(R.string.matching_field_shortcut),
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        isActive = focus == EditorFocus.Shortcut,
                        onFocus = { focus = EditorFocus.Shortcut },
                        onClear = { shortcut = TextFieldValue("") },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.matching_words_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (words.isEmpty()) {
                        Text(
                            text = stringResource(R.string.matching_no_words),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            words.forEachIndexed { index, word ->
                                EditableWordRow(
                                    index = index,
                                    word = word,
                                    isFirst = index == 0,
                                    isLast = index == words.lastIndex,
                                    onMoveUp = { if (index > 0) words.swap(index, index - 1) },
                                    onMoveDown = { if (index < words.lastIndex) words.swap(index, index + 1) },
                                    onRemove = { words.removeAt(index) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Box(modifier = Modifier.weight(1f)) {
                            KeyboardEditField(
                                label = stringResource(R.string.matching_field_word),
                                value = wordInput,
                                onValueChange = { wordInput = it },
                                isActive = focus == EditorFocus.Word,
                                onFocus = { focus = EditorFocus.Word },
                                onClear = { wordInput = TextFieldValue("") },
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { commitPendingWord() },
                            enabled = wordInput.text.isNotBlank(),
                        ) { Text(stringResource(R.string.matching_add_word)) }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DirectionSelector(direction = direction, onToggle = { direction = direction.toggled() })
                }

                Spacer(modifier = Modifier.height(10.dp))

                HebrewKeyboard(
                    onKey = ::handleKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                        .heightIn(min = 280.dp),
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.error,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(stringResource(R.string.delete), color = cs.error)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val pending = wordInput.text.trim()
                            val finalWords = if (pending.isNotEmpty() && words.none { it.equals(pending, ignoreCase = true) }) {
                                words.toList() + pending
                            } else {
                                words.toList()
                            }
                            onSave(
                                initial.copy(
                                    shortcut = shortcut.text,
                                    words = finalWords,
                                    direction = direction,
                                ),
                            )
                        },
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

@Composable
private fun EditableWordRow(
    index: Int,
    word: String,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.secondaryContainer,
        contentColor = cs.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = word,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            CircleGlyphButton(
                glyph = "▲",
                background = if (isFirst) cs.surfaceVariant else cs.surface,
                enabled = !isFirst,
                onClick = onMoveUp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            CircleGlyphButton(
                glyph = "▼",
                background = if (isLast) cs.surfaceVariant else cs.surface,
                enabled = !isLast,
                onClick = onMoveDown,
            )
            Spacer(modifier = Modifier.width(6.dp))
            CircleGlyphButton(glyph = "×", background = cs.errorContainer, onClick = onRemove)
        }
    }
}

@Composable
private fun DirectionSelector(direction: MatchingDirection, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bidirectional = direction == MatchingDirection.Bidirectional
    Surface(
        color = cs.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (bidirectional) "⇄" else "→",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (bidirectional) R.string.matching_direction_bidirectional
                        else R.string.matching_direction_words_to_shortcut,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        if (bidirectional) R.string.matching_direction_hint_bidirectional
                        else R.string.matching_direction_hint_words_to_shortcut,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CircleGlyphButton(
    glyph: String,
    background: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) background else cs.surfaceVariant.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.3f),
        )
    }
}

private fun <T> androidx.compose.runtime.snapshots.SnapshotStateList<T>.swap(a: Int, b: Int) {
    val tmp = this[a]
    this[a] = this[b]
    this[b] = tmp
}

private fun TextFieldValue.insertAt(insertion: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val newText = buildString {
        append(text, 0, start)
        append(insertion)
        append(text, end, text.length)
    }
    return TextFieldValue(text = newText, selection = TextRange(start + insertion.length))
}

private fun TextFieldValue.backspaceAt(): TextFieldValue {
    if (text.isEmpty()) return this
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    if (start != end) {
        val newText = buildString {
            append(text, 0, start)
            append(text, end, text.length)
        }
        return TextFieldValue(text = newText, selection = TextRange(start))
    }
    if (start == 0) return this
    val newText = buildString {
        append(text, 0, start - 1)
        append(text, start, text.length)
    }
    return TextFieldValue(text = newText, selection = TextRange(start - 1))
}
