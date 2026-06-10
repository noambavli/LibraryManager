package com.mh.librarymanager.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import com.mh.librarymanager.data.civ.CivCatalogIO
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImportSummaryScreen(
    viewModel: CatalogTransferViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val summary by viewModel.importSummary.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    AppScreenBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            ManagementHeader(
                title = stringResource(R.string.import_summary_title),
                onBack = onBack,
                onLogout = onLogout,
            )

            if (!summary.hasData) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.import_summary_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text(stringResource(R.string.import_summary_back_sync))
                }
            }
                return@Column
            }

            LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SummaryHeaderCard(summary = summary)
            }

            if (summary.added > 0) {
                item {
                    Text(
                        text = stringResource(R.string.import_summary_books_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                itemsIndexed(summary.addedBooks.take(summary.displayLimit)) { index, book ->
                    BookLineCard(index = index + 1, name = book.name, writer = book.writer)
                }
                if (summary.hasMoreBooks) {
                    item {
                        Text(
                            text = stringResource(
                                R.string.import_summary_more_books,
                                summary.added - summary.displayLimit,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = cs.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.import_summary_none_added),
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(stringResource(R.string.import_summary_done))
                }
            }
            }
        }
    }
}

@Composable
private fun SummaryHeaderCard(summary: CivCatalogIO.ImportSummaryDetail) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.primaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = stringResource(R.string.import_summary_file_chosen, summary.fileLabel),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (summary.sourceFile.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.import_summary_excel_source, summary.sourceFile),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.import_summary_counts,
                    summary.added,
                    summary.skipped,
                    summary.totalAfter,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (summary.at > 0L) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.import_summary_when, formatWhen(summary.at)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun BookLineCard(index: Int, name: String, writer: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surface,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "$index. ${name.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (writer.isNotBlank()) {
                Text(
                    text = writer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

private val WHEN_FMT: SimpleDateFormat by lazy {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("he")).apply {
        timeZone = java.util.TimeZone.getDefault()
    }
}

private fun formatWhen(ms: Long): String = WHEN_FMT.format(Date(ms))
