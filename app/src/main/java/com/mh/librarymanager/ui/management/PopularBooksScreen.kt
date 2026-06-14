package com.mh.librarymanager.ui.management

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.linkedParent
import com.mh.librarymanager.ui.components.AppLoadingContent
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.BookCard
import com.mh.librarymanager.ui.components.ManagementHeader

@Composable
fun PopularBooksScreen(
    viewModel: PopularBooksViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val ratings by viewModel.ratings.collectAsStateWithLifecycle()
    val dataLoaded by viewModel.dataLoaded.collectAsStateWithLifecycle()
    val customColors by viewModel.customColors.collectAsStateWithLifecycle()
    val booksById = ratings.mapNotNull { it.book?.let { book -> book.id to book } }.toMap()

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ManagementHeader(
                title = stringResource(R.string.popular_books_title),
                onBack = onBack,
                onLogout = onLogout,
            )

            PopularBooksNotice(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )

            when {
                !dataLoaded -> AppLoadingContent()
                ratings.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.popular_books_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextMuted,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ratings, key = { it.bookId }) { rating ->
                        PopularBookCard(
                            rating = rating,
                            customColors = customColors,
                            booksById = booksById,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularBooksNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = AppColors.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.Border),
    ) {
        Text(
            text = stringResource(R.string.popular_books_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PopularBookCard(
    rating: PopularBookRating,
    customColors: List<com.mh.librarymanager.domain.CustomColor>,
    booksById: Map<String, com.mh.librarymanager.domain.Book>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.BorderLight),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.popular_books_rank, rating.rank),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.Accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 14.dp),
                )
                Text(
                    text = stringResource(R.string.popular_books_press_count, rating.pressCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val book = rating.book
            if (book != null) {
                BookCard(
                    book = book,
                    parentBook = book.linkedParent(booksById),
                    customColors = customColors,
                )
            } else {
                Text(
                    text = stringResource(R.string.popular_books_missing, rating.bookId),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
