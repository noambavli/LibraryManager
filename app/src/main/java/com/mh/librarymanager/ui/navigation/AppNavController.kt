package com.mh.librarymanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Hand-rolled back-stack navigator.
 *
 * Compose's built-in nav graph is overkill here: the app is a five-screen
 * kiosk where every transition is deliberate and reversible only by an
 * on-screen affordance. We model it as a [SnapshotStateList] so Compose
 * recomposes whenever the stack mutates.
 */
class AppNavController internal constructor(initial: AppScreen) {

    private val stack: SnapshotStateList<AppScreen> = mutableStateListOf(initial)

    var current: AppScreen by mutableStateOf(initial)
        private set

    val canPop: Boolean get() = stack.size > 1

    fun push(screen: AppScreen) {
        stack += screen
        current = screen
    }

    /**
     * Replaces the top of the stack instead of pushing a new entry. Used when
     * the password gate unlocks into the management home — popping should
     * never bring the gate back.
     */
    fun replaceTop(screen: AppScreen) {
        if (stack.isEmpty()) {
            stack += screen
        } else {
            stack[stack.lastIndex] = screen
        }
        current = screen
    }

    fun popTo(screen: AppScreen) {
        while (stack.size > 1 && stack.last() != screen) {
            stack.removeAt(stack.lastIndex)
        }
        current = stack.last()
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        current = stack.last()
        return true
    }

    fun resetTo(screen: AppScreen) {
        stack.clear()
        stack += screen
        current = screen
    }
}

@Composable
fun rememberAppNavController(initial: AppScreen = AppScreen.Home): AppNavController =
    remember { AppNavController(initial) }
