package com.mh.librarymanager.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.data.homemap.HomeOverviewMapStore
import com.mh.librarymanager.domain.HomeOverviewMapKind
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.PublicBackBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeOverviewMapScreen(
    kind: HomeOverviewMapKind,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { HomeOverviewMapStore.from(context) }
    var imageBitmap by remember(kind) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var missing by remember(kind) { mutableStateOf(false) }

    LaunchedEffect(kind) {
        val file = store.mapFile(kind)
        if (!file.exists() || file.length() == 0L) {
            missing = true
            imageBitmap = null
            return@LaunchedEffect
        }
        missing = false
        imageBitmap = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
        if (imageBitmap == null) missing = true
    }

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            PublicBackBar(
                onBack = onBack,
                title = stringResource(kind.titleRes()),
            )

            when {
                missing -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.home_overview_map_missing),
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextMuted,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }
                imageBitmap != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Image(
                            bitmap = imageBitmap!!,
                            contentDescription = stringResource(kind.titleRes()),
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
