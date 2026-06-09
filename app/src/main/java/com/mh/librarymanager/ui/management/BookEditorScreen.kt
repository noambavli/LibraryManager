package com.mh.librarymanager.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.BookState
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.ui.components.resolveBookColorStyle
import com.mh.librarymanager.ui.search.HebrewKeyboard
import com.mh.librarymanager.ui.search.KeyAction
import com.mh.librarymanager.ui.search.KeyboardEditField
import com.mh.librarymanager.ui.search.SuppressPlatformKeyboardEffect
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Fields that hold typed text in the editor and therefore plug into the
 * in-app Hebrew keyboard via the focused-field pattern.
 */
private enum class EditField {
    NAME, WRITER, BOOK_NUMBER, DISPLAY_NUMBER, LETTER, CATEGORY, TOPICS, NOTES,
}

/** Snapshot of the form. Stored as one struct so save/duplicate is trivial. */
private data class FormState(
    val id: String,
    val logicalBookId: String,
    val version: Int,
    val createdAt: Long,
    val name: String = "",
    val writer: String = "",
    val bookNumber: String = "",
    val displayNumber: String = "",
    val letter: String = "",
    val category: String = "",
    val topics: String = "",
    val notes: String = "",
    val color: String = "",
    val place: BookPlace = BookPlace.OTZAR,
    val state: BookState = BookState.AVAILABLE,
    val subcategories: List<String> = emptyList(),
    val relations: List<String> = emptyList(),
    val parentBookId: String? = null,
)

private fun Book.toForm(): FormState = FormState(
    id = id,
    logicalBookId = logicalBookId,
    version = version,
    createdAt = createdAt,
    name = name,
    writer = writer,
    bookNumber = bookNumber,
    displayNumber = displayNumber,
    letter = letter,
    category = category,
    topics = topics,
    notes = notes,
    color = color,
    place = place,
    state = state,
    subcategories = subcategories,
    relations = relations,
    parentBookId = parentBookId,
)

private fun FormState.toBook(): Book = Book(
    id = id,
    logicalBookId = logicalBookId,
    version = version,
    isLatest = true,
    name = name.trim(),
    topics = topics.trim(),
    writer = writer.trim(),
    bookNumber = bookNumber.trim(),
    displayNumber = displayNumber.trim(),
    letter = letter.trim(),
    color = color.trim(),
    category = category.trim(),
    subcategories = subcategories.map { it.trim() }.filter { it.isNotBlank() },
    notes = notes.trim(),
    place = place,
    state = state,
    parentBookId = parentBookId,
    relations = relations.map { it.trim() }.filter { it.isNotBlank() },
    createdAt = createdAt,
    updatedAt = System.currentTimeMillis(),
)

@Composable
fun BookEditorScreen(
    viewModel: BooksManagementViewModel,
    bookId: String?,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onReplaceWith: (newBookId: String) -> Unit,
) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val customColors by viewModel.customColors.collectAsStateWithLifecycle()
    val parentNameLookup by viewModel.parentNameLookup.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val now = remember { System.currentTimeMillis() }
    // Generate the placeholder UUID once per editor instance so the form
    // doesn't reset every recomposition while we wait for the catalog flow
    // to catch up after a save-and-duplicate.
    val fallbackId = remember(bookId) {
        bookId ?: "book-${UUID.randomUUID()}"
    }
    val seed = remember(bookId, catalog, fallbackId) {
        if (bookId == null) {
            FormState(
                id = fallbackId,
                logicalBookId = fallbackId,
                version = 1,
                createdAt = now,
                bookNumber = viewModel.suggestNextBookNumber(),
            )
        } else {
            catalog.firstOrNull { it.id == bookId }?.toForm()
                ?: FormState(
                    id = fallbackId,
                    logicalBookId = fallbackId,
                    version = 1,
                    createdAt = now,
                    bookNumber = viewModel.suggestNextBookNumber(),
                )
        }
    }

    var form by remember(bookId) { mutableStateOf(seed) }
    // Pristine snapshot used to detect unsaved edits so we can warn before an
    // accidental back-press / edge-swipe throws away an in-progress book.
    var baseline by remember(bookId) { mutableStateOf(seed) }
    var focused by remember { mutableStateOf(EditField.NAME) }
    var fieldValues by remember(bookId) {
        mutableStateOf(initialFieldValues(seed))
    }

    // When the catalog finally produces an entry for the bookId we're editing
    // (e.g. directly after a save-and-duplicate save lands), refresh the form
    // with the real persisted data — but only once, while the form still
    // matches the placeholder state.
    LaunchedEffect(catalog, bookId) {
        if (bookId == null) return@LaunchedEffect
        val persisted = catalog.firstOrNull { it.id == bookId } ?: return@LaunchedEffect
        if (form.name.isEmpty() && form.writer.isEmpty() && form.bookNumber.isEmpty() &&
            form.notes.isEmpty() && form.color.isEmpty()
        ) {
            form = persisted.toForm()
            baseline = persisted.toForm()
            fieldValues = initialFieldValues(form)
        }
    }

    // Dialog state
    var pickingColor by remember { mutableStateOf(false) }
    var pickingParent by remember { mutableStateOf(false) }
    var addingSubcategory by remember { mutableStateOf(false) }
    var addingRelation by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // Holds the navigation action queued behind the "discard changes?" dialog.
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isDirty = form != baseline

    fun attemptExit(action: () -> Unit) {
        if (isDirty) pendingExit = action else action()
    }

    // Intercept the hardware/edge-swipe back so a stray gesture can't silently
    // drop an in-progress book. When clean, fall through to normal navigation.
    BackHandler(enabled = true) { attemptExit(onBack) }

    SuppressPlatformKeyboardEffect()

    fun setText(field: EditField, value: TextFieldValue) {
        fieldValues = fieldValues.toMutableMap().also { it[field] = value }
        form = form.copy(
            name = if (field == EditField.NAME) value.text else form.name,
            writer = if (field == EditField.WRITER) value.text else form.writer,
            bookNumber = if (field == EditField.BOOK_NUMBER) value.text else form.bookNumber,
            displayNumber = if (field == EditField.DISPLAY_NUMBER) value.text else form.displayNumber,
            letter = if (field == EditField.LETTER) value.text else form.letter,
            category = if (field == EditField.CATEGORY) value.text else form.category,
            topics = if (field == EditField.TOPICS) value.text else form.topics,
            notes = if (field == EditField.NOTES) value.text else form.notes,
        )
    }

    fun handleKey(action: KeyAction) {
        val current = fieldValues[focused] ?: TextFieldValue("")
        val next: TextFieldValue = when (action) {
            is KeyAction.Insert -> current.insertAt(action.text)
            KeyAction.Backspace -> current.deleteBack()
            KeyAction.ClearField -> TextFieldValue("")
            KeyAction.ClearAll -> TextFieldValue("")
        }
        setText(focused, next)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditorHeader(
            isNew = bookId == null,
            onBack = { attemptExit(onBack) },
            onLogout = { attemptExit(onLogout) },
            onSave = {
                scope.launch {
                    viewModel.saveAwait(form.toBook())
                    onBack()
                }
            },
            onSaveAndDuplicate = {
                val original = form.toBook()
                val dup = original.copy(
                    id = "book-${UUID.randomUUID()}",
                    logicalBookId = "book-${UUID.randomUUID()}",
                    version = 1,
                    bookNumber = viewModel.suggestNextBookNumber(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                scope.launch {
                    viewModel.saveBothAwait(original, dup)
                    onReplaceWith(dup.id)
                }
            },
            onDelete = { confirmingDelete = true },
            canDelete = bookId != null,
        )

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            FormColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                form = form,
                fieldValues = fieldValues,
                focused = focused,
                customColors = customColors,
                parentNameLookup = parentNameLookup,
                onSetFocused = { focused = it },
                onSetText = ::setText,
                onChangeColor = { pickingColor = true },
                onChangeParent = { pickingParent = true },
                onAddSubcategory = { addingSubcategory = true },
                onRemoveSubcategory = { idx ->
                    form = form.copy(subcategories = form.subcategories.filterIndexed { i, _ -> i != idx })
                },
                onAddRelation = { addingRelation = true },
                onRemoveRelation = { idx ->
                    form = form.copy(relations = form.relations.filterIndexed { i, _ -> i != idx })
                },
                onSetPlace = { form = form.copy(place = it) },
                onSetState = { form = form.copy(state = it) },
            )

            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.Black))

            HebrewKeyboard(
                onKey = { handleKey(it) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }

    if (pickingColor) {
        ColorPickerDialog(
            initialColorName = form.color,
            customColors = customColors,
            onAddColor = { viewModel.upsertColor(it) },
            onDismiss = { pickingColor = false },
            onPicked = {
                form = form.copy(color = it)
                pickingColor = false
            },
        )
    }

    if (pickingParent) {
        ParentBookPickerDialog(
            allBooks = catalog,
            excludedId = form.id,
            currentParentId = form.parentBookId,
            onDismiss = { pickingParent = false },
            onClear = {
                form = form.copy(parentBookId = null)
                pickingParent = false
            },
            onPick = {
                form = form.copy(parentBookId = it.id)
                pickingParent = false
            },
        )
    }

    if (addingSubcategory) {
        TextEntryDialog(
            title = stringResource(R.string.field_subcategories),
            onDismiss = { addingSubcategory = false },
            onConfirm = { entry ->
                if (entry.isNotBlank()) {
                    form = form.copy(subcategories = form.subcategories + entry.trim())
                }
                addingSubcategory = false
            },
        )
    }

    if (addingRelation) {
        TextEntryDialog(
            title = stringResource(R.string.field_relations),
            onDismiss = { addingRelation = false },
            onConfirm = { entry ->
                if (entry.isNotBlank()) {
                    form = form.copy(relations = form.relations + entry.trim())
                }
                addingRelation = false
            },
        )
    }

    pendingExit?.let { exit ->
        ConfirmDialog(
            title = stringResource(R.string.discard_changes_title),
            body = stringResource(R.string.discard_changes_body),
            confirmLabel = stringResource(R.string.discard_changes_confirm),
            destructive = true,
            onDismiss = { pendingExit = null },
            onConfirm = {
                pendingExit = null
                exit()
            },
        )
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete),
            body = stringResource(R.string.confirm_delete_body),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                scope.launch {
                    viewModel.delete(form.id)
                    confirmingDelete = false
                    onBack()
                }
            },
        )
    }
}

@Composable
private fun EditorHeader(
    isNew: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSave: () -> Unit,
    onSaveAndDuplicate: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surface, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "‹  " + stringResource(R.string.back),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (isNew) R.string.add_book else R.string.edit_book
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )

            Spacer(modifier = Modifier.weight(1f))

            if (canDelete) {
                OutlinedButton(
                    onClick = onDelete,
                    border = BorderStroke(1.dp, cs.error),
                ) {
                    Text(stringResource(R.string.delete), color = cs.error)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            OutlinedButton(onClick = onSaveAndDuplicate) {
                Text(stringResource(R.string.save_and_duplicate))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSave) { Text(stringResource(R.string.save)) }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onLogout) { Text(stringResource(R.string.logout)) }
        }
    }
}

@Composable
private fun FormColumn(
    modifier: Modifier,
    form: FormState,
    fieldValues: Map<EditField, TextFieldValue>,
    focused: EditField,
    customColors: List<CustomColor>,
    parentNameLookup: Map<String, String>,
    onSetFocused: (EditField) -> Unit,
    onSetText: (EditField, TextFieldValue) -> Unit,
    onChangeColor: () -> Unit,
    onChangeParent: () -> Unit,
    onAddSubcategory: () -> Unit,
    onRemoveSubcategory: (Int) -> Unit,
    onAddRelation: () -> Unit,
    onRemoveRelation: (Int) -> Unit,
    onSetPlace: (BookPlace) -> Unit,
    onSetState: (BookState) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        @Composable
        fun textField(
            field: EditField,
            labelRes: Int,
            modifier: Modifier = Modifier,
            singleLine: Boolean = true,
            minHeight: androidx.compose.ui.unit.Dp? = null,
        ) {
            KeyboardEditField(
                modifier = modifier,
                label = stringResource(labelRes),
                value = fieldValues[field] ?: TextFieldValue(""),
                onValueChange = { onSetText(field, it) },
                isActive = focused == field,
                onFocus = { onSetFocused(field) },
                onClear = {
                    onSetText(field, TextFieldValue(""))
                    onSetFocused(field)
                },
                compact = false,
                singleLine = singleLine,
                minHeight = minHeight,
            )
        }

        textField(EditField.NAME, R.string.field_name)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            textField(EditField.WRITER, R.string.field_writer, modifier = Modifier.weight(1f))
            textField(EditField.BOOK_NUMBER, R.string.field_book_number, modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            textField(EditField.DISPLAY_NUMBER, R.string.field_display_number, modifier = Modifier.weight(1f))
            textField(EditField.LETTER, R.string.field_letter, modifier = Modifier.weight(1f))
        }

        // Place
        LabeledBlock(label = stringResource(R.string.field_place)) {
            SegmentedRow(
                options = BookPlace.entries.filter { it != BookPlace.UNSPECIFIED },
                selected = form.place,
                labelFor = { stringResource(it.editorLabelRes()) },
                onSelect = onSetPlace,
            )
        }

        // State
        LabeledBlock(label = stringResource(R.string.field_state)) {
            SegmentedRow(
                options = BookState.entries.toList(),
                selected = form.state,
                labelFor = {
                    when (it) {
                        BookState.AVAILABLE -> stringResource(R.string.book_state_available)
                        BookState.UNAVAILABLE -> stringResource(R.string.book_state_unavailable)
                        BookState.IN_REPAIR -> stringResource(R.string.book_state_in_repair)
                    }
                },
                onSelect = onSetState,
                accentFor = {
                    when (it) {
                        BookState.UNAVAILABLE -> Color(0xFFC62828)
                        BookState.IN_REPAIR -> Color(0xFF0B3A6F)
                        BookState.AVAILABLE -> null
                    }
                },
            )
        }

        // Color
        LabeledBlock(label = stringResource(R.string.field_color)) {
            val style = resolveBookColorStyle(
                colorName = form.color,
                customColors = customColors,
                cardSurface = cs.surface,
                fallbackBackground = cs.surfaceVariant,
                fallbackForeground = cs.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = style.background,
                    contentColor = style.foreground,
                    border = style.border ?: BorderStroke(1.dp, cs.outlineVariant),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = form.color.ifBlank { stringResource(R.string.none_selected) },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(onClick = onChangeColor) { Text(stringResource(R.string.add_value)) }
            }
        }

        textField(EditField.CATEGORY, R.string.field_category)

        // Subcategories
        LabeledBlock(label = stringResource(R.string.field_subcategories)) {
            ChipListEditor(
                items = form.subcategories,
                emptyLabel = stringResource(R.string.none_selected),
                onAdd = onAddSubcategory,
                onRemove = onRemoveSubcategory,
            )
        }

        textField(EditField.TOPICS, R.string.field_topics)

        // Parent book
        LabeledBlock(label = stringResource(R.string.field_parent)) {
            val parentName = form.parentBookId?.let { parentNameLookup[it] }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = cs.surface,
                    border = BorderStroke(1.dp, cs.outlineVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = parentName ?: stringResource(R.string.none_selected),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (parentName == null) cs.outline else cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(onClick = onChangeParent) { Text(stringResource(R.string.choose_parent)) }
            }
        }

        // Relations
        LabeledBlock(label = stringResource(R.string.field_relations)) {
            ChipListEditor(
                items = form.relations,
                emptyLabel = stringResource(R.string.none_selected),
                onAdd = onAddRelation,
                onRemove = onRemoveRelation,
            )
        }

        // Notes (multiline)
        textField(
            EditField.NOTES,
            R.string.field_notes,
            singleLine = false,
            minHeight = 120.dp,
        )

        Spacer(modifier = Modifier.height(20.dp))
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
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    accentFor: @Composable (T) -> Color? = { null },
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val accent = accentFor(option)
            val bg = when {
                isSelected && accent != null -> accent
                isSelected -> cs.primary
                else -> cs.surface
            }
            val fg = when {
                isSelected && accent != null -> Color.White
                isSelected -> cs.onPrimary
                else -> cs.onSurface
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                color = bg,
                contentColor = fg,
                shape = RoundedCornerShape(10.dp),
                border = if (isSelected) null else BorderStroke(1.dp, cs.outlineVariant),
                shadowElevation = if (isSelected) 2.dp else 0.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labelFor(option),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipListEditor(
    items: List<String>,
    emptyLabel: String,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (items.isEmpty()) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.outline,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        items.forEachIndexed { idx, value ->
            Surface(
                color = cs.surfaceVariant,
                contentColor = cs.onSurfaceVariant,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, cs.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(cs.outlineVariant)
                            .clickable { onRemove(idx) },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", color = cs.onSurface) }
                }
            }
        }
        OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(20.dp)) {
            Text("+  " + stringResource(R.string.add_value))
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
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
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (destructive) {
                        Button(
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.error,
                                contentColor = cs.onError,
                            ),
                        ) { Text(confirmLabel) }
                    } else {
                        Button(onClick = onConfirm) { Text(confirmLabel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextEntryDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(true) }

    fun handleKey(action: KeyAction) {
        text = when (action) {
            is KeyAction.Insert -> text.insertAt(action.text)
            KeyAction.Backspace -> text.deleteBack()
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
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(14.dp))
                KeyboardEditField(
                    label = title,
                    value = text,
                    onValueChange = { text = it },
                    isActive = focused,
                    onFocus = { focused = true },
                    onClear = { text = TextFieldValue("") },
                )
                Spacer(modifier = Modifier.height(14.dp))
                HebrewKeyboard(
                    onKey = ::handleKey,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).height(240.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(onClick = { onConfirm(text.text) }) {
                        Text(stringResource(R.string.add))
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentBookPickerDialog(
    allBooks: List<Book>,
    excludedId: String,
    currentParentId: String?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onPick: (Book) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(true) }

    fun handleKey(action: KeyAction) {
        query = when (action) {
            is KeyAction.Insert -> query.insertAt(action.text)
            KeyAction.Backspace -> query.deleteBack()
            KeyAction.ClearField, KeyAction.ClearAll -> TextFieldValue("")
        }
    }

    val filtered = remember(allBooks, query.text, excludedId) {
        val q = query.text.trim()
        val candidates = allBooks.filter { it.id != excludedId && it.isLatest }
        if (q.isEmpty()) candidates.take(80)
        else candidates.filter { it.name.contains(q) || it.writer.contains(q) }.take(80)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(640.dp).heightIn(max = 620.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.choose_parent),
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
                        val isCurrent = book.id == currentParentId
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isCurrent) cs.primaryContainer else cs.surface,
                            border = BorderStroke(1.dp, if (isCurrent) cs.primary else cs.outlineVariant),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPick(book) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
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
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HebrewKeyboard(
                    onKey = ::handleKey,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onClear) { Text(stringResource(R.string.clear_parent)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

// --- shared text field value helpers -----------------------------------------

private fun TextFieldValue.insertAt(insertion: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val newText = buildString {
        append(text, 0, start)
        append(insertion)
        append(text, end, text.length)
    }
    val cursor = start + insertion.length
    return TextFieldValue(text = newText, selection = androidx.compose.ui.text.TextRange(cursor))
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
        return TextFieldValue(text = newText, selection = androidx.compose.ui.text.TextRange(start))
    }
    if (start == 0) return this
    val newText = buildString {
        append(text, 0, start - 1)
        append(text, start, text.length)
    }
    return TextFieldValue(text = newText, selection = androidx.compose.ui.text.TextRange(start - 1))
}

private fun initialFieldValues(form: FormState): Map<EditField, TextFieldValue> = mapOf(
    EditField.NAME to TextFieldValue(form.name),
    EditField.WRITER to TextFieldValue(form.writer),
    EditField.BOOK_NUMBER to TextFieldValue(form.bookNumber),
    EditField.DISPLAY_NUMBER to TextFieldValue(form.displayNumber),
    EditField.LETTER to TextFieldValue(form.letter),
    EditField.CATEGORY to TextFieldValue(form.category),
    EditField.TOPICS to TextFieldValue(form.topics),
    EditField.NOTES to TextFieldValue(form.notes),
)
