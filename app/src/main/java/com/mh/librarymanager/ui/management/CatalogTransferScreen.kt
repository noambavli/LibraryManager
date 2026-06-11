package com.mh.librarymanager.ui.management

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import com.mh.librarymanager.data.civ.CivCatalogIO

@Composable
fun CatalogTransferScreen(
    viewModel: CatalogTransferViewModel,
    session: ManagementSession,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenSummary: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val importHistory by viewModel.importHistory.collectAsStateWithLifecycle()

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showUndoConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteType by remember { mutableStateOf(false) }
    var showRestoreWipeConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshSummary()
        viewModel.openSummary.collect {
            viewModel.refreshSummary()
            onOpenSummary()
        }
    }

    LaunchedEffect(status, pendingUri, preview) {
        val inPickerFlow = pendingUri != null || preview != null || status is CatalogTransferViewModel.Status.Working
        if (!inPickerFlow && session.isExternalTaskActive()) {
            session.endExternalTask()
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            viewModel.loadPreview(uri)
        } else {
            session.endExternalTask()
        }
    }

    val isWorking = status is CatalogTransferViewModel.Status.Working
    val cs = MaterialTheme.colorScheme

    AppScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
            ManagementHeader(
                title = stringResource(R.string.catalog_transfer_title),
                onBack = onBack,
                onLogout = onLogout,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                SimpleInfoCard(
                    bookCount = dashboard.bookCount,
                    lastFile = dashboard.lastImport.let {
                        if (it.hasData && it.added > 0) {
                            stringResource(R.string.catalog_transfer_last_file, it.added)
                        } else {
                            stringResource(R.string.catalog_transfer_no_sync_yet)
                        }
                    },
                )

                if (importHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.catalog_transfer_import_history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    importHistory.forEach { entry ->
                        ImportHistoryCompactRow(
                            summary = entry,
                            onClick = {
                                viewModel.selectImport(entry.id)
                                onOpenSummary()
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.catalog_transfer_how_it_works),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.catalog_transfer_manual_fallback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    enabled = !isWorking,
                    onClick = {
                        session.beginExternalTask()
                        picker.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth().height(68.dp),
                ) {
                    Text(
                        text = stringResource(R.string.catalog_transfer_pick_file),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (dashboard.hasSummary) {
                    OutlinedButton(
                        onClick = {
                            viewModel.selectImport(null)
                            onOpenSummary()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(stringResource(R.string.catalog_transfer_view_summary))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (dashboard.hasBackup) {
                    OutlinedButton(
                        enabled = !isWorking,
                        onClick = { showUndoConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(stringResource(R.string.catalog_transfer_undo))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (dashboard.hasWipeBackup) {
                    OutlinedButton(
                        enabled = !isWorking,
                        onClick = { showRestoreWipeConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(stringResource(R.string.catalog_transfer_restore_wipe))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (dashboard.bookCount > 0) {
                    OutlinedButton(
                        enabled = !isWorking,
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.catalog_transfer_delete_all),
                            color = cs.error,
                        )
                    }
                }

                if (isWorking) {
                    Spacer(modifier = Modifier.height(20.dp))
                    RowWorking()
                }

                if (status is CatalogTransferViewModel.Status.Imported) {
                    val imported = status as CatalogTransferViewModel.Status.Imported
                    Spacer(modifier = Modifier.height(16.dp))
                    SuccessCard(
                        message = if (imported.added > 0) {
                            stringResource(
                                R.string.catalog_transfer_imported_ok,
                                imported.added,
                                imported.skipped,
                            )
                        } else {
                            stringResource(
                                R.string.catalog_transfer_imported_none,
                                imported.skipped,
                            )
                        },
                        onDismiss = { viewModel.dismissStatus() },
                    )
                }

                if (status is CatalogTransferViewModel.Status.Wiped) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SuccessCard(
                        message = stringResource(
                            R.string.catalog_transfer_wiped_ok,
                            (status as CatalogTransferViewModel.Status.Wiped).count,
                        ),
                        onDismiss = { viewModel.dismissStatus() },
                    )
                }

                if (status is CatalogTransferViewModel.Status.Restored) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SuccessCard(
                        message = stringResource(
                            R.string.catalog_transfer_restored_ok,
                            (status as CatalogTransferViewModel.Status.Restored).count,
                        ),
                        onDismiss = { viewModel.dismissStatus() },
                    )
                }

                if (status is CatalogTransferViewModel.Status.Error) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ErrorCard(
                        message = (status as CatalogTransferViewModel.Status.Error).message,
                        onDismiss = { viewModel.dismissStatus() },
                    )
                }
            }
        }
    }

    preview?.let { p ->
        if (pendingUri != null && !isWorking) {
            ImportConfirmDialog(
                preview = p,
                fileLabel = p.meta?.fileLabel() ?: "?",
                onCancel = {
                    pendingUri = null
                    viewModel.clearPreview()
                },
                onConfirm = {
                    val uri = pendingUri
                    pendingUri = null
                    viewModel.clearPreview()
                    if (uri != null) viewModel.importFromUri(uri)
                },
            )
        }
    }

    if (showUndoConfirm) {
        AlertDialog(
            onDismissRequest = { showUndoConfirm = false },
            title = { Text(stringResource(R.string.catalog_transfer_undo)) },
            text = { Text(stringResource(R.string.catalog_transfer_undo_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showUndoConfirm = false
                    viewModel.undoLastImport()
                }) { Text(stringResource(R.string.catalog_transfer_undo_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showUndoConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showRestoreWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreWipeConfirm = false },
            title = { Text(stringResource(R.string.catalog_transfer_restore_wipe)) },
            text = { Text(stringResource(R.string.catalog_transfer_restore_wipe_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreWipeConfirm = false
                    viewModel.restoreAfterWipe()
                }) { Text(stringResource(R.string.catalog_transfer_undo_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreWipeConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.catalog_transfer_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.catalog_transfer_delete_all_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    showDeleteType = true
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDeleteType) {
        TypedConfirmDialog(
            title = stringResource(R.string.catalog_transfer_delete_all_type_title),
            body = stringResource(R.string.catalog_transfer_delete_all_type_body),
            requiredPhrase = stringResource(R.string.catalog_transfer_delete_all_phrase),
            confirmLabel = stringResource(R.string.catalog_transfer_delete_all_ok),
            onDismiss = { showDeleteType = false },
            onConfirmed = {
                showDeleteType = false
                viewModel.deleteAllBooks()
            },
        )
    }
    }
}

@Composable
private fun ImportHistoryCompactRow(
    summary: CivCatalogIO.ImportSummaryDetail,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = cs.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(
                    R.string.catalog_transfer_import_history_item,
                    summary.fileLabel,
                    summary.added,
                    formatImportWhen(summary.at),
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (summary.sourceFile.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary.sourceFile,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

private val IMPORT_WHEN_FMT: java.text.SimpleDateFormat by lazy {
    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.forLanguageTag("he")).apply {
        timeZone = java.util.TimeZone.getDefault()
    }
}

private fun formatImportWhen(ms: Long): String =
    if (ms > 0L) IMPORT_WHEN_FMT.format(java.util.Date(ms)) else "—"

@Composable
private fun SimpleInfoCard(bookCount: Int, lastFile: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = stringResource(R.string.catalog_transfer_book_count, bookCount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(lastFile, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowWorking() {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cs.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.catalog_transfer_working))
        }
    }
}

@Composable
private fun SuccessCard(message: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.primaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(message, fontWeight = FontWeight.Medium)
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.catalog_transfer_dismiss))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.errorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.catalog_transfer_error_title),
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(message)
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.catalog_transfer_dismiss))
            }
        }
    }
}

