package com.mh.librarymanager.ui.management

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.data.homemap.HomeOverviewMapProcessor
import com.mh.librarymanager.domain.HomeOverviewMapKind

@Composable
fun HomeOverviewMapImportDialog(
    kind: HomeOverviewMapKind,
    preview: HomeOverviewMapProcessor.Preview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
) {
    val imageBitmap = remember(preview.pngBytes) {
        BitmapFactory.decodeByteArray(preview.pngBytes, 0, preview.pngBytes.size)?.asImageBitmap()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.windows_tool_home_map_confirm_title, stringResource(kind.titleRes())),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = buildPreviewDetails(preview),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = stringResource(R.string.windows_tool_home_map_confirm_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun buildPreviewDetails(preview: HomeOverviewMapProcessor.Preview): String {
    val sizeLine = stringResource(
        R.string.windows_tool_home_map_preview_size,
        preview.width,
        preview.height,
    )
    return if (preview.wasResized) {
        stringResource(
            R.string.windows_tool_home_map_preview_resized,
            preview.originalWidth,
            preview.originalHeight,
            preview.width,
            preview.height,
        ) + "\n" + sizeLine
    } else {
        sizeLine
    }
}
