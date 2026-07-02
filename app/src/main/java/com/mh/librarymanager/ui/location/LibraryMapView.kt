package com.mh.librarymanager.ui.location

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.LibraryMap
import com.mh.librarymanager.domain.LibraryMapSection
import kotlin.math.min

/** Tip of [R.drawable.ic_location_pin] in its 24×24 viewport (y=22). */
private const val PIN_TIP_Y_FRACTION = 22f / 24f

@Composable
fun LibraryMapView(
    map: LibraryMap,
    highlightSection: LibraryMapSection?,
    modifier: Modifier = Modifier,
    calloutText: String? = null,
) {
    // Maps are physical layout: coords come from Figma top-left (LTR).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val containerW = constraints.maxWidth.toFloat()
            val containerH = constraints.maxHeight.toFloat()
            if (containerW <= 0f || containerH <= 0f) return@BoxWithConstraints

            val frameW = map.frameWidth.toFloat()
            val frameH = map.frameHeight.toFloat()
            val scale = min(containerW / frameW, containerH / frameH)
            val drawnW = frameW * scale
            val drawnH = frameH * scale

            Box(
                modifier = with(density) {
                    Modifier.size(drawnW.toDp(), drawnH.toDp())
                },
            ) {
                Image(
                    painter = painterResource(map.imageResId),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )

                highlightSection?.let { section ->
                    val hotspot = section.hotspot
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val left = maxWidth * hotspot.xFraction(map.frameWidth)
                        val top = maxHeight * hotspot.yFraction(map.frameHeight)
                        val width = maxWidth * hotspot.wFraction(map.frameWidth)
                        val height = maxHeight * hotspot.hFraction(map.frameHeight)
                        val pinSize = maxOf(
                            min(width.value, height.value) * 2.2f,
                            56f,
                        ).dp

                        val centerX = left + width / 2
                        val centerY = top + height / 2

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = left, y = top)
                                .size(width, height)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF5252).copy(alpha = 0.15f))
                                .border(2.dp, Color(0xFFE53935), RoundedCornerShape(4.dp)),
                        )

                        Icon(
                            painter = painterResource(R.drawable.ic_location_pin),
                            contentDescription = null,
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(
                                    x = centerX - pinSize / 2,
                                    y = centerY - pinSize * PIN_TIP_Y_FRACTION,
                                )
                                .size(pinSize),
                        )

                        // Beis-Midrash writes the מדף beside the red sign.
                        if (!calloutText.isNullOrBlank()) {
                            Surface(
                                color = Color(0xFFD32F2F),
                                contentColor = Color.White,
                                shape = RoundedCornerShape(6.dp),
                                shadowElevation = 3.dp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = left + width + 6.dp, y = top),
                            ) {
                                Text(
                                    text = calloutText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
