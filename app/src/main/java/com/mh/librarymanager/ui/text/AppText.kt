package com.mh.librarymanager.ui.text

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource as androidStringResource
import com.mh.librarymanager.data.store.TextOverrideRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * Reactive source of the current text overrides for Compose. Defaults to an
 * empty map so any composable used outside [ProvideAppText] (previews, the
 * technician maintenance screen) simply falls back to the shipped defaults.
 */
val LocalTextOverrides: ProvidableCompositionLocal<Map<String, String>> =
    compositionLocalOf { emptyMap() }

/** Cache of string-resource id -> stable entry name (e.g. "home_title"). */
private val entryNameCache = ConcurrentHashMap<Int, String>()

private fun entryNameOf(context: Context, @StringRes id: Int): String? {
    entryNameCache[id]?.let { return it }
    return try {
        context.resources.getResourceEntryName(id).also { entryNameCache[id] = it }
    } catch (_: Exception) {
        null
    }
}

/**
 * Drop-in replacement for `androidx.compose.ui.res.stringResource`.
 *
 * Screens import this instead of the framework function (identical signature),
 * so every existing call site keeps working unchanged. If management has
 * overridden the string it wins; otherwise the shipped default is returned.
 */
@Composable
fun stringResource(@StringRes id: Int): String {
    val overrides = LocalTextOverrides.current
    if (overrides.isNotEmpty()) {
        val key = entryNameOf(LocalContext.current, id)
        val override = key?.let { overrides[it] }
        if (override != null) return override
    }
    return androidStringResource(id)
}

/**
 * Formatted overload. A management-edited template that no longer matches its
 * arguments can never crash the UI: if [String.format] throws we quietly fall
 * back to the shipped default rather than surfacing an exception.
 */
@Composable
fun stringResource(@StringRes id: Int, vararg formatArgs: Any): String {
    val overrides = LocalTextOverrides.current
    if (overrides.isNotEmpty()) {
        val key = entryNameOf(LocalContext.current, id)
        val template = key?.let { overrides[it] }
        if (template != null) {
            return try {
                String.format(template, *formatArgs)
            } catch (_: Exception) {
                androidStringResource(id, *formatArgs)
            }
        }
    }
    return androidStringResource(id, *formatArgs)
}

/**
 * Non-composable resolver for view-models and other [Context] holders. Reads the
 * same overrides from [TextOverrideRegistry]. Format failures fall back to the
 * shipped default, mirroring the composable path.
 */
fun Context.appString(@StringRes id: Int): String {
    val overrides = TextOverrideRegistry.current
    if (overrides.isNotEmpty()) {
        entryNameOf(this, id)?.let { overrides[it] }?.let { return it }
    }
    return getString(id)
}

fun Context.appString(@StringRes id: Int, vararg formatArgs: Any): String {
    val overrides = TextOverrideRegistry.current
    if (overrides.isNotEmpty()) {
        val template = entryNameOf(this, id)?.let { overrides[it] }
        if (template != null) {
            return try {
                String.format(template, *formatArgs)
            } catch (_: Exception) {
                getString(id, *formatArgs)
            }
        }
    }
    return getString(id, *formatArgs)
}

/** Makes [overrides] available to every `stringResource` call in [content]. */
@Composable
fun ProvideAppText(
    overrides: Map<String, String>,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTextOverrides provides overrides, content = content)
}
