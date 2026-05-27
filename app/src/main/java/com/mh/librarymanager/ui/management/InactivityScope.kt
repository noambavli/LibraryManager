package com.mh.librarymanager.ui.management

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mh.librarymanager.R
import kotlinx.coroutines.delay

/**
 * Wraps a management screen so any tap/drag resets the idle timer, and the
 * "you're about to be logged out" warning + auto-logout fire on schedule.
 *
 * The pointer listener uses [PointerEventPass.Initial] so we observe input
 * even when the warning sheet is consuming it — we want the warning to stay
 * visible (the user must press "stay logged in" deliberately) but we still
 * want to know they're alive.
 */
@Composable
fun InactivityScope(
    session: ManagementSession,
    onAutoLogout: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val warningStartedAt = session.warningStartedAt
    var nowTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(session) {
        while (true) {
            delay(500)
            nowTick = SystemClock.elapsedRealtime()
            val started = session.warningStartedAt
            if (started == null) {
                val idleFor = SystemClock.elapsedRealtime() - session.lastInteractionAt()
                if (idleFor >= ManagementSession.IDLE_BEFORE_WARNING_MS) {
                    session.showWarningNow()
                }
            } else {
                val remaining = ManagementSession.WARNING_DURATION_MS -
                    (SystemClock.elapsedRealtime() - started)
                if (remaining <= 0L) {
                    session.logout()
                    onAutoLogout()
                }
            }
        }
    }

    // If the app goes background while authenticated, force-logout when it
    // resumes — kiosks shouldn't keep an admin session warm across away time.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && session.isAuthenticated) {
                session.logout()
                onAutoLogout()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(session) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            session.recordInteraction()
                        }
                    }
                }
            },
    ) {
        content()

        if (warningStartedAt != null) {
            val secondsRemaining = (
                (ManagementSession.WARNING_DURATION_MS -
                    (nowTick - warningStartedAt)) / 1000L
                ).coerceAtLeast(0L).toInt()
            IdleWarningOverlay(
                secondsRemaining = secondsRemaining,
                onStay = { session.dismissWarning() },
                onLogoutNow = {
                    session.logout()
                    onAutoLogout()
                },
            )
        }
    }
}

@Composable
private fun IdleWarningOverlay(
    secondsRemaining: Int,
    onStay: () -> Unit,
    onLogoutNow: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = stringResource(R.string.inactivity_warning_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.inactivity_warning_body,
                        secondsRemaining,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onLogoutNow) {
                        Text(stringResource(R.string.logout))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = onStay) {
                        Text(stringResource(R.string.inactivity_stay))
                    }
                }
            }
        }
    }
}
