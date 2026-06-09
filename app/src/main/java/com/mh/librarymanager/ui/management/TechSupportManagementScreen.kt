package com.mh.librarymanager.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.TechSupportRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun TechSupportManagementScreen(
    viewModel: TechSupportManagementViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    var deleteCandidate by remember { mutableStateOf<TechSupportRequest?>(null) }

    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        ManagementHeader(
            title = stringResource(R.string.tech_support_management_title),
            onBack = onBack,
            onLogout = onLogout,
        )

        HorizontalDivider(color = cs.outlineVariant)

        if (requests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.tech_support_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(requests, key = { it.id }) { request ->
                TechSupportCard(
                    request = request,
                    onDelete = { deleteCandidate = request },
                )
            }
        }
    }

    deleteCandidate?.let { candidate ->
        ConfirmDeleteDialog(
            request = candidate,
            onDismiss = { deleteCandidate = null },
            onConfirm = {
                viewModel.delete(candidate.id)
                deleteCandidate = null
            },
        )
    }
}

@Composable
private fun TechSupportCard(
    request: TechSupportRequest,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reporterLine(request),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(request.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, cs.error),
                ) {
                    Text(text = stringResource(R.string.delete), color = cs.error)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = request.problem,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    request: TechSupportRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = stringResource(R.string.tech_support_confirm_delete),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = request.problem.take(80).let { if (request.problem.length > 80) "$it…" else it },
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.confirm_delete_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.error,
                            contentColor = cs.onError,
                        ),
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@Composable
private fun reporterLine(request: TechSupportRequest): String {
    val name = request.reporterName.ifBlank {
        stringResource(R.string.tech_support_anonymous_label)
    }
    return name
}

private val TS_FMT: SimpleDateFormat by lazy {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("he")).apply {
        timeZone = TimeZone.getDefault()
    }
}

private fun formatTimestamp(ts: Long): String = TS_FMT.format(Date(ts))
