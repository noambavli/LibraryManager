package com.mh.librarymanager.ui.management

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.mh.librarymanager.data.backup.BackupState
import com.mh.librarymanager.data.backup.BackupTrigger
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ImportKind { Books, Matchings }

private enum class PendingDelete { Books, Shortcuts }

private data class PendingImport(val kind: ImportKind, val uri: Uri)

@Composable
fun WindowsToolScreen(
    viewModel: WindowsToolViewModel,
    session: ManagementSession,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val opStatus by viewModel.opStatus.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackup.collectAsStateWithLifecycle()

    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showRestoreConfirm by remember { mutableStateOf<Uri?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    val inExternalFlow = pendingImport != null ||
        showRestoreConfirm != null ||
        pendingDelete != null ||
        opStatus !is WindowsToolViewModel.OpStatus.Idle ||
        backupState is BackupState.Running

    LaunchedEffect(inExternalFlow) {
        if (!inExternalFlow && session.isExternalTaskActive()) {
            session.endExternalTask()
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshLastBackup() }
    LaunchedEffect(backupState) {
        if (backupState is BackupState.Done) viewModel.refreshLastBackup()
    }

    // Keep the kiosk from auto-logging-out while a picker is open or work runs.
    val busy = opStatus is WindowsToolViewModel.OpStatus.Working || backupState is BackupState.Running
    DisposableEffect(busy) {
        if (busy) session.beginExternalTask()
        onDispose { if (busy) session.endExternalTask() }
    }

    fun pick(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>, types: Array<String>) {
        session.beginExternalTask()
        launcher.launch(types)
    }

    val booksPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImport = PendingImport(ImportKind.Books, uri)
        } else {
            session.endExternalTask()
        }
    }
    val matchingsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImport = PendingImport(ImportKind.Matchings, uri)
        } else {
            session.endExternalTask()
        }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            showRestoreConfirm = uri
        } else {
            session.endExternalTask()
        }
    }

    val xlsxTypes = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/octet-stream",
        "*/*",
    )

    AppScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ManagementHeader(
                    title = stringResource(R.string.windows_tool_title),
                    onBack = onBack,
                    onLogout = onLogout,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                ) {
                    IntroCard()
                    Spacer(modifier = Modifier.height(16.dp))

                    BackupCard(
                        backupState = backupState,
                        lastBackupAt = lastBackup.at,
                        lastBackupName = lastBackup.name,
                        onBackupNow = { viewModel.createBackupNow() },
                        onDismissBackup = { viewModel.dismissBackup() },
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TransferSection(
                        title = stringResource(R.string.windows_tool_books_section),
                        format = stringResource(R.string.windows_tool_books_format),
                        onExport = { viewModel.exportBooks() },
                        onImport = { pick(booksPicker, xlsxTypes) },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TransferSection(
                        title = stringResource(R.string.windows_tool_matchings_section),
                        format = stringResource(R.string.windows_tool_matchings_format),
                        onExport = { viewModel.exportMatchings() },
                        onImport = { pick(matchingsPicker, xlsxTypes) },
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    RestoreCard(onRestore = { pick(restorePicker, arrayOf("application/zip", "application/octet-stream", "*/*")) })

                    Spacer(modifier = Modifier.height(16.dp))

                    DangerZoneCard(
                        onDeleteAllBooks = { pendingDelete = PendingDelete.Books },
                        onDeleteAllShortcuts = { pendingDelete = PendingDelete.Shortcuts },
                    )

                    Spacer(modifier = Modifier.height(28.dp))
                }

                when (val status = opStatus) {
                    is WindowsToolViewModel.OpStatus.Working -> {
                        WorkingCard(
                            message = status.message,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                        )
                    }
                    is WindowsToolViewModel.OpStatus.Success -> {
                        FeedbackCard(
                            message = status.message,
                            isError = false,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                            onDismiss = { viewModel.dismissStatus() },
                        )
                    }
                    is WindowsToolViewModel.OpStatus.Error -> {
                        FeedbackCard(
                            message = status.message,
                            isError = true,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                            onDismiss = { viewModel.dismissStatus() },
                        )
                    }
                    WindowsToolViewModel.OpStatus.Idle -> Unit
                }
            }
        }
    }

    pendingImport?.let { request ->
        val body = when (request.kind) {
            ImportKind.Books -> stringResource(R.string.windows_tool_import_books_confirm)
            ImportKind.Matchings -> stringResource(R.string.windows_tool_import_matchings_confirm)
        }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.windows_tool_import_confirm_title)) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    when (request.kind) {
                        ImportKind.Books -> viewModel.importBooks(request.uri)
                        ImportKind.Matchings -> viewModel.importMatchings(request.uri)
                    }
                    pendingImport = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    showRestoreConfirm?.let { uri ->
        TypedConfirmDialog(
            title = stringResource(R.string.windows_tool_restore_confirm_title),
            body = stringResource(R.string.windows_tool_restore_confirm_body),
            requiredPhrase = stringResource(R.string.windows_tool_restore_phrase),
            confirmLabel = stringResource(R.string.windows_tool_restore_ok),
            onDismiss = { showRestoreConfirm = null },
            onConfirmed = {
                showRestoreConfirm = null
                viewModel.restoreFromZip(uri)
            },
        )
    }

    when (pendingDelete) {
        PendingDelete.Books -> TypedConfirmDialog(
            title = stringResource(R.string.windows_tool_delete_books_confirm_title),
            body = stringResource(R.string.windows_tool_delete_books_confirm_body),
            requiredPhrase = stringResource(R.string.windows_tool_delete_books_phrase),
            confirmLabel = stringResource(R.string.windows_tool_delete_books_ok),
            onDismiss = { pendingDelete = null },
            onConfirmed = {
                pendingDelete = null
                viewModel.deleteAllBooks()
            },
        )
        PendingDelete.Shortcuts -> TypedConfirmDialog(
            title = stringResource(R.string.windows_tool_delete_shortcuts_confirm_title),
            body = stringResource(R.string.windows_tool_delete_shortcuts_confirm_body),
            requiredPhrase = stringResource(R.string.windows_tool_delete_shortcuts_phrase),
            confirmLabel = stringResource(R.string.windows_tool_delete_shortcuts_ok),
            onDismiss = { pendingDelete = null },
            onConfirmed = {
                pendingDelete = null
                viewModel.deleteAllShortcuts()
            },
        )
        null -> Unit
    }
}

@Composable
private fun IntroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = stringResource(R.string.windows_tool_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.windows_tool_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun BackupCard(
    backupState: BackupState,
    lastBackupAt: Long,
    lastBackupName: String,
    onBackupNow: () -> Unit,
    onDismissBackup: () -> Unit,
) {
    val anyRunning = backupState is BackupState.Running
    val runningState = (backupState as? BackupState.Running)?.takeIf { it.trigger == BackupTrigger.Manual }
    val doneState = (backupState as? BackupState.Done)?.takeIf { it.trigger != BackupTrigger.Usb }
    val failedState = (backupState as? BackupState.Failed)?.takeIf { it.trigger != BackupTrigger.Usb }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Panel,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.windows_tool_backup_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.windows_tool_backup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (lastBackupAt > 0L) {
                    stringResource(R.string.windows_tool_backup_last, formatStamp(lastBackupAt))
                } else {
                    stringResource(R.string.windows_tool_backup_never)
                },
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.TextMuted,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onBackupNow,
                enabled = !anyRunning,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.windows_tool_backup_now),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (runningState != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp).padding(end = 12.dp))
                    Text(runningState.step, style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Surface non-USB backup outcomes here (USB ones show a global overlay).
            if (doneState != null) {
                Spacer(modifier = Modifier.height(12.dp))
                FeedbackInline(
                    message = backupDoneLabel(doneState.fileName, doneState.location),
                    isError = false,
                    onDismiss = onDismissBackup,
                )
            }
            if (failedState != null) {
                Spacer(modifier = Modifier.height(12.dp))
                FeedbackInline(
                    message = stringResource(R.string.windows_tool_backup_failed, failedState.message),
                    isError = true,
                    onDismiss = onDismissBackup,
                )
            }
        }
    }
}

@Composable
private fun TransferSection(
    title: String,
    format: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Panel,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = format,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onExport,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.windows_tool_export)) }
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.windows_tool_import)) }
            }
        }
    }
}

@Composable
private fun DangerZoneCard(
    onDeleteAllBooks: () -> Unit,
    onDeleteAllShortcuts: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.errorContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, cs.error.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.windows_tool_danger_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.error,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.windows_tool_danger_desc),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDeleteAllBooks,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                    border = BorderStroke(1.dp, cs.error),
                ) { Text(stringResource(R.string.windows_tool_delete_all_books)) }
                OutlinedButton(
                    onClick = onDeleteAllShortcuts,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                    border = BorderStroke(1.dp, cs.error),
                ) { Text(stringResource(R.string.windows_tool_delete_all_shortcuts)) }
            }
        }
    }
}

@Composable
private fun RestoreCard(onRestore: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Panel,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.windows_tool_restore_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.windows_tool_restore_desc),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted,
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.windows_tool_restore_button)) }
        }
    }
}

@Composable
private fun WorkingCard(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.height(24.dp).padding(end = 14.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun FeedbackCard(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isError) cs.errorContainer else cs.primaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(message, fontWeight = FontWeight.Medium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.windows_tool_dismiss)) }
        }
    }
}

@Composable
private fun FeedbackInline(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) cs.errorContainer else cs.primaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.windows_tool_dismiss)) }
        }
    }
}

private val STAMP: SimpleDateFormat by lazy {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("he"))
}

private fun formatStamp(ms: Long): String = STAMP.format(Date(ms))

@Composable
private fun backupDoneLabel(fileName: String, location: String): String =
    if (location.isNotBlank() && location != "Download") {
        "$fileName\n$location"
    } else {
        stringResource(R.string.windows_tool_backup_done, fileName)
    }
