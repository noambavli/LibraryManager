package com.mh.librarymanager.ui.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R

/**
 * Public full view of a single announcement, reached from "הודעה מלאה".
 */
@Composable
fun AnnouncementDetailScreen(
    viewModel: AnnouncementsViewModel,
    announcementId: String,
    onBack: () -> Unit,
) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    val booksById by viewModel.booksById.collectAsStateWithLifecycle()
    val customColors by viewModel.customColors.collectAsStateWithLifecycle()
    val announcement = all.firstOrNull { it.id == announcementId }
    val isExpired = announcement != null && !announcement.isActive()

    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        BackBar(onBack = onBack)

        if (announcement == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.announcement_missing),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            if (isExpired) {
                Text(
                    text = stringResource(R.string.announcement_expired_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            AnnouncementFullView(
                announcement = announcement,
                booksById = booksById,
                customColors = customColors,
            )
        }
    }
}

@Composable
internal fun BackBar(onBack: () -> Unit, title: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = "‹  " + stringResource(R.string.back),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (title != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
