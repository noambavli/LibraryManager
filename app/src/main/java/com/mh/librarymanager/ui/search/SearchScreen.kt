package com.mh.librarymanager.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.domain.linkedParent
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppHorizontalDivider
import com.mh.librarymanager.ui.components.AppPaneDivider
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.PublicBackBar
import com.mh.librarymanager.ui.components.BookCard

/**
 * Half/half search screen. Left pane holds search fields and the in-app
 * keyboard; right pane shows live results. Everything is always visible —
 * there is no collapse / minimise affordance by design.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: (() -> Unit)? = null,
    onOpenBookLocation: (String) -> Unit = {},
) {
    val fieldValues by viewModel.fieldValues.collectAsStateWithLifecycle()
    val focusedField by viewModel.focusedField.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val catalogSize by viewModel.catalogSize.collectAsStateWithLifecycle()
    val catalogLoaded by viewModel.catalogLoaded.collectAsStateWithLifecycle()
    val customColors by viewModel.customColors.collectAsStateWithLifecycle()
    val booksById by viewModel.booksById.collectAsStateWithLifecycle()
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()

    SuppressPlatformKeyboardEffect()

    DisposableEffect(Unit) {
        onDispose { viewModel.finalizePublicSearchSession() }
    }

    val commitSearch = { viewModel.commitSearchToHistory(results.size) }

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            if (onBack != null) {
                PublicBackBar(onBack = {
                    commitSearch()
                    onBack()
                })
            }
            Row(modifier = Modifier.fillMaxSize()) {
            SearchPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                fieldValues = fieldValues,
                focusedField = focusedField,
                shortcuts = shortcuts,
                onApplyShortcut = viewModel::applyShortcut,
                onSetValue = viewModel::setValue,
                onSetFocused = viewModel::setFocused,
                onKey = viewModel::handleKey,
                onOutsideKeyboard = commitSearch,
            )

            AppPaneDivider()

            ResultsPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                results = results,
                scrollResetKey = fieldValues.queryScrollKey(),
                catalogSize = catalogSize,
                catalogLoaded = catalogLoaded,
                queryIsEmpty = fieldValues.values.all { it.text.isBlank() },
                customColors = customColors,
                booksById = booksById,
                onOutsideKeyboard = commitSearch,
                onOpenBookLocation = onOpenBookLocation,
            )
            }
        }
    }
}

@Composable
private fun SearchPane(
    modifier: Modifier,
    fieldValues: Map<SearchField, androidx.compose.ui.text.input.TextFieldValue>,
    focusedField: SearchField,
    shortcuts: List<String>,
    onApplyShortcut: (String) -> Unit,
    onSetValue: (SearchField, androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onSetFocused: (SearchField) -> Unit,
    onKey: (KeyAction) -> Unit,
    onOutsideKeyboard: () -> Unit,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(0.48f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onOutsideKeyboard() })
                }
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
                shortcuts = shortcuts,
                onApplyShortcut = onApplyShortcut,
                onSetValue = onSetValue,
                onSetFocused = onSetFocused,
                compactInput = true,
            )
        }

        AppHorizontalDivider()

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
    shortcuts: List<String>,
    onApplyShortcut: (String) -> Unit,
    onSetValue: (SearchField, androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onSetFocused: (SearchField) -> Unit,
    compactInput: Boolean = false,
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
            compact = compactInput,
        )
    }

    val spacing = if (compactInput) 7.dp else 10.dp

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (shortcuts.isNotEmpty()) {
            ShortcutTags(shortcuts = shortcuts, onApply = onApplyShortcut)
        }
        field(SearchField.GENERAL)
        field(SearchField.NAME)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            field(SearchField.TOPICS, Modifier.weight(1f))
            field(SearchField.WRITER, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            field(SearchField.CATEGORY, Modifier.weight(1f))
            field(SearchField.SUBCATEGORY, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            field(SearchField.LETTER, Modifier.weight(1f))
            field(SearchField.COLOR, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            field(SearchField.DISPLAY_NUMBER, Modifier.weight(1f))
            field(SearchField.BOOK_NUMBER, Modifier.weight(1f))
        }
        field(SearchField.NOTES)
    }
}

@Composable
private fun ShortcutTags(
    shortcuts: List<String>,
    onApply: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.search_shortcuts_label),
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shortcuts.forEach { word ->
                Surface(
                    color = cs.secondaryContainer,
                    contentColor = cs.onSecondaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, cs.outlineVariant),
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onApply(word) },
                ) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Map<SearchField, androidx.compose.ui.text.input.TextFieldValue>.queryScrollKey(): String =
    SearchField.entries.joinToString("|") { field -> this[field]?.text.orEmpty() }

@Composable
private fun ResultsPane(
    modifier: Modifier,
    results: List<
            Book>,
    scrollResetKey: String,
    catalogSize: Int,
    catalogLoaded: Boolean,
    queryIsEmpty: Boolean,
    customColors: List<CustomColor>,
    booksById: Map<String, Book>,
    onOutsideKeyboard: () -> Unit,
    onOpenBookLocation: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollResetKey, results) {
        if (results.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    Column(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onOutsideKeyboard() })
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        ResultsHeader(
            count = results.size,
            catalogSize = catalogSize,
            catalogLoaded = catalogLoaded,
            queryIsEmpty = queryIsEmpty,
        )
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))

        when {
            !catalogLoaded -> CenteredHint(
                primary = stringResource(R.string.results_loading),
                secondary = null,
                showSpinner = true,
            )
            results.isEmpty() && queryIsEmpty -> CenteredHint(
                primary = stringResource(R.string.results_idle),
                secondary = null,
            )
            results.isEmpty() -> CenteredHint(
                primary = stringResource(R.string.results_empty),
                secondary = stringResource(R.string.results_empty_hint),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(results, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        parentBook = book.linkedParent(booksById),
                        customColors = customColors,
                        onOpenLocation = { onOpenBookLocation(book.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsHeader(
    count: Int,
    catalogSize: Int,
    catalogLoaded: Boolean,
    queryIsEmpty: Boolean,
) {
    val text = when {
        !catalogLoaded -> stringResource(R.string.results_loading)
        queryIsEmpty && count == 0 -> stringResource(R.string.results_idle)
        else -> stringResource(R.string.results_total, count, catalogSize)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CenteredHint(primary: String, secondary: String?, showSpinner: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = primary,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (secondary != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
