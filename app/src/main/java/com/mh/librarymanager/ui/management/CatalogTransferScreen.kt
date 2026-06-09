package com.mh.librarymanager.ui.management

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R

/**
 * Management → "Import catalog from PC".
 *
 * The whole flow is intentionally minimal so it's hard to misuse:
 *   1. Big primary button → opens Android's Storage Access Framework picker
 *      filtered to .civ files (also visible if user goes broader).
 *   2. Confirm dialog states the current count and what the import will do.
 *   3. Progress + result shown inline. A backup is always taken first, so an
 *      "Undo last import" button restores the previous catalog with one tap.
 */
@Composable
fun CatalogTransferScreen(
    viewModel: CatalogTransferViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val hasBackup by viewModel.hasBackup.collectAsStateWithLifecycle()
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()

    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showUndoConfirm by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) pendingUri = uri
    }

    // We accept "any file" so the SAF picker always shows .civ files (which
    // lack a registered MIME type). The actual format is validated as soon as
    // we read it, so picking the wrong file just produces a clear error.
    val pickerMimes = remember { arrayOf("*/*") }

    val cs = MaterialTheme.colorScheme
    val isImporting = status is CatalogTransferViewModel.Status.Importing

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
                StatusCard(bookCount = bookCount)

                Spacer(modifier = Modifier.height(20.dp))

                InstructionCard()

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    enabled = !isImporting,
                    onClick = { picker.launch(pickerMimes) },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Text(
                        text = stringResource(R.string.catalog_transfer_pick_file),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (hasBackup) {
                    OutlinedButton(
                        enabled = !isImporting,
                        onClick = { showUndoConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.catalog_transfer_undo),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                StatusBanner(status = status, onDismiss = { viewModel.dismissStatus() })
            }
        }
    }

    if (pendingUri != null && !isImporting) {
        ImportConfirmDialog(
            currentCount = bookCount,
            onCancel = { pendingUri = null },
            onConfirm = {
                val uri = pendingUri
                pendingUri = null
                if (uri != null) viewModel.importFromUri(uri)
            },
        )
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
private fun StatusCard(bookCount: Int) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u05E1\u05E4\u05E8", color = cs.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.catalog_transfer_current_status),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.catalog_transfer_book_count, bookCount),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun InstructionCard() {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.catalog_transfer_how_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Step(num = "1", text = stringResource(R.string.catalog_transfer_step1))
            Step(num = "2", text = stringResource(R.string.catalog_transfer_step2))
            Step(num = "3", text = stringResource(R.string.catalog_transfer_step3))
        }
    }
}

@Composable
private fun Step(num: String, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(cs.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(num, color = cs.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun StatusBanner(
    status: CatalogTransferViewModel.Status,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    when (status) {
        CatalogTransferViewModel.Status.Idle -> Unit
        CatalogTransferViewModel.Status.Importing -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = cs.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.catalog_transfer_working))
                }
            }
        }
        is CatalogTransferViewModel.Status.Imported -> {
            ResultBanner(
                color = cs.primaryContainer,
                title = stringResource(R.string.catalog_transfer_done_title),
                body = stringResource(
                    R.string.catalog_transfer_done_body,
                    status.count,
                    status.previousCount,
                ),
                onDismiss = onDismiss,
            )
        }
        is CatalogTransferViewModel.Status.Restored -> {
            ResultBanner(
                color = cs.tertiaryContainer,
                title = stringResource(R.string.catalog_transfer_restored_title),
                body = stringResource(R.string.catalog_transfer_restored_body, status.count),
                onDismiss = onDismiss,
            )
        }
        is CatalogTransferViewModel.Status.Error -> {
            ResultBanner(
                color = cs.errorContainer,
                title = stringResource(R.string.catalog_transfer_error_title),
                body = status.message,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun ResultBanner(
    color: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.catalog_transfer_dismiss))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ImportConfirmDialog(
    currentCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.catalog_transfer_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.catalog_transfer_confirm_body, currentCount))
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
