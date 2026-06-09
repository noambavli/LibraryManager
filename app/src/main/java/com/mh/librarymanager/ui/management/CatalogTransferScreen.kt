package com.mh.librarymanager.ui.management

import android.net.Uri
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.data.civ.CivCatalogIO
import com.mh.librarymanager.data.civ.CivExportMeta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CatalogTransferScreen(
    viewModel: CatalogTransferViewModel,
    session: ManagementSession,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showUndoConfirm by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        session.endExternalTask()
        if (uri != null) {
            pendingUri = uri
            viewModel.loadPreview(uri)
        }
    }

    val pickerMimes = remember { arrayOf("*/*") }
    val cs = MaterialTheme.colorScheme
    val isWorking = status is CatalogTransferViewModel.Status.Working

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
                VersionBadge(formatVersion = viewModel.formatVersion)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    InfoTile(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.catalog_transfer_tablet_title),
                        value = stringResource(
                            R.string.catalog_transfer_book_count,
                            dashboard.bookCount,
                        ),
                        subtitle = stringResource(
                            R.string.catalog_transfer_format_label,
                            viewModel.formatVersion,
                        ),
                        accent = cs.primary,
                    )
                    InfoTile(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.catalog_transfer_pc_title),
                        value = if (dashboard.lastImport.hasData) {
                            stringResource(
                                R.string.catalog_transfer_last_sync_added,
                                dashboard.lastImport.added,
                            )
                        } else {
                            stringResource(R.string.catalog_transfer_never_synced)
                        },
                        subtitle = lastSyncSubtitle(dashboard.lastImport),
                        accent = cs.tertiary,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                MergeExplainCard()

                Spacer(modifier = Modifier.height(16.dp))

                FileNamingCard(formatVersion = viewModel.formatVersion)

                Spacer(modifier = Modifier.height(20.dp))

                FlowStepsCard()

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    enabled = !isWorking,
                    onClick = {
                        session.beginExternalTask()
                        picker.launch(pickerMimes)
                    },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Text(
                        text = stringResource(R.string.catalog_transfer_pick_file),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (dashboard.hasBackup) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        enabled = !isWorking,
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
private fun VersionBadge(formatVersion: Int) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.secondaryContainer,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = stringResource(R.string.catalog_transfer_badge, formatVersion),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSecondaryContainer,
        )
    }
}

@Composable
private fun InfoTile(
    title: String,
    value: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = cs.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = accent)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MergeExplainCard() {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.primaryContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.catalog_transfer_merge_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.catalog_transfer_merge_body),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun FileNamingCard(formatVersion: Int) {
    val cs = MaterialTheme.colorScheme
    val example = stringResource(R.string.catalog_transfer_filename_example, formatVersion)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.catalog_transfer_filename_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.catalog_transfer_filename_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = cs.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, cs.outlineVariant),
            ) {
                Text(
                    text = example,
                    modifier = Modifier.padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun FlowStepsCard() {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
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
        CatalogTransferViewModel.Status.Working -> {
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
                    status.added,
                    status.skipped,
                    status.totalAfter,
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
    preview: CivCatalogIO.ImportPreview,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.catalog_transfer_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.catalog_transfer_confirm_preview,
                        preview.addedCount,
                        preview.skippedCount,
                        preview.currentCount,
                        preview.totalAfter,
                    ),
                )
                preview.meta?.let { MetaLine(it) }
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

@Composable
private fun MetaLine(meta: CivExportMeta) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = stringResource(
                R.string.catalog_transfer_file_meta,
                meta.displayLabel(),
            ),
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun lastSyncSubtitle(last: CivCatalogIO.LastImportSummary): String {
    if (!last.hasData) return stringResource(R.string.catalog_transfer_last_sync_none)
    val whenText = formatWhen(last.at)
    val source = last.sourceFile.takeIf { it.isNotBlank() }
    return if (source != null) {
        stringResource(R.string.catalog_transfer_last_sync_detail, whenText, source)
    } else {
        whenText
    }
}

private fun formatWhen(ms: Long): String {
    if (ms <= 0L) return ""
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))
}
