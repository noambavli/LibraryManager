package com.mh.librarymanager.ui.location

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.PublicBackBar

@Composable
fun BookLocationScreen(
    book: Book?,
    catalogLoaded: Boolean,
    onBack: () -> Unit,
) {
    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            PublicBackBar(onBack = onBack)

            when {
                !catalogLoaded -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.results_loading),
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextMuted,
                        )
                    }
                }
                book == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.book_location_missing),
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextMuted,
                        )
                    }
                }
                else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.book_location_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = book.name.ifBlank { "—" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                book.place.labelRes()?.let { labelRes ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextSecondary,
                    )
                }
            }
            }
        }
    }
}
