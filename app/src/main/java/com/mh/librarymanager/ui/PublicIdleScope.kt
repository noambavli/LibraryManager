package com.mh.librarymanager.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

object PublicIdle {
    /** Return to the attract screen after this long with no public interaction. */
    const val TIMEOUT_MS = 5 * 60 * 1000L
    internal const val MIN_POLL_MS = 1_000L
    internal const val MAX_POLL_MS = 30_000L
}

/**
 * Resets a timer on any touch within [content]. When [enabled] and the user
 * stays idle for [timeoutMs], [onIdle] fires (typically return to attract).
 *
 * Disabled on the attract screen itself so the kiosk can sit there with no
 * background polling. Polling backs off the farther the session is from timing out.
 */
@Composable
fun PublicIdleScope(
    enabled: Boolean,
    onIdle: () -> Unit,
    modifier: Modifier = Modifier,
    timeoutMs: Long = PublicIdle.TIMEOUT_MS,
    content: @Composable () -> Unit,
) {
    var lastInteractionAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(enabled, timeoutMs) {
        if (!enabled) return@LaunchedEffect
        lastInteractionAt = SystemClock.elapsedRealtime()
        while (true) {
            val idleFor = SystemClock.elapsedRealtime() - lastInteractionAt
            if (idleFor >= timeoutMs) {
                onIdle()
                lastInteractionAt = SystemClock.elapsedRealtime()
            }
            val remaining = timeoutMs - idleFor
            val delayMs = when {
                remaining <= 30_000L -> PublicIdle.MIN_POLL_MS
                remaining <= 120_000L -> 5_000L
                else -> PublicIdle.MAX_POLL_MS
            }
            delay(delayMs)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            lastInteractionAt = SystemClock.elapsedRealtime()
                        }
                    }
                }
            },
    ) {
        content()
    }
}
