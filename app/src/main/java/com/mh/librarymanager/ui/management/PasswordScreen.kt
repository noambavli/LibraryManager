package com.mh.librarymanager.ui.management

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mh.librarymanager.R
import kotlinx.coroutines.delay

private const val MAX_LEN = 8

/**
 * Numeric password gate. The kiosk forcibly hides the system IME so we own a
 * deliberately big, tactile keypad. A small shake-style error label appears
 * on a bad code and clears itself on the next keystroke.
 */
@Composable
fun PasswordScreen(
    session: ManagementSession,
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var code by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(showError) {
        if (showError) {
            delay(1800)
            showError = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 36.dp),
        ) {
            TopBar(onBack = onBack)

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.password_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = cs.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.password_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))

                CodeDots(length = code.length)

                Spacer(modifier = Modifier.height(14.dp))

                AnimatedVisibility(visible = showError) {
                    Text(
                        text = stringResource(R.string.password_wrong),
                        color = cs.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Keypad(
                    onDigit = { d ->
                        showError = false
                        if (code.length < MAX_LEN) code += d
                    },
                    onBackspace = {
                        showError = false
                        if (code.isNotEmpty()) code = code.dropLast(1)
                    },
                    onSubmit = {
                        if (session.tryUnlock(code)) {
                            code = ""
                            onUnlocked()
                        } else {
                            showError = true
                            code = ""
                        }
                    },
                    submitEnabled = code.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) {
            Text(
                text = "‹  " + stringResource(R.string.back),
                style = MaterialTheme.typography.titleMedium,
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
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
            )
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyDigit('1', onDigit)
            KeyDigit('2', onDigit)
            KeyDigit('3', onDigit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyDigit('4', onDigit)
            KeyDigit('5', onDigit)
            KeyDigit('6', onDigit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyDigit('7', onDigit)
            KeyDigit('8', onDigit)
            KeyDigit('9', onDigit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyAction(label = "⌫", onClick = onBackspace, accent = false)
            KeyDigit('0', onDigit)
            KeyAction(
                label = stringResource(R.string.password_unlock),
                onClick = onSubmit,
                accent = true,
                disabled = !submitEnabled,
            )
        }
    }
}

@Composable
private fun KeyDigit(digit: Char, onDigit: (Char) -> Unit) {
    KeyTile(label = digit.toString(), accent = false, disabled = false) { onDigit(digit) }
}

@Composable
private fun KeyAction(
    label: String,
    onClick: () -> Unit,
    accent: Boolean,
    disabled: Boolean = false,
) {
    KeyTile(label = label, accent = accent, disabled = disabled, onClick = onClick)
}

@Composable
private fun KeyTile(
    label: String,
    accent: Boolean,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg = when {
        disabled -> cs.surfaceVariant
        accent -> cs.primary
        else -> cs.surface
    }
    val fg = when {
        disabled -> cs.onSurfaceVariant.copy(alpha = 0.4f)
        accent -> cs.onPrimary
        else -> cs.onSurface
    }
    Surface(
        modifier = Modifier.size(width = 96.dp, height = 76.dp),
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(14.dp),
        border = if (accent) null else BorderStroke(1.dp, cs.outlineVariant),
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
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (disabled) Color.Gray else fg,
            )
        }
    }
}
