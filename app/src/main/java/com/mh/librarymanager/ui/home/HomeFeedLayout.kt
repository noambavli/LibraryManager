package com.mh.librarymanager.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.ui.announcements.isLong
import com.mh.librarymanager.ui.components.AppColors

/** Shared sizing for the home "what's new" and announcements panels (~3 book rows). */
object HomeFeedLayout {
    /** Visible book rows in the catalog panel. */
    const val MaxBooks = 3

    /**
     * Fixed content well below the panel title. Sized for three compact book
     * rows (single-line title + writer) with spacing between them.
     */
    val ContentHeight = 141.dp

    /** Keeps the announcements "see all" row aligned with the books panel. */
    val FooterHeight = 29.dp

    private val CardSpacing = 6.dp
    private val CardPaddingV = 16.dp
    private val TitleHeight = 14.dp
    private val TitleDescGap = 2.dp
    private val DescLineHeight = 12.dp
    private val LinkRowHeight = 18.dp

    data class AnnouncementSlot(
        val announcement: Announcement,
        val descriptionMaxLines: Int,
    )

    private data class FitStrategy(val maxItems: Int, val descLinesPerCard: Int)

    private fun estimatedCardHeight(announcement: Announcement, descMaxLines: Int): Dp {
        var height = CardPaddingV + TitleHeight
        if (announcement.description.isNotBlank() && descMaxLines > 0) {
            height += TitleDescGap + DescLineHeight * descMaxLines
        }
        if (announcement.isLong()) height += LinkRowHeight
        return height
    }

    /**
     * Picks the first N announcements that fit the fixed home well, with
     * per-card description line limits. Uses coarse dp estimates only — no
     * layout measurement pass.
     */
    fun planAnnouncements(announcements: List<Announcement>): List<AnnouncementSlot> {
        if (announcements.isEmpty()) return emptyList()

        val strategies = listOf(
            FitStrategy(maxItems = 3, descLinesPerCard = 2),
            FitStrategy(maxItems = 2, descLinesPerCard = 3),
            FitStrategy(maxItems = 1, descLinesPerCard = 8),
        )

        for (strategy in strategies) {
            val slice = announcements.take(strategy.maxItems)
            var total = 0.dp
            slice.forEachIndexed { index, announcement ->
                total += estimatedCardHeight(announcement, strategy.descLinesPerCard)
                if (index > 0) total += CardSpacing
            }
            if (total <= ContentHeight) {
                return slice.map { AnnouncementSlot(it, strategy.descLinesPerCard) }
            }
        }

        val first = announcements.first()
        val base = estimatedCardHeight(first, descMaxLines = 0)
        val lines = ((ContentHeight - base) / DescLineHeight)
            .toInt()
            .coerceIn(1, 8)
        return listOf(AnnouncementSlot(first, lines))
    }
}

@Composable
fun HomeFeedPanel(
    title: String,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppColors.Panel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.Border),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeFeedLayout.ContentHeight)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                content()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeFeedLayout.FooterHeight),
                contentAlignment = Alignment.CenterEnd,
            ) {
                footer()
            }
        }
    }
}
