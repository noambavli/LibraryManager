package com.mh.librarymanager.ui.management

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppContentCard
import com.mh.librarymanager.ui.components.AppLogo
import com.mh.librarymanager.ui.components.AppPaneDivider
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.PublicBackBar
import kotlinx.coroutines.delay

private const val MAX_LEN = 8

/**
 * Numeric password gate. The kiosk forcibly hides the system IME so we own a
 * deliberately big, tactile keypad. A small shake-style error label appears
 * on a bad code and clears itself on the next keystroke.
 *
 * On landscape tablets the branding and keypad sit side-by-side; on narrower
 * layouts they stack vertically with a capped content width.
 */
@Composable
fun PasswordScreen(
    session: ManagementSession,
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(showError) {
        if (showError) {
            delay(1800)
            showError = false
        }
    }

    val onDigit: (Char) -> Unit = { d ->
        showError = false
        if (code.length < MAX_LEN) code += d
    }
    val onBackspace: () -> Unit = {
        showError = false
        if (code.isNotEmpty()) code = code.dropLast(1)
    }
    val onSubmit: () -> Unit = {
        if (session.tryUnlock(code)) {
            code = ""
            onUnlocked()
        } else {
            showError = true
            code = ""
        }
    }

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            PublicBackBar(onBack = onBack)

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            ) {
                val landscapeTablet = maxWidth >= 720.dp && maxWidth > maxHeight

                if (landscapeTablet) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PasswordBrandingPane(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp),
                            codeLength = code.length,
                            showError = showError,
                            logoSize = 88.dp,
                            textAlign = TextAlign.Start,
                        )

                        AppPaneDivider()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppContentCard(
                                modifier = Modifier.widthIn(max = 420.dp),
                            ) {
                                Keypad(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    onDigit = onDigit,
                                    onBackspace = onBackspace,
                                    onSubmit = onSubmit,
                                    submitEnabled = code.isNotEmpty(),
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 480.dp)
                            .align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        PasswordBrandingPane(
                            modifier = Modifier.fillMaxWidth(),
                            codeLength = code.length,
                            showError = showError,
                            logoSize = 80.dp,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        AppContentCard(modifier = Modifier.fillMaxWidth()) {
                            Keypad(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                onDigit = onDigit,
                                onBackspace = onBackspace,
                                onSubmit = onSubmit,
                                submitEnabled = code.isNotEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordBrandingPane(
    codeLength: Int,
    showError: Boolean,
    logoSize: Dp,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = when (textAlign) {
            TextAlign.Start, TextAlign.Right -> Alignment.Start
            else -> Alignment.CenterHorizontally
        },
        verticalArrangement = Arrangement.Center,
    ) {
        AppLogo(size = logoSize)
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.password_title),
            style = MaterialTheme.typography.headlineMedium,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.password_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextSecondary,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(22.dp))
        CodeDots(length = codeLength)
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedVisibility(visible = showError) {
            Text(
                text = stringResource(R.string.password_wrong),
                color = AppColors.Warning,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CodeDots(length: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val total = MAX_LEN.coerceAtMost(8)
        repeat(total) { i ->
            val filled = i < length
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) AppColors.Accent
                        else AppColors.BorderLight,
                    ),
            )
        }
    }
}

@Composable
private fun Keypad(
    modifier: Modifier = Modifier,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KeyDigit('1', onDigit, Modifier.weight(1f))
            KeyDigit('2', onDigit, Modifier.weight(1f))
            KeyDigit('3', onDigit, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KeyDigit('4', onDigit, Modifier.weight(1f))
            KeyDigit('5', onDigit, Modifier.weight(1f))
            KeyDigit('6', onDigit, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KeyDigit('7', onDigit, Modifier.weight(1f))
            KeyDigit('8', onDigit, Modifier.weight(1f))
            KeyDigit('9', onDigit, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KeyAction(label = "⌫", onClick = onBackspace, accent = false, modifier = Modifier.weight(1f))
            KeyDigit('0', onDigit, Modifier.weight(1f))
            KeyAction(
                label = stringResource(R.string.password_unlock),
                onClick = onSubmit,
                accent = true,
                disabled = !submitEnabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KeyDigit(digit: Char, onDigit: (Char) -> Unit, modifier: Modifier = Modifier) {
    KeyTile(label = digit.toString(), accent = false, disabled = false, modifier = modifier) {
        onDigit(digit)
    }
}

@Composable
private fun KeyAction(
    label: String,
    onClick: () -> Unit,
    accent: Boolean,
    modifier: Modifier = Modifier,
    disabled: Boolean = false,
) {
    KeyTile(label = label, accent = accent, disabled = disabled, modifier = modifier, onClick = onClick)
}

@Composable
private fun KeyTile(
    label: String,
    accent: Boolean,
    disabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when {
        disabled -> AppColors.BorderLight
        accent -> AppColors.HeroStart
        else -> AppColors.PanelElevated
    }
    val fg = when {
        disabled -> AppColors.TextMuted.copy(alpha = 0.4f)
        accent -> Color.White
        else -> AppColors.TextPrimary
    }
    Surface(
        modifier = modifier.height(76.dp),
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(14.dp),
        border = if (accent) null else BorderStroke(1.dp, AppColors.Border),
        shadowElevation = if (accent) 4.dp else 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .let { if (disabled) it else it.clickable(onClick = onClick) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = if (label.length > 2) 20.sp else 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (disabled) Color.Gray else fg,
                maxLines = 1,
            )
        }
    }
}
