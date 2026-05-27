package com.mh.librarymanager.ui.search

import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps [EditText] instances (used under Compose text fields) from opening the
 * system IME while still allowing tap-to-place-cursor and selection.
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
