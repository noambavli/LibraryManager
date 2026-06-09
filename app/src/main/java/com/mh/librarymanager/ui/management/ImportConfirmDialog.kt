package com.mh.librarymanager.ui.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mh.librarymanager.R
import com.mh.librarymanager.data.civ.CivCatalogIO

@Composable
fun ImportConfirmDialog(
    preview: CivCatalogIO.ImportPreview,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    title: String = stringResource(R.string.catalog_transfer_confirm_title),
    fileLabel: String? = preview.meta?.fileLabel(),
    showSafetyNote: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fileLabel != null) {
                    Text(stringResource(R.string.catalog_transfer_chosen_file, fileLabel))
                }
                Text(
                    stringResource(
                        R.string.catalog_transfer_confirm_preview,
                        preview.addedCount,
                        preview.skippedCount,
                        preview.currentCount,
                        preview.totalAfter,
                    ),
                )
                if (showSafetyNote) {
                    Text(
                        text = stringResource(R.string.catalog_transfer_confirm_safety),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
