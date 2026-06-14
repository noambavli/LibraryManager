package com.mh.librarymanager.ui.announcements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.PublicBackBar

/**
 * Public full view of a single announcement, reached from "הודעה מלאה".
 */
@Composable
fun AnnouncementDetailScreen(
    viewModel: AnnouncementsViewModel,
    announcementId: String,
    booksById: Map<String, Book>,
    customColors: List<CustomColor>,
    onBack: () -> Unit,
    onOpenBookLocation: (String) -> Unit = {},
) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val announcement = all.firstOrNull { it.id == announcementId }
    val isExpired = announcement != null && !announcement.isActive()

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            PublicBackBar(onBack = onBack)

            when {
                !loaded && announcement == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.results_loading),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextMuted,
                    )
                }
                announcement == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.announcement_missing),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextMuted,
                    )
                }
                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                ) {
                    if (isExpired) {
                        Text(
                            text = stringResource(R.string.announcement_expired_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextMuted,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    AnnouncementFullView(
                        announcement = announcement,
                        booksById = booksById,
                        customColors = customColors,
                        onOpenBookLocation = onOpenBookLocation,
                    )
                }
            }
        }
    }
}
