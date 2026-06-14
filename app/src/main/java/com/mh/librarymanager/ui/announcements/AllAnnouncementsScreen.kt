package com.mh.librarymanager.ui.announcements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppContentCard
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.AppScreenTitle
import com.mh.librarymanager.ui.components.PublicBackBar

/**
 * Public list of every active announcement, each rendered in full.
 * Title: "כל הודעות המערכת המלאות".
 */
@Composable
fun AllAnnouncementsScreen(
    viewModel: AnnouncementsViewModel,
    booksById: Map<String, Book>,
    customColors: List<CustomColor>,
    onBack: () -> Unit,
    onOpenBookLocation: (String) -> Unit = {},
) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            PublicBackBar(onBack = onBack)

            AppScreenTitle(
                text = stringResource(R.string.announcements_all_title),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
            )

            when {
                !loaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.results_loading),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = AppColors.TextMuted,
                    )
                }
                active.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.announcements_empty),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = AppColors.TextMuted,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(active, key = { it.id }) { announcement ->
                        AppContentCard(modifier = Modifier.fillMaxWidth()) {
                            AnnouncementFullView(
                                announcement = announcement,
                                booksById = booksById,
                                customColors = customColors,
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                onOpenBookLocation = onOpenBookLocation,
                            )
                        }
                    }
                }
            }
        }
    }
}
