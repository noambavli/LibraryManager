package com.mh.librarymanager.ui.management

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.data.backup.BackupState
import com.mh.librarymanager.data.backup.BackupTrigger
import com.mh.librarymanager.domain.HomeOverviewMapKind
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ImportKind { Books, Beis, Matchings }

private enum class PendingDelete { Books, Beis, Matchings }

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
    val pendingHomeMap by viewModel.pendingHomeMap.collectAsStateWithLifecycle()
    val homeMapConfirming by viewModel.homeMapConfirming.collectAsStateWithLifecycle()
    val homeMapRevision by viewModel.homeMapRevision.collectAsStateWithLifecycle()
    val lastExport by viewModel.lastExport.collectAsStateWithLifecycle()

    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showRestoreConfirm by remember { mutableStateOf<Uri?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    val pendingDownloadActive by viewModel.pendingDownload.collectAsStateWithLifecycle()

    val inExternalFlow = pendingImport != null ||
        pendingHomeMap != null ||
        showRestoreConfirm != null ||
        pendingDelete != null ||
        pendingDownloadActive != null ||
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

    // Safety net: if the screen is left (e.g. hardware back while a picker
    // confirm dialog is open) release any external-task hold started here, so
    // idle auto-logout can never get stuck permanently off.
    DisposableEffect(Unit) {
        onDispose { if (session.isExternalTaskActive()) session.endExternalTask() }
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
    val beisPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImport = PendingImport(ImportKind.Beis, uri)
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
    val otzarMapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.stageHomeMapUpload(HomeOverviewMapKind.OTZAR, uri)
        } else {
            session.endExternalTask()
        }
    }
    val beisMidrashMapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.stageHomeMapUpload(HomeOverviewMapKind.BEIS_MIDRASH, uri)
        } else {
            session.endExternalTask()
        }
    }

    val xlsxTypes = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/octet-stream",
        "*/*",
    )

    val exportSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ),
    ) { uri ->
        if (uri != null) {
            viewModel.saveExportTo(uri)
        } else {
            viewModel.cancelPendingDownload()
            session.endExternalTask()
        }
    }
    LaunchedEffect(pendingDownloadActive) {
        val pd = pendingDownloadActive
        if (pd != null) {
            session.beginExternalTask()
            exportSaver.launch(pd.fileName)
        }
    }

    val imageTypes = arrayOf("image/png", "image/jpeg", "image/jpg", "image/webp", "image/*", "*/*")

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

                    HomeOverviewMapsSection(
                        hasOtzarMap = remember(homeMapRevision) {
                            viewModel.hasHomeMap(HomeOverviewMapKind.OTZAR)
                        },
                        hasBeisMidrashMap = remember(homeMapRevision) {
                            viewModel.hasHomeMap(HomeOverviewMapKind.BEIS_MIDRASH)
                        },
                        onUploadOtzar = { pick(otzarMapPicker, imageTypes) },
                        onUploadBeisMidrash = { pick(beisMidrashMapPicker, imageTypes) },
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ExportToPcSection(
                        lastExport = lastExport,
                        onExportBooks = { viewModel.exportBooks() },
                        onExportBeis = { viewModel.exportBeis() },
                        onExportMatchings = { viewModel.exportMatchings() },
                        onDismissExport = { viewModel.dismissExport() },
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LocalImportSection(
                        onImportBooks = { pick(booksPicker, xlsxTypes) },
                        onImportBeis = { pick(beisPicker, xlsxTypes) },
                        onImportMatchings = { pick(matchingsPicker, xlsxTypes) },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    RestoreCard(onRestore = { pick(restorePicker, arrayOf("application/zip", "application/octet-stream", "*/*")) })

                    Spacer(modifier = Modifier.height(16.dp))

                    DangerZoneCard(
                        onDeleteAllBooks = { pendingDelete = PendingDelete.Books },
                        onDeleteAllBeis = { pendingDelete = PendingDelete.Beis },
                        onDeleteAllMatchings = { pendingDelete = PendingDelete.Matchings },
                    )

                    when (val status = opStatus) {
                        is WindowsToolViewModel.OpStatus.Working -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            WorkingCard(message = status.message)
                        }
                        is WindowsToolViewModel.OpStatus.Success -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            FeedbackCard(
                                message = status.message,
                                isError = false,
                                onDismiss = { viewModel.dismissStatus() },
                            )
                        }
                        is WindowsToolViewModel.OpStatus.Error -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            FeedbackCard(
                                message = status.message,
                                isError = true,
                                onDismiss = { viewModel.dismissStatus() },
                            )
                        }
                        WindowsToolViewModel.OpStatus.Idle -> Unit
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }

    pendingImport?.let { request ->
        val body = when (request.kind) {
            ImportKind.Books -> stringResource(R.string.windows_tool_import_books_confirm)
            ImportKind.Beis -> stringResource(R.string.windows_tool_import_beis_confirm)
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
                        ImportKind.Beis -> viewModel.importBeis(request.uri)
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
        PendingDelete.Beis -> TypedConfirmDialog(
            title = stringResource(R.string.windows_tool_delete_beis_confirm_title),
            body = stringResource(R.string.windows_tool_delete_beis_confirm_body),
            requiredPhrase = stringResource(R.string.windows_tool_delete_beis_phrase),
            confirmLabel = stringResource(R.string.windows_tool_delete_beis_ok),
            onDismiss = { pendingDelete = null },
            onConfirmed = {
                pendingDelete = null
                viewModel.deleteAllBeis()
            },
        )
        PendingDelete.Matchings -> TypedConfirmDialog(
            title = stringResource(R.string.windows_tool_delete_matchings_confirm_title),
            body = stringResource(R.string.windows_tool_delete_matchings_confirm_body),
            requiredPhrase = stringResource(R.string.windows_tool_delete_matchings_phrase),
            confirmLabel = stringResource(R.string.windows_tool_delete_matchings_ok),
            onDismiss = { pendingDelete = null },
            onConfirmed = {
                pendingDelete = null
                viewModel.deleteAllMatchings()
            },
        )
        null -> Unit
    }

    pendingHomeMap?.let { pending ->
        HomeOverviewMapImportDialog(
            kind = pending.kind,
            preview = pending.preview,
            onDismiss = { viewModel.cancelHomeMapUpload() },
            onConfirm = { viewModel.confirmHomeMapUpload() },
            confirmEnabled = !homeMapConfirming,
        )
    }
}

@Composable
private fun IntroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, AppColors.BorderLight),
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
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
        }
    }
}

/**
 * Unified card used by every Windows-Tool section: a colored accent bar, a
 * title, an action-focused subtitle, then the section's controls. Keeping one
 * component guarantees identical spacing, radius, and typography everywhere.
 */
@Composable
private fun ToolSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.Accent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, AppColors.BorderLight),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            content()
        }
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
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
    ToolSection(
        title = stringResource(R.string.windows_tool_backup_section),
        subtitle = stringResource(R.string.windows_tool_backup_desc),
    ) {
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
        PrimaryActionButton(
            text = stringResource(R.string.windows_tool_backup_now),
            onClick = onBackupNow,
            enabled = !anyRunning,
            modifier = Modifier.fillMaxWidth(),
        )

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

@Composable
private fun HomeOverviewMapsSection(
    hasOtzarMap: Boolean,
    hasBeisMidrashMap: Boolean,
    onUploadOtzar: () -> Unit,
    onUploadBeisMidrash: () -> Unit,
) {
    ToolSection(
        title = stringResource(R.string.windows_tool_home_maps_section),
        subtitle = stringResource(R.string.windows_tool_home_maps_desc),
    ) {
        HomeOverviewMapUploadRow(
            title = stringResource(R.string.home_map_otzar),
            hasMap = hasOtzarMap,
            onUpload = onUploadOtzar,
        )
        Spacer(modifier = Modifier.height(10.dp))
        HomeOverviewMapUploadRow(
            title = stringResource(R.string.home_map_beis_midrash),
            hasMap = hasBeisMidrashMap,
            onUpload = onUploadBeisMidrash,
        )
    }
}

@Composable
private fun HomeOverviewMapUploadRow(
    title: String,
    hasMap: Boolean,
    onUpload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary,
            )
            Text(
                text = if (hasMap) {
                    stringResource(R.string.windows_tool_home_map_uploaded)
                } else {
                    stringResource(R.string.windows_tool_home_map_not_uploaded)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (hasMap) AppColors.Accent else AppColors.TextMuted,
            )
        }
        SecondaryActionButton(
            text = stringResource(R.string.windows_tool_home_map_upload),
            onClick = onUpload,
        )
    }
}

@Composable
private fun ExportToPcSection(
    lastExport: WindowsToolViewModel.LastExport?,
    onExportBooks: () -> Unit,
    onExportBeis: () -> Unit,
    onExportMatchings: () -> Unit,
    onDismissExport: () -> Unit,
) {
    ToolSection(
        title = stringResource(R.string.windows_tool_export_section_title),
        subtitle = stringResource(R.string.windows_tool_export_section_subtitle),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportStepRow("1", stringResource(R.string.windows_tool_export_step_tap))
            ExportStepRow("2", stringResource(R.string.windows_tool_export_step_save))
            ExportStepRow("3", stringResource(R.string.windows_tool_export_step_copy))
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PrimaryActionButton(
                    text = "\u2193  " + stringResource(R.string.windows_tool_export_books_btn),
                    onClick = onExportBooks,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.windows_tool_export_books_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                PrimaryActionButton(
                    text = "\u2193  " + stringResource(R.string.windows_tool_export_beis_btn),
                    onClick = onExportBeis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.windows_tool_export_beis_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PrimaryActionButton(
                    text = "\u2193  " + stringResource(R.string.windows_tool_export_matchings_btn),
                    onClick = onExportMatchings,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.windows_tool_export_matchings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        if (lastExport != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ExportResultCard(export = lastExport, onDismiss = onDismissExport)
        }
    }
}

@Composable
private fun ExportStepRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = RoundedCornerShape(12.dp),
            color = AppColors.Accent.copy(alpha = 0.16f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Accent,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LocalImportSection(
    onImportBooks: () -> Unit,
    onImportBeis: () -> Unit,
    onImportMatchings: () -> Unit,
) {
    ToolSection(
        title = stringResource(R.string.windows_tool_local_import_section),
        subtitle = stringResource(R.string.windows_tool_local_import_note),
    ) {
        LocalImportRow(
            title = stringResource(R.string.windows_tool_books_section),
            hint = stringResource(R.string.windows_tool_books_format),
            onImport = onImportBooks,
        )
        Spacer(modifier = Modifier.height(14.dp))
        LocalImportRow(
            title = stringResource(R.string.windows_tool_beis_section),
            hint = stringResource(R.string.windows_tool_beis_format),
            onImport = onImportBeis,
        )
        Spacer(modifier = Modifier.height(14.dp))
        LocalImportRow(
            title = stringResource(R.string.windows_tool_matchings_section),
            hint = stringResource(R.string.windows_tool_matchings_format),
            onImport = onImportMatchings,
        )
    }
}

@Composable
private fun LocalImportRow(
    title: String,
    hint: String,
    onImport: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.BorderLight),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryActionButton(
                text = stringResource(R.string.windows_tool_import),
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExportResultCard(
    export: WindowsToolViewModel.LastExport,
    onDismiss: () -> Unit,
) {
    val titleRes = when (export.kind) {
        WindowsToolViewModel.ExportKind.Books -> R.string.windows_tool_export_result_title_books
        WindowsToolViewModel.ExportKind.Beis -> R.string.windows_tool_export_result_title_beis
        WindowsToolViewModel.ExportKind.Matchings -> R.string.windows_tool_export_result_title_matchings
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.windows_tool_export_result_done),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AppColors.Accent,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.windows_tool_export_result_file, export.fileName),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (export.isEmpty) {
                    stringResource(R.string.windows_tool_export_result_empty)
                } else {
                    stringResource(R.string.windows_tool_export_result_rows, export.rowCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.windows_tool_export_result_where),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.windows_tool_export_result_steps, export.fileName),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            if (export.absolutePath.isNotBlank() && export.absolutePath.contains('/')) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.windows_tool_export_result_path, export.absolutePath),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.windows_tool_dismiss))
            }
        }
    }
}

@Composable
private fun DangerZoneCard(
    onDeleteAllBooks: () -> Unit,
    onDeleteAllBeis: () -> Unit,
    onDeleteAllMatchings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ToolSection(
        title = stringResource(R.string.windows_tool_danger_section),
        subtitle = stringResource(R.string.windows_tool_danger_desc),
        accent = cs.error,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DangerButton(
                text = stringResource(R.string.windows_tool_delete_all_books),
                onClick = onDeleteAllBooks,
            )
            DangerButton(
                text = stringResource(R.string.windows_tool_delete_all_beis),
                onClick = onDeleteAllBeis,
            )
            DangerButton(
                text = stringResource(R.string.windows_tool_delete_all_matchings),
                onClick = onDeleteAllMatchings,
            )
        }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
        border = BorderStroke(1.dp, cs.error),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RestoreCard(onRestore: () -> Unit) {
    ToolSection(
        title = stringResource(R.string.windows_tool_restore_section),
        subtitle = stringResource(R.string.windows_tool_restore_desc),
    ) {
        SecondaryActionButton(
            text = stringResource(R.string.windows_tool_restore_button),
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth(),
        )
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
