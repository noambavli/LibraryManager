package com.mh.librarymanager.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.ui.components.BookCard
import com.mh.librarymanager.ui.search.HebrewKeyboard
import com.mh.librarymanager.ui.search.KeyboardEditField
import com.mh.librarymanager.ui.search.SearchField
import com.mh.librarymanager.ui.search.SuppressPlatformKeyboardEffect
import kotlinx.coroutines.launch

/**
 * Management list screen. Left half is the same advanced search the public
 * uses; right half is the result list with edit/duplicate/delete affordances.
 * Tapping a card opens the full editor.
 */
@Composable
fun BooksManagementScreen(
    viewModel: BooksManagementViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenEditor: (bookId: String?) -> Unit,
) {
    val fieldValues by viewModel.fieldValues.collectAsStateWithLifecycle()
    val focusedField by viewModel.focusedField.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val catalogSize by viewModel.catalogSize.collectAsStateWithLifecycle()
    val customColors by viewModel.customColors.collectAsStateWithLifecycle()
    val parentNameLookup by viewModel.parentNameLookup.collectAsStateWithLifecycle()

    SuppressPlatformKeyboardEffect()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ManagementHeader(
            title = stringResource(R.string.books_management_title),
            onBack = onBack,
            onLogout = onLogout,
            primaryAction = stringResource(R.string.add_book),
            onPrimaryAction = { onOpenEditor(null) },
        )

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            SearchPane(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                fieldValues = fieldValues,
                focusedField = focusedField,
                onSetValue = viewModel::setValue,
                onSetFocused = viewModel::setFocused,
                onKey = viewModel::handleKey,
            )

            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.Black),
            )

            ResultsPane(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                viewModel = viewModel,
                results = results,
                catalogSize = catalogSize,
                queryIsEmpty = fieldValues.values.all { it.text.isBlank() },
                customColors = customColors,
                parentNameLookup = parentNameLookup,
                onOpenEditor = onOpenEditor,
            )
        }
    }
}

@Composable
internal fun ManagementHeader(
    title: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    primaryAction: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
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
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (primaryAction != null && onPrimaryAction != null) {
                Button(onClick = onPrimaryAction) {
                    Text("+  $primaryAction")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            OutlinedButton(onClick = onLogout) {
                Text(stringResource(R.string.logout))
            }
        }
    }
}

@Composable
private fun SearchPane(
    modifier: Modifier,
    fieldValues: Map<SearchField, androidx.compose.ui.text.input.TextFieldValue>,
    focusedField: SearchField,
    onSetValue: (SearchField, androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onSetFocused: (SearchField) -> Unit,
    onKey: (com.mh.librarymanager.ui.search.KeyAction) -> Unit,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(0.48f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.search_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            SearchFieldsGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                fieldValues = fieldValues,
                focusedField = focusedField,
                onSetValue = onSetValue,
                onSetFocused = onSetFocused,
            )
        }

        HorizontalDivider(thickness = 2.dp, color = Color.Black)

        HebrewKeyboard(
            onKey = onKey,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.52f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun SearchFieldsGrid(
    modifier: Modifier,
    fieldValues: Map<SearchField, androidx.compose.ui.text.input.TextFieldValue>,
    focusedField: SearchField,
    onSetValue: (SearchField, androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onSetFocused: (SearchField) -> Unit,
) {
    @Composable
    fun field(target: SearchField, modifier: Modifier = Modifier) {
        KeyboardEditField(
            modifier = modifier,
            label = stringResource(target.labelRes),
            value = fieldValues[target] ?: androidx.compose.ui.text.input.TextFieldValue(""),
            onValueChange = { onSetValue(target, it) },
            isActive = focusedField == target,
            onFocus = { onSetFocused(target) },
            onClear = {
                onSetValue(target, androidx.compose.ui.text.input.TextFieldValue(""))
                onSetFocused(target)
            },
            compact = true,
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        field(SearchField.GENERAL)
        field(SearchField.NAME)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            field(SearchField.TOPICS, Modifier.weight(1f))
            field(SearchField.WRITER, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            field(SearchField.CATEGORY, Modifier.weight(1f))
            field(SearchField.SUBCATEGORY, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            field(SearchField.LETTER, Modifier.weight(1f))
            field(SearchField.COLOR, Modifier.weight(1f))
        }
        field(SearchField.NOTES)
    }
}

@Composable
private fun ResultsPane(
    modifier: Modifier,
    viewModel: BooksManagementViewModel,
    results: List<Book>,
    catalogSize: Int,
    queryIsEmpty: Boolean,
    customColors: List<CustomColor>,
    parentNameLookup: Map<String, String>,
    onOpenEditor: (bookId: String?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var deleteCandidate by remember { mutableStateOf<Book?>(null) }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = when {
                catalogSize == 0 -> stringResource(R.string.results_loading)
                queryIsEmpty -> stringResource(R.string.results_total, results.size, catalogSize)
                else -> stringResource(R.string.results_total, results.size, catalogSize)
            },
            style = MaterialTheme.typography.titleMedium,
            color = cs.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = cs.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))

        if (results.isEmpty() && catalogSize > 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.results_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(results, key = { it.id }) { book ->
                    ManagementBookRow(
                        book = book,
                        parentName = book.parentBookId?.let { parentNameLookup[it] },
                        customColors = customColors,
                        onEdit = { onOpenEditor(book.id) },
                        onDuplicate = {
                            val copy = book.copy(
                                id = viewModel.newBookId(),
                                logicalBookId = viewModel.newBookId(),
                                version = 1,
                                isLatest = true,
                                bookNumber = viewModel.suggestNextBookNumber(),
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                            )
                            scope.launch {
                                viewModel.saveAwait(copy)
                                onOpenEditor(copy.id)
                            }
                        },
                        onDelete = { deleteCandidate = book },
                    )
                }
            }
        }
    }

    deleteCandidate?.let { candidate ->
        ConfirmDeleteDialog(
            book = candidate,
            onDismiss = { deleteCandidate = null },
            onConfirm = {
                scope.launch { viewModel.delete(candidate.id) }
                deleteCandidate = null
            },
        )
    }
}

@Composable
private fun ManagementBookRow(
    book: Book,
    parentName: String?,
    customColors: List<CustomColor>,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        BookCard(
            book = book,
            parentName = parentName,
            customColors = customColors,
            onClick = onEdit,
            trailing = {
                Text(
                    text = book.bookNumber.ifBlank { "—" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onDuplicate, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.duplicate_book))
            }
            OutlinedButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.edit_book))
            }
            OutlinedButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    book: Book,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = stringResource(R.string.confirm_delete),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = book.name.ifBlank { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.confirm_delete_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = cs.error,
                            contentColor = cs.onError,
                        ),
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}
