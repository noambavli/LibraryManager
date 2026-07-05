package com.mh.librarymanager.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.ui.announcements.AnnouncementsHomeSection
import com.mh.librarymanager.ui.components.AppLoadingContent
import com.mh.librarymanager.ui.components.AppActionTile
import com.mh.librarymanager.ui.components.AppBrandHeader
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppHeroButton
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.resolveBookColorStyle

// The terms-of-use label and body are intentionally hardcoded here (NOT in
// strings.xml and NOT in the editable text catalog), so the management "app
// texts" editor can never change or remove them.
private const val TERMS_BUTTON_LABEL = "תנאי שימוש"
private const val TERMS_CLOSE_LABEL = "סגירה"
private const val TERMS_TITLE = "תנאי שימוש"
private const val TERMS_BODY =
    "כל שימוש בתוכנה זו מותנה בהסכמה לתנאים הבאים:\n\n" +
        "א. מחילה גמורה ליוצרי התוכנה על כל טעות באלגוריתם החיפוש, ועל כל נזק, חס ושלום, " +
        "שעלול להיגרם כתוצאה מתוצאות שגויות של אלגוריתם החיפוש, וכן על כל דבר שאינו טוב, חס ושלום.\n\n" +
        "ב. מחילה גמורה על כל טעות בתמחור המכשירים, על כל נזק למכשירים, ועל כל עניין הקשור למכשירים שנרכשו.\n\n" +
        "ג. מחילה גמורה על כל פגיעה, חס ושלום, ברוחניות או בגשמיות עקב השימוש במכשיר זה, " +
        "בין אם עקב טעות בתוכנה שהובילה למסקנה שגויה בדבר הימצאות ספר, ובין מכל סיבה אחרת.\n\n" +
        "ד. למרות ההשקעה הרבה שנעשתה כדי לוודא שהמכשירים יהיו חסומים לחלוטין מכל סוג של גישה לאינטרנט, " +
        "חס ושלום, וכן מכל סוג של תוכנה אחרת, במקרה – אף שאינו סביר – של פרצה כלשהי במכשיר, " +
        "לרבות אך לא רק: יציאה מהתוכנה, התקנת תוכנה אחרת, או, חס ושלום, אפשרות להיכנס להגדרות המכשיר " +
        "(למרות שנעשו מאמצים רבים לחסום אפשרות זו מכל כיוון), יש לדווח על כך מיד לאחראי הספרייה או למשגיח, " +
        "להרחיק את המכשיר מהספרייה, וליצור קשר בהקדם האפשרי, בכל שעה (באמת ב*כל* שעה!!!!), " +
        "בטלפון: 055-673-2641 . רק במקרה שהמספר לא זמין כמה ימים כגון שהוחלף מספר טלפון, ניתן ליצור קשר עם " +
        "בית יצחק 1, ירושלים 02-6544500, לבקש שיעבירו אתכם למזכירות המחלקה הישראלית ולבקש מהמחלקה " +
        "הישראלית את הטלפון המעודכן של שם המשפחה ״בבלי״ משנת תשפ״ו ."

/**
 * Tablet landing screen: search is the primary action at the top; announcements
 * and recently added books live in compact vertical panels below the secondary
 * shortcuts.
 */
@Composable
fun HomeScreen(
    recentlyAdded: List<Book>,
    catalogLoaded: Boolean,
    customColors: List<CustomColor>,
    announcements: List<Announcement>,
    onOpenSearch: () -> Unit,
    onOpenManagement: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenTechSupport: () -> Unit,
    onOpenOtzarMap: () -> Unit,
    onOpenBeisMidrashMap: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenAllAnnouncements: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var showTerms by remember { mutableStateOf(false) }
    AppScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TermsButton(onClick = { showTerms = true })
                }

                Spacer(modifier = Modifier.height(8.dp))

                AppBrandHeader(
                    title = stringResource(R.string.home_title),
                    subtitle = stringResource(R.string.home_subtitle),
                )

                Spacer(modifier = Modifier.height(28.dp))

                AppHeroButton(
                    title = stringResource(R.string.home_search),
                    subtitle = stringResource(R.string.home_search_subtitle),
                    onClick = onOpenSearch,
                )

                Spacer(modifier = Modifier.height(20.dp))

                SecondaryActionsRow(
                    onOpenRequests = onOpenRequests,
                    onOpenTechSupport = onOpenTechSupport,
                    onOpenManagement = onOpenManagement,
                )

                Spacer(modifier = Modifier.height(16.dp))

                MapOverviewButtonsRow(
                    onOpenOtzarMap = onOpenOtzarMap,
                    onOpenBeisMidrashMap = onOpenBeisMidrashMap,
                )

                if (!catalogLoaded) {
                    Spacer(modifier = Modifier.height(24.dp))
                    HomeFeedPanel(title = stringResource(R.string.home_whats_new)) {
                        AppLoadingContent(modifier = Modifier.fillMaxSize())
                    }
                } else if (announcements.isNotEmpty() || recentlyAdded.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    FeedPanelsRow(
                        announcements = announcements,
                        recentlyAdded = recentlyAdded,
                        customColors = customColors,
                        onOpenAnnouncement = onOpenAnnouncement,
                        onOpenAllAnnouncements = onOpenAllAnnouncements,
                    )
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                    WhatsNewSection(
                        books = recentlyAdded,
                        customColors = customColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

            if (showTerms) {
                TermsOfUseOverlay(onClose = { showTerms = false })
            }
        }
    }
}

@Composable
private fun TermsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = AppColors.PanelElevated,
        border = BorderStroke(1.dp, AppColors.BorderLight),
    ) {
        Text(
            text = TERMS_BUTTON_LABEL,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.TextSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TermsOfUseOverlay(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // Consume all touches so the home screen behind stays inert.
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .fillMaxHeight(0.9f)
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = TERMS_TITLE,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = TERMS_BODY,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.HeroStart,
                ) {
                    Text(
                        text = TERMS_CLOSE_LABEL,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MapOverviewButtonsRow(
    onOpenOtzarMap: () -> Unit,
    onOpenBeisMidrashMap: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth > 640.dp
        val maps = listOf(
            Triple(stringResource(R.string.home_map_otzar), stringResource(R.string.home_map_otzar_subtitle), onOpenOtzarMap),
            Triple(stringResource(R.string.home_map_beis_midrash), stringResource(R.string.home_map_beis_midrash_subtitle), onOpenBeisMidrashMap),
        )
        val accents = listOf(cs.primary, cs.secondary)

        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                maps.forEachIndexed { i, (title, subtitle, onClick) ->
                    AppActionTile(
                        title = title,
                        subtitle = subtitle,
                        accent = accents[i],
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                maps.forEachIndexed { i, (title, subtitle, onClick) ->
                    AppActionTile(
                        title = title,
                        subtitle = subtitle,
                        accent = accents[i],
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryActionsRow(
    onOpenRequests: () -> Unit,
    onOpenTechSupport: () -> Unit,
    onOpenManagement: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth > 640.dp
        val actions = listOf(
            Triple(stringResource(R.string.home_requests), stringResource(R.string.home_requests_subtitle), onOpenRequests),
            Triple(stringResource(R.string.home_tech_support), stringResource(R.string.home_tech_support_subtitle), onOpenTechSupport),
            Triple(stringResource(R.string.home_management), stringResource(R.string.home_management_subtitle), onOpenManagement),
        )
        val accents = listOf(cs.secondary, cs.tertiary, cs.primary)

        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                actions.forEachIndexed { i, (title, subtitle, onClick) ->
                    AppActionTile(
                        title = title,
                        subtitle = subtitle,
                        accent = accents[i],
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEachIndexed { i, (title, subtitle, onClick) ->
                    AppActionTile(
                        title = title,
                        subtitle = subtitle,
                        accent = accents[i],
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedPanelsRow(
    announcements: List<Announcement>,
    recentlyAdded: List<Book>,
    customColors: List<CustomColor>,
    onOpenAnnouncement: (String) -> Unit,
    onOpenAllAnnouncements: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val sideBySide = maxWidth > 720.dp
        if (sideBySide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (announcements.isNotEmpty()) {
                    AnnouncementsHomeSection(
                        announcements = announcements,
                        onOpenAnnouncement = onOpenAnnouncement,
                        onOpenAll = onOpenAllAnnouncements,
                        modifier = Modifier.weight(1f),
                    )
                }
                WhatsNewSection(
                    books = recentlyAdded,
                    customColors = customColors,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (announcements.isNotEmpty()) {
                    AnnouncementsHomeSection(
                        announcements = announcements,
                        onOpenAnnouncement = onOpenAnnouncement,
                        onOpenAll = onOpenAllAnnouncements,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WhatsNewSection(
                    books = recentlyAdded,
                    customColors = customColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WhatsNewSection(
    books: List<Book>,
    customColors: List<CustomColor>,
    modifier: Modifier = Modifier,
) {
    val visibleBooks = books.take(HomeFeedLayout.MaxBooks)

    HomeFeedPanel(
        title = stringResource(R.string.home_whats_new),
        modifier = modifier,
    ) {
        if (visibleBooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.home_whats_new_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Top),
            ) {
                visibleBooks.forEach { book ->
                    RecentBookRow(book = book, customColors = customColors)
                }
            }
        }
    }
}

@Composable
private fun RecentBookRow(
    book: Book,
    customColors: List<CustomColor>,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AppColors.BorderLight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (book.color.isNotBlank()) {
                val style = resolveBookColorStyle(
                    colorName = book.color,
                    customColors = customColors,
                    cardSurface = cs.surface,
                    fallbackBackground = cs.primaryContainer,
                    fallbackForeground = cs.onPrimaryContainer,
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(style.background),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.writer.isNotBlank()) {
                    Text(
                        text = book.writer,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = recencyLabel(book.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.AccentMuted,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
}

@Composable
private fun recencyLabel(createdAt: Long): String {
    val daysAgo = ((System.currentTimeMillis() - createdAt) / (24L * 60L * 60L * 1000L))
        .toInt()
        .coerceAtLeast(0)
    return when (daysAgo) {
        0 -> stringResource(R.string.home_added_today)
        1 -> stringResource(R.string.home_added_yesterday)
        else -> stringResource(R.string.home_added_days_ago, daysAgo)
    }
}
