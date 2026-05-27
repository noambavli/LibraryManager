package com.mh.librarymanager.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R

@Composable
fun ManagementDashboardScreen(
    onOpenBooks: () -> Unit,
    onLogout: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            TopBar(onLogout = onLogout)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.management_title),
                style = MaterialTheme.typography.displaySmall,
                color = cs.onBackground,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DashboardTile(
                    modifier = Modifier.weight(1f).height(220.dp),
                    title = stringResource(R.string.management_books),
                    subtitle = stringResource(R.string.management_books_subtitle),
                    accent = cs.primary,
                    iconText = "\u05E1\u05E4\u05E8", // ספר
                    onClick = onOpenBooks,
                )
                // Reserved slots so it doesn't look lonely — disabled placeholders.
                PlaceholderTile(modifier = Modifier.weight(1f).height(220.dp))
            }
        }
    }
}

@Composable
private fun TopBar(onLogout: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onLogout) {
            Text(
                text = stringResource(R.string.logout),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun DashboardTile(
    modifier: Modifier,
    title: String,
    subtitle: String,
    accent: Color,
    iconText: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = cs.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = iconText,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                color = accent,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PlaceholderTile(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = cs.surface.copy(alpha = 0.4f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 0.dp,
    ) { Box(modifier = Modifier.fillMaxSize()) }
}
