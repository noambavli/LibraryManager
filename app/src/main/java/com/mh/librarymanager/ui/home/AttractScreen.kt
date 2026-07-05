package com.mh.librarymanager.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppLogo

// Translucent white overlays read well over any (dark) hero gradient, so they
// stay fixed; the gradient and hint colours follow the selected theme.
private val AttractPanel = Color(0x1AFFFFFF)
private val AttractPanelBorder = Color(0x33FFFFFF)

/**
 * Default kiosk standby: themed branding, active announcements, swipe up to open.
 */
@Composable
fun AttractScreen(
    announcements: List<Announcement>,
    onOpenHome: () -> Unit,
) {
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    var accumulatedUp by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to AppColors.HeroStart,
                    1f to AppColors.HeroEnd,
                ),
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { accumulatedUp = 0f },
                    onDragCancel = { accumulatedUp = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount < 0f) {
                            accumulatedUp += -dragAmount
                            if (accumulatedUp >= swipeThresholdPx) {
                                accumulatedUp = 0f
                                onOpenHome()
                            }
                        } else {
                            accumulatedUp = 0f
                        }
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(size = 112.dp)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (announcements.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.announcements_home_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = AppColors.HeroSubtitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                    announcements.forEach { announcement ->
                        AttractAnnouncementCard(announcement = announcement)
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))
            SwipeUpHint()
        }
    }
}

@Composable
private fun AttractAnnouncementCard(announcement: Announcement) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AttractPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AttractPanelBorder),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = announcement.title.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (announcement.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = announcement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.HeroSubtitle,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SwipeUpHint() {
    val transition = rememberInfiniteTransition(label = "swipe_hint")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Text(
            text = "▲",
            style = MaterialTheme.typography.headlineSmall,
            color = AppColors.HeroSubtitle.copy(alpha = alpha),
            modifier = Modifier.offset(y = bounce.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.attract_swipe_up),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.HeroSubtitle.copy(alpha = alpha),
            textAlign = TextAlign.Center,
        )
    }
}
