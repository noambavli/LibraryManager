package com.mh.librarymanager.ui.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R

/**
 * Public list of every active announcement, each rendered in full.
 * Title: "כל הודעות המערכת המלאות".
 */
@Composable
fun AllAnnouncementsScreen(
    viewModel: AnnouncementsViewModel,
    onBack: () -> Unit,
) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    val booksById by viewModel.booksById.collectAsStateWithLifecycle()
    val customColors by viewModel.customColors.collectAsStateWithLifecycle()

    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        BackBar(onBack = onBack)

        Text(
            text = stringResource(R.string.announcements_all_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = cs.onBackground,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
        )

        if (active.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.announcements_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(active, key = { it.id }) { announcement ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cs.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
                    shadowElevation = 1.dp,
                ) {
                    AnnouncementFullView(
                        announcement = announcement,
                        booksById = booksById,
                        customColors = customColors,
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    )
                }
            }
        }
    }
}
