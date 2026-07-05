package com.mh.librarymanager.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.linkedParent
import com.mh.librarymanager.search.SearchEngine
import com.mh.librarymanager.search.SearchQuery
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppPaneDivider
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.BookCard
import kotlinx.coroutines.launch
import com.mh.librarymanager.ui.search.HebrewKeyboard
import com.mh.librarymanager.ui.search.KeyAction
import com.mh.librarymanager.ui.search.KeyboardEditField
import com.mh.librarymanager.ui.search.SuppressPlatformKeyboardEffect

private enum class AnnField { TITLE, DESCRIPTION }

/**
 * Editor for creating a new announcement: title, description, how many days to
 * show, and optional linked books picked via a search dialog.
 */
@Composable
fun AnnouncementEditorScreen(
    viewModel: AnnouncementsManagementViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenBookLocation: (String) -> Unit = {},
) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val booksById by viewModel.booksById.collectAsStateWithLifecycle()
    val customColors by viewModel.customColors.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var days by remember { mutableStateOf(Announcement.DEFAULT_DURATION_DAYS) }
    var linkedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var focused by remember { mutableStateOf(AnnField.TITLE) }
    var pickingBooks by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    SuppressPlatformKeyboardEffect()

    val isDirty = title.text.isNotBlank() || description.text.isNotBlank() || linkedIds.isNotEmpty()
    fun attemptExit(action: () -> Unit) {
        if (isDirty) pendingExit = action else action()
    }
    BackHandler(enabled = true) { attemptExit(onBack) }

    fun handleKey(action: KeyAction) {
        when (focused) {
            AnnField.TITLE -> title = title.applyKey(action)
            AnnField.DESCRIPTION -> description = description.applyKey(action)
        }
    }

    val cs = MaterialTheme.colorScheme
    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
            title = stringResource(R.string.announcement_add),
            canSave = title.text.isNotBlank(),
            onBack = { attemptExit(onBack) },
            onLogout = { attemptExit(onLogout) },
            onSave = {
                scope.launch {
                    viewModel.addAwait(
                        title = title.text,
                        description = description.text,
                        durationDays = days,
                        linkedBookIds = linkedIds,
                    )
                    onBack()
                }
            },
        )

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                KeyboardEditField(
                    label = stringResource(R.string.announcement_field_title),
                    value = title,
                    onValueChange = { title = it },
                    isActive = focused == AnnField.TITLE,
                    onFocus = { focused = AnnField.TITLE },
                    onClear = { title = TextFieldValue(""); focused = AnnField.TITLE },
                )

                KeyboardEditField(
                    label = stringResource(R.string.announcement_field_description),
                    value = description,
                    onValueChange = { description = it },
                    isActive = focused == AnnField.DESCRIPTION,
                    onFocus = { focused = AnnField.DESCRIPTION },
                    onClear = { description = TextFieldValue(""); focused = AnnField.DESCRIPTION },
                    singleLine = false,
                    minHeight = 130.dp,
                )

                LabeledBlock(label = stringResource(R.string.announcement_field_days)) {
                    DaysStepper(days = days, onChange = { days = it })
                }

                LabeledBlock(label = stringResource(R.string.announcement_field_books)) {
                    LinkedBooksEditor(
                        linkedIds = linkedIds,
                        booksById = booksById,
                        customColors = customColors,
                        onAdd = { pickingBooks = true },
                        onRemove = { id -> linkedIds = linkedIds - id },
                        onOpenBookLocation = onOpenBookLocation,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            AppPaneDivider()

            HebrewKeyboard(
                onKey = { handleKey(it) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        }
    }

    if (pickingBooks) {
        BookPickerDialog(
            allBooks = catalog,
            selectedIds = linkedIds,
            onDismiss = { pickingBooks = false },
            onConfirm = { selection ->
                linkedIds = selection
                pickingBooks = false
            },
        )
    }

    pendingExit?.let { exit ->
        ConfirmExitDialog(
            onStay = { pendingExit = null },
            onLeave = {
                pendingExit = null
                exit()
            },
        )
    }
}

@Composable
private fun Header(
    title: String,
    canSave: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(color = AppColors.Panel, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "‹  " + stringResource(R.string.back),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onSave,
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.HeroStart,
                    contentColor = Color.White,
                ),
            ) { Text(stringResource(R.string.save)) }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onLogout,
                border = BorderStroke(1.dp, AppColors.Border),
            ) {
                Text(stringResource(R.string.logout), color = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun LabeledBlock(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        content()
    }
}

@Composable
private fun DaysStepper(days: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(symbol = "−", enabled = days > 1) {
                onChange((days - 1).coerceAtLeast(1))
            }
            Surface(
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outlineVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.announcement_days_value, days),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            StepperButton(symbol = "+", enabled = days < Announcement.MAX_DURATION_DAYS) {
                onChange((days + 1).coerceAtMost(Announcement.MAX_DURATION_DAYS))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 14, 30).forEach { preset ->
                val selected = days == preset
                Surface(
                    color = if (selected) cs.primary else cs.surface,
                    contentColor = if (selected) cs.onPrimary else cs.onSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = if (selected) null else BorderStroke(1.dp, cs.outlineVariant),
                    modifier = Modifier.clickable { onChange(preset) },
                ) {
                    Text(
                        text = stringResource(R.string.announcement_days_value, preset),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (enabled) cs.primary else cs.surfaceVariant,
        contentColor = if (enabled) cs.onPrimary else cs.onSurfaceVariant,
        shape = CircleShape,
        modifier = Modifier.size(44.dp).then(
            if (enabled) Modifier.clickable(onClick = onClick) else Modifier
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = symbol, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LinkedBooksEditor(
    linkedIds: List<String>,
    booksById: Map<String, Book>,
    customColors: List<com.mh.librarymanager.domain.CustomColor>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onOpenBookLocation: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (linkedIds.isEmpty()) {
            Text(
                text = stringResource(R.string.announcement_no_books),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.outline,
            )
        } else {
            linkedIds.forEach { id ->
                val book = booksById[id]
                if (book != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BookCard(
                            book = book,
                            parentBook = book.linkedParent(booksById),
                            customColors = customColors,
                            modifier = Modifier.weight(1f),
                            onOpenLocation = { onOpenBookLocation(book.id) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(cs.surfaceVariant)
                                .clickable { onRemove(id) },
                            contentAlignment = Alignment.Center,
                        ) { Text("×", color = cs.onSurface, style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
        OutlinedButton(onClick = onAdd) { Text(stringResource(R.string.announcement_attach_book)) }
    }
}

@Composable
private fun BookPickerDialog(
    allBooks: List<Book>,
    selectedIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(true) }
    var selection by remember { mutableStateOf(selectedIds) }

    fun handleKey(action: KeyAction) {
        query = query.applyKey(action)
    }

    val filtered = remember(allBooks, query.text) {
        val candidates = allBooks.filter { it.isLatest }
        val q = query.text.trim()
        if (q.isEmpty()) candidates.take(80)
        else SearchEngine(candidates).search(SearchQuery(general = q), limit = 80)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(640.dp).heightIn(max = 640.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.announcement_pick_books_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                KeyboardEditField(
                    label = stringResource(R.string.search_books_hint),
                    value = query,
                    onValueChange = { query = it },
                    isActive = focused,
                    onFocus = { focused = true },
                    onClear = { query = TextFieldValue("") },
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.id }) { book ->
                        val isSelected = book.id in selection
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isSelected) cs.primaryContainer else cs.surface,
                            border = BorderStroke(1.dp, if (isSelected) cs.primary else cs.outlineVariant),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selection = if (isSelected) selection - book.id
                                        else selection + book.id
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.name.ifBlank { "—" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (book.writer.isNotBlank()) {
                                        Text(
                                            text = book.writer,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cs.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HebrewKeyboard(
                    onKey = ::handleKey,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp).height(280.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.announcement_selected_count, selection.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(selection) }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmExitDialog(onStay: () -> Unit, onLeave: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.discard_changes_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.discard_changes_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onStay) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onLeave) { Text(stringResource(R.string.discard_changes_confirm)) }
                }
            }
        }
    }
}

// --- text field key helpers (file-private) -----------------------------------

private fun TextFieldValue.applyKey(action: KeyAction): TextFieldValue = when (action) {
    is KeyAction.Insert -> insertAt(action.text)
    KeyAction.Backspace -> deleteBack()
    KeyAction.ClearField, KeyAction.ClearAll -> TextFieldValue("")
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

private fun TextFieldValue.deleteBack(): TextFieldValue {
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
