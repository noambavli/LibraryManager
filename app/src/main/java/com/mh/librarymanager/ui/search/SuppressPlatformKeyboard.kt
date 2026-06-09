package com.mh.librarymanager.ui.search

import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.awaitCancellation

/**
 * Wraps the entire app so the system IME can **never** be started.
 *
 * Every Compose text field still places its cursor and supports tap/selection,
 * but when it asks the platform to begin an input-method session we swallow
 * the request and suspend forever instead of delegating to the real handler.
 * Because the session never starts, the on-screen system keyboard never
 * appears — this replaces the previous "let it open, then race to hide it"
 * approach that occasionally flashed the keyboard.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoSystemKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { _, _ -> awaitCancellation() },
        content = content,
    )
}

/**
 * Belt-and-suspenders for the rare legacy path where an [EditText] view is
 * focused directly: disable its soft-input-on-focus and force the IME hidden.
 * The primary defence is [NoSystemKeyboard]; this just covers edge cases.
 */
@Composable
fun SuppressPlatformKeyboardEffect() {
    val root = LocalView.current
    DisposableEffect(root) {
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
            if (focused is EditText) {
                focused.showSoftInputOnFocus = false
                val imm = focused.context.getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(focused.windowToken, 0)
            }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
        onDispose {
            root.viewTreeObserver.removeOnGlobalFocusChangeListener(listener)
        }
    }
}
