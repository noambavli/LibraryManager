package com.mh.librarymanager.ui.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.components.AppBrandHeader
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppDashboardTopBar
import com.mh.librarymanager.ui.components.AppManagementTile
import com.mh.librarymanager.ui.components.AppScreenBackground

private data class DashboardEntry(
    val title: String,
    val subtitle: String,
    val accent: Color,
    val onClick: () -> Unit,
)

@Composable
fun ManagementDashboardScreen(
    outOfOrderCount: Int,
    onOpenBooks: () -> Unit,
    onOpenOutOfOrder: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSearchHistory: () -> Unit,
    onOpenPopularBooks: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenTechSupport: () -> Unit,
    onOpenCatalogTransfer: () -> Unit,
    onLogout: () -> Unit,
) {
    AppScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp),
            ) {
                AppDashboardTopBar(onLogout = onLogout)
                Spacer(modifier = Modifier.height(16.dp))
                AppBrandHeader(title = stringResource(R.string.management_title))

                Spacer(modifier = Modifier.height(28.dp))

                DashboardGrid(
                    outOfOrderCount = outOfOrderCount,
                    onOpenBooks = onOpenBooks,
                    onOpenOutOfOrder = onOpenOutOfOrder,
                    onOpenHistory = onOpenHistory,
                    onOpenSearchHistory = onOpenSearchHistory,
                    onOpenPopularBooks = onOpenPopularBooks,
                    onOpenRequests = onOpenRequests,
                    onOpenAnnouncements = onOpenAnnouncements,
                    onOpenShortcuts = onOpenShortcuts,
                    onOpenTechSupport = onOpenTechSupport,
                    onOpenCatalogTransfer = onOpenCatalogTransfer,
                )
            }
        }
    }
}

@Composable
private fun DashboardGrid(
    outOfOrderCount: Int,
    onOpenBooks: () -> Unit,
    onOpenOutOfOrder: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSearchHistory: () -> Unit,
    onOpenPopularBooks: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenTechSupport: () -> Unit,
    onOpenCatalogTransfer: () -> Unit,
) {
    val tiles = listOf(
        DashboardEntry(
            stringResource(R.string.management_books),
            stringResource(R.string.management_books_subtitle),
            AppColors.Accent,
            onOpenBooks,
        ),
        DashboardEntry(
            stringResource(R.string.management_out_of_order),
            if (outOfOrderCount > 0) {
                stringResource(R.string.management_out_of_order_subtitle_count, outOfOrderCount)
            } else {
                stringResource(R.string.management_out_of_order_subtitle_ok)
            },
            if (outOfOrderCount > 0) AppColors.Warning else AppColors.Accent,
            onOpenOutOfOrder,
        ),
        DashboardEntry(
            stringResource(R.string.management_requests),
            stringResource(R.string.management_requests_subtitle),
            AppColors.Accent,
            onOpenRequests,
        ),
        DashboardEntry(
            stringResource(R.string.management_announcements),
            stringResource(R.string.management_announcements_subtitle),
            AppColors.Accent,
            onOpenAnnouncements,
        ),
        DashboardEntry(
            stringResource(R.string.management_shortcuts),
            stringResource(R.string.management_shortcuts_subtitle),
            AppColors.Accent,
            onOpenShortcuts,
        ),
        DashboardEntry(
            stringResource(R.string.management_search_history),
            stringResource(R.string.management_search_history_subtitle),
            AppColors.Accent,
            onOpenSearchHistory,
        ),
        DashboardEntry(
            stringResource(R.string.management_popular_books),
            stringResource(R.string.management_popular_books_subtitle),
            AppColors.Accent,
            onOpenPopularBooks,
        ),
        DashboardEntry(
            stringResource(R.string.management_history),
            stringResource(R.string.management_history_subtitle),
            AppColors.Accent,
            onOpenHistory,
        ),
        DashboardEntry(
            stringResource(R.string.management_tech_support),
            stringResource(R.string.management_tech_support_subtitle),
            AppColors.Accent,
            onOpenTechSupport,
        ),
        DashboardEntry(
            stringResource(R.string.management_catalog_transfer),
            stringResource(R.string.management_catalog_transfer_subtitle),
            AppColors.Accent,
            onOpenCatalogTransfer,
        ),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth > 640.dp
        if (wide) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { tile ->
                            AppManagementTile(
                                title = tile.title,
                                subtitle = tile.subtitle,
                                accent = tile.accent,
                                onClick = tile.onClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.forEach { tile ->
                    AppManagementTile(
                        title = tile.title,
                        subtitle = tile.subtitle,
                        accent = tile.accent,
                        onClick = tile.onClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
