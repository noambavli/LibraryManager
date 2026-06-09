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

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showUndoConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
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

    Box(modifier = Modifier.fillMaxSize().background(cs.background)) {
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

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.catalog_transfer_merge_short),
                    style = MaterialTheme.typography.bodyLarge,
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
                        onClick = onOpenSummary,
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
}

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

@Composable
private fun ImportConfirmDialog(
    preview: CivCatalogIO.ImportPreview,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val fileLabel = preview.meta?.fileLabel() ?: "?"
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.catalog_transfer_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.catalog_transfer_chosen_file, fileLabel))
                Text(
                    stringResource(
                        R.string.catalog_transfer_confirm_preview,
                        preview.addedCount,
                        preview.skippedCount,
                        preview.currentCount,
                        preview.totalAfter,
                    ),
                )
                Text(
                    text = stringResource(R.string.catalog_transfer_confirm_safety),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.catalog_transfer_confirm_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}
