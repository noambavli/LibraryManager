package com.mh.librarymanager.ui.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mh.librarymanager.R
import com.mh.librarymanager.data.excel.MatchingsImportIO

@Composable
fun MatchingsImportConfirmDialog(
    preview: MatchingsImportIO.ImportPreview,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
        title = { Text(stringResource(R.string.matchings_import_adb_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview.fileLabel()?.let { label ->
                    Text(stringResource(R.string.excel_import_chosen_file, label))
                }
                Text(
                    stringResource(
                        R.string.matchings_import_confirm_preview,
                        preview.addedCount,
                        preview.updatedCount,
                        preview.unchangedCount,
                        preview.currentCount,
                        preview.totalAfter,
                    ),
                )
                Text(
                    text = stringResource(R.string.excel_import_confirm_safety),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = confirmEnabled) {
                Text(stringResource(R.string.matchings_import_confirm_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = confirmEnabled) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
