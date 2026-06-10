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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import com.mh.librarymanager.domain.Announcement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Management → Announcements. Lists every announcement (active + expired) with
 * an "add" action and per-row delete.
 */
@Composable
fun AnnouncementsManagementScreen(
    viewModel: AnnouncementsManagementViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onAdd: () -> Unit,
) {
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    var deleteCandidate by remember { mutableStateOf<Announcement?>(null) }

    val cs = MaterialTheme.colorScheme
    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ManagementHeader(
            title = stringResource(R.string.announcements_management_title),
            onBack = onBack,
            onLogout = onLogout,
            primaryAction = stringResource(R.string.announcement_add),
            onPrimaryAction = onAdd,
        )

        if (announcements.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.announcements_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            return@Column
        }

        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(announcements, key = { it.id }) { announcement ->
                AnnouncementRow(
                    announcement = announcement,
                    onDelete = { deleteCandidate = announcement },
                )
            }
        }
    }

    deleteCandidate?.let { candidate ->
        ConfirmAnnouncementDeleteDialog(
            announcement = candidate,
            onDismiss = { deleteCandidate = null },
            onConfirm = {
                viewModel.delete(candidate.id)
                deleteCandidate = null
            },
        )
    }
    }
}

@Composable
private fun AnnouncementRow(
    announcement: Announcement,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val active = announcement.isActive()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(active = active)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = announcement.title.ifBlank { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (announcement.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = announcement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = metaLine(announcement),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, cs.error),
                ) {
                    Text(text = stringResource(R.string.delete), color = cs.error)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bg = if (active) Color(0xFF1B5E20) else Color(0xFF757575)
    Surface(color = bg, contentColor = Color.White, shape = RoundedCornerShape(8.dp)) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(
                text = stringResource(
                    if (active) R.string.announcement_active else R.string.announcement_expired
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ConfirmAnnouncementDeleteDialog(
    announcement: Announcement,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
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
                    text = stringResource(R.string.announcement_confirm_delete),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = announcement.title.ifBlank { "—" },
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

private val DATE_FMT: SimpleDateFormat by lazy {
    SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("he")).apply {
        timeZone = TimeZone.getDefault()
    }
}

@Composable
private fun metaLine(announcement: Announcement): String {
    val created = DATE_FMT.format(Date(announcement.createdAt))
    val until = DATE_FMT.format(Date(announcement.expiresAt()))
    val booksSuffix = if (announcement.linkedBookIds.isNotEmpty()) {
        " · " + stringResource(R.string.announcement_books_count, announcement.linkedBookIds.size)
    } else {
        ""
    }
    return stringResource(R.string.announcement_meta, created, until) + booksSuffix
}
