package com.mh.librarymanager.ui.management

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.data.storage.SandboxStorage
import com.mh.librarymanager.data.storage.StorageEntry
import com.mh.librarymanager.data.storage.StorageZone
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppManagementTile
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader

@Composable
fun StorageBrowserScreen(
    viewModel: StorageBrowserViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val location by viewModel.location.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    fun handleBack() {
        if (!viewModel.navigateUp()) onBack()
    }

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ManagementHeader(
                title = stringResource(R.string.storage_browser_title),
                onBack = { handleBack() },
                onLogout = onLogout,
            )

            if (location == null) {
                ZonePicker(
                    zones = zones,
                    onOpenZone = { viewModel.openZone(it) },
                )
            } else {
                StorageEntryList(
                    breadcrumb = viewModel.breadcrumb(),
                    entries = entries,
                    selected = selected,
                    isWorking = isWorking,
                    onOpenFolder = { viewModel.openFolder(it) },
                    onToggleSelection = { viewModel.toggleSelection(it) },
                    onSelectAll = { viewModel.selectAllDeletable() },
                    onClearSelection = { viewModel.clearSelection() },
                    onDeleteSelected = { showBulkDeleteConfirm = true },
                )
            }
        }
    }

    feedback?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissFeedback() },
            title = { Text(stringResource(R.string.storage_browser_feedback_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissFeedback() }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }

    if (showBulkDeleteConfirm) {
        val count = selected.size
        val totalSize = SandboxStorage.formatSize(viewModel.selectedTotalBytes())
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text(stringResource(R.string.storage_browser_bulk_delete_title)) },
            text = {
                Text(stringResource(R.string.storage_browser_bulk_delete_body, count, totalSize))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteConfirm = false
                        viewModel.deleteSelected()
                    },
                    enabled = !isWorking && count > 0,
                ) {
                    Text(stringResource(R.string.storage_browser_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ZonePicker(
    zones: List<com.mh.librarymanager.data.storage.ZoneInfo>,
    onOpenZone: (StorageZone) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp, start = 28.dp, end = 28.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.storage_browser_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        items(zones.filter { it.available }) { zone ->
            AppManagementTile(
                title = zone.label,
                subtitle = zone.description,
                accent = AppColors.Accent,
                onClick = { onOpenZone(zone.zone) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.storage_browser_sandbox_note),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted,
            )
        }
    }
}

@Composable
private fun StorageEntryList(
    breadcrumb: String,
    entries: List<StorageEntry>,
    selected: Set<String>,
    isWorking: Boolean,
    onOpenFolder: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    val deletableCount = entries.count { it.deletable && !it.isDirectory }
    val selectedCount = selected.size

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            color = AppColors.Panel,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = breadcrumb,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (deletableCount > 0) {
            SelectionToolbar(
                deletableCount = deletableCount,
                selectedCount = selectedCount,
                isWorking = isWorking,
                onSelectAll = onSelectAll,
                onClearSelection = onClearSelection,
                onDeleteSelected = onDeleteSelected,
            )
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.storage_browser_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextMuted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            ) {
                items(entries, key = { it.name }) { entry ->
                    StorageEntryRow(
                        entry = entry,
                        selected = entry.name in selected,
                        isWorking = isWorking,
                        onOpen = { if (entry.isDirectory) onOpenFolder(entry.name) },
                        onToggleSelection = { onToggleSelection(entry.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionToolbar(
    deletableCount: Int,
    selectedCount: Int,
    isWorking: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onSelectAll,
                enabled = !isWorking && selectedCount < deletableCount,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.storage_browser_select_all))
            }
            if (selectedCount > 0) {
                OutlinedButton(
                    onClick = onClearSelection,
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.storage_browser_clear_selection))
                }
            }
        }
        if (selectedCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDeleteSelected,
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.storage_browser_delete_selected, selectedCount))
            }
        }
    }
}

@Composable
private fun StorageEntryRow(
    entry: StorageEntry,
    selected: Boolean,
    isWorking: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val selectable = entry.deletable && !entry.isDirectory
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (selectable) {
                    Modifier.clickable(enabled = !isWorking) { onToggleSelection() }
                } else {
                    Modifier
                },
            ),
        color = if (selected) AppColors.Panel else AppColors.PanelElevated,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, AppColors.Accent)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectable) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() },
                    enabled = !isWorking,
                )
            } else {
                Spacer(modifier = Modifier.padding(start = 12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (entry.isDirectory) "📁 ${entry.name}" else entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (entry.isDirectory) {
                        stringResource(R.string.storage_browser_folder)
                    } else {
                        "${SandboxStorage.formatSize(entry.sizeBytes)} · ${SandboxStorage.formatDate(entry.modifiedAt)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
                if (!entry.deletable && entry.protectedReason != null && !entry.isDirectory) {
                    Text(
                        text = entry.protectedReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Warning,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (entry.isDirectory) {
                OutlinedButton(onClick = onOpen, enabled = !isWorking) {
                    Text(stringResource(R.string.storage_browser_open))
                }
            }
        }
    }
}
