package com.mh.librarymanager.ui.management

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import com.mh.librarymanager.domain.AuditEvent
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.BookState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Management → History.
 *
 * Lists every recorded mutation newest-first with a colour-coded kind tag,
 * the book name at the time, and a contextual restore button. Updates can
 * be expanded inline to show the exact field-level diff (old → new).
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var transientMessage by remember { mutableStateOf<String?>(null) }

    val successText = stringResource(R.string.history_restore_success)
    val conflictText = stringResource(R.string.history_restore_conflict)

    LaunchedEffect(transientMessage) {
        if (transientMessage != null) {
            kotlinx.coroutines.delay(2200)
            transientMessage = null
        }
    }

    val cs = MaterialTheme.colorScheme
    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ManagementHeader(
            title = stringResource(R.string.history_title),
            onBack = onBack,
            onLogout = onLogout,
        )

        if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            return@Column
        }

        val listState = rememberLazyListState()
        val grouped = remember(rows) { groupByDay(rows) }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (label, items) ->
                    item(key = "day-$label") { DayHeader(label) }
                    items(items, key = { it.event.id }) { row ->
                        HistoryRowCard(
                            row = row,
                            onRestoreDeleted = { ev ->
                                scope.launch {
                                    val outcome = viewModel.restoreDeleted(ev)
                                    transientMessage = if (outcome == RestoreOutcome.Ok) {
                                        successText
                                    } else {
                                        conflictText
                                    }
                                }
                            },
                            onRestoreUpdate = { ev ->
                                scope.launch {
                                    val outcome = viewModel.restoreUpdate(ev)
                                    transientMessage = if (outcome == RestoreOutcome.Ok) {
                                        successText
                                    } else {
                                        conflictText
                                    }
                                }
                            },
                        )
                    }
                }
            }

            if (transientMessage != null) {
                Snackbar(
                    message = transientMessage!!,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun HistoryRowCard(
    row: HistoryRow,
    onRestoreDeleted: (AuditEvent.Deleted) -> Unit,
    onRestoreUpdate: (AuditEvent.Updated) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember(row.event.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindBadge(row.event)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primaryTitleFor(event = row.event),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatTime(row.event.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                RestoreActionButton(
                    row = row,
                    onRestoreDeleted = onRestoreDeleted,
                    onRestoreUpdate = onRestoreUpdate,
                )
            }

            val canExpand = row.event is AuditEvent.Updated
            if (canExpand) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.history_hide_changes)
                        } else {
                            stringResource(R.string.history_show_changes)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    UpdateDiffPanel(event = row.event as AuditEvent.Updated)
                }
            }

            row.restore.blockedReason()?.let { reasonRes ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(reasonRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KindBadge(event: AuditEvent) {
    val (bg, fg, labelRes) = when (event) {
        is AuditEvent.Added -> Triple(Color(0xFF1B5E20), Color.White, R.string.history_kind_added)
        is AuditEvent.Updated -> Triple(Color(0xFF0B3A6F), Color.White, R.string.history_kind_updated)
        is AuditEvent.Deleted -> Triple(Color(0xFFC62828), Color.White, R.string.history_kind_deleted)
        is AuditEvent.Imported -> Triple(Color(0xFF455A64), Color.White, R.string.history_kind_imported)
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RestoreActionButton(
    row: HistoryRow,
    onRestoreDeleted: (AuditEvent.Deleted) -> Unit,
    onRestoreUpdate: (AuditEvent.Updated) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    when (row.restore) {
        RestoreState.Available -> when (val ev = row.event) {
            is AuditEvent.Deleted -> Button(
                onClick = { onRestoreDeleted(ev) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) { Text(stringResource(R.string.history_restore)) }
            is AuditEvent.Updated -> OutlinedButton(
                onClick = { onRestoreUpdate(ev) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) { Text(stringResource(R.string.history_undo_update)) }
            else -> Unit
        }
        is RestoreState.Unavailable -> {
            OutlinedButton(
                onClick = {},
                enabled = false,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, cs.outlineVariant),
            ) {
                Text(
                    text = stringResource(R.string.history_restore),
                    color = cs.onSurfaceVariant,
                )
            }
        }
        RestoreState.NotApplicable -> Unit
    }
}

@Composable
private fun UpdateDiffPanel(event: AuditEvent.Updated) {
    val diffs = remember(event) { diffBooks(event.before, event.after) }
    val cs = MaterialTheme.colorScheme
    if (diffs.isEmpty()) {
        Text(
            text = stringResource(R.string.history_no_field_changes),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        diffs.forEach { diff ->
            DiffRow(diff)
        }
    }
}

@Composable
private fun DiffRow(diff: FieldDiff) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(diff.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            DiffSide(
                modifier = Modifier.weight(1f),
                text = diff.before.ifBlank { "—" },
                strikethrough = true,
                accent = Color(0xFFC62828),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "→",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(10.dp))
            DiffSide(
                modifier = Modifier.weight(1f),
                text = diff.after.ifBlank { "—" },
                strikethrough = false,
                accent = Color(0xFF1B5E20),
            )
        }
    }
}

@Composable
private fun DiffSide(
    modifier: Modifier,
    text: String,
    strikethrough: Boolean,
    accent: Color,
) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = accent,
        textDecoration = if (strikethrough) {
            androidx.compose.ui.text.style.TextDecoration.LineThrough
        } else null,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Snackbar(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .padding(20.dp)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// --- support: formatting / diff / grouping -----------------------------------

@Composable
private fun primaryTitleFor(event: AuditEvent): String = when (event) {
    is AuditEvent.Added -> event.bookName.ifBlank { event.snapshot.name.ifBlank { "—" } }
    is AuditEvent.Updated -> event.bookName.ifBlank { event.after.name.ifBlank { "—" } }
    is AuditEvent.Deleted -> event.bookName.ifBlank { event.snapshot.name.ifBlank { "—" } }
    is AuditEvent.Imported -> stringResource(
        R.string.history_imported_title,
        event.importedCount,
    )
}

private data class FieldDiff(val labelRes: Int, val before: String, val after: String)

private fun diffBooks(before: Book, after: Book): List<FieldDiff> {
    val out = ArrayList<FieldDiff>(8)
    fun addIfChanged(labelRes: Int, b: String, a: String) {
        if (b != a) out += FieldDiff(labelRes, b, a)
    }
    addIfChanged(R.string.field_name, before.name, after.name)
    addIfChanged(R.string.field_writer, before.writer, after.writer)
    addIfChanged(R.string.field_book_number, before.bookNumber, after.bookNumber)
    addIfChanged(R.string.field_display_number, before.displayNumber, after.displayNumber)
    addIfChanged(R.string.field_letter, before.letter, after.letter)
    addIfChanged(R.string.field_color, before.color, after.color)
    addIfChanged(R.string.field_category, before.category, after.category)
    addIfChanged(R.string.field_topics, before.topics, after.topics)
    addIfChanged(R.string.field_notes, before.notes, after.notes)
    if (before.subcategories != after.subcategories) {
        out += FieldDiff(
            R.string.field_subcategories,
            before.subcategories.joinToString(", "),
            after.subcategories.joinToString(", "),
        )
    }
    if (before.relations != after.relations) {
        out += FieldDiff(
            R.string.field_relations,
            before.relations.joinToString(", "),
            after.relations.joinToString(", "),
        )
    }
    if (before.place != after.place) {
        out += FieldDiff(R.string.field_place, placeLabel(before.place), placeLabel(after.place))
    }
    if (before.state != after.state) {
        out += FieldDiff(R.string.field_state, stateLabel(before.state), stateLabel(after.state))
    }
    if (before.parentBookId != after.parentBookId || before.parentBookName != after.parentBookName) {
        out += FieldDiff(
            R.string.field_parent,
            parentSummary(before),
            parentSummary(after),
        )
    }
    return out
}

private fun parentSummary(book: Book): String =
    book.parentBookName.ifBlank { book.parentBookId.orEmpty() }.ifBlank { "—" }

private fun placeLabel(place: BookPlace): String = when (place) {
    BookPlace.OTZAR -> "אוצר הספרים"
    BookPlace.BEIS_MIDRASH -> "בית מדרש"
    BookPlace.OTHER -> "אחר"
    BookPlace.UNSPECIFIED -> "לא צוין"
}

private fun stateLabel(state: BookState): String = when (state) {
    BookState.AVAILABLE -> "זמין"
    BookState.UNAVAILABLE -> "לא זמין"
    BookState.IN_REPAIR -> "בתיקון"
}

private fun groupByDay(rows: List<HistoryRow>): List<Pair<String, List<HistoryRow>>> {
    if (rows.isEmpty()) return emptyList()
    val out = LinkedHashMap<String, MutableList<HistoryRow>>()
    for (r in rows) {
        val key = formatDay(r.event.timestamp)
        out.getOrPut(key) { ArrayList() } += r
    }
    return out.map { (k, v) -> k to v }
}

private val DAY_FMT: SimpleDateFormat by lazy {
    SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("he")).apply {
        timeZone = TimeZone.getDefault()
    }
}
private val TIME_FMT: SimpleDateFormat by lazy {
    SimpleDateFormat("HH:mm", Locale.forLanguageTag("he")).apply {
        timeZone = TimeZone.getDefault()
    }
}

private fun formatDay(ts: Long): String = DAY_FMT.format(Date(ts))
private fun formatTime(ts: Long): String = TIME_FMT.format(Date(ts))

private fun RestoreState.blockedReason(): Int? = when (this) {
    is RestoreState.Unavailable -> when (reason) {
        RestoreBlock.IdInUse -> R.string.history_block_id_in_use
        RestoreBlock.BookMissing -> R.string.history_block_missing
        RestoreBlock.ChangedSince -> R.string.history_block_changed_since
    }
    else -> null
}
