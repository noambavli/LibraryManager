package com.mh.librarymanager.ui.support

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.TechSupportRequest
import com.mh.librarymanager.ui.search.KeyAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

enum class TechSupportField { NAME, PROBLEM }

class TechSupportViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    private val _fieldValues: MutableStateFlow<Map<TechSupportField, TextFieldValue>> =
        MutableStateFlow(TechSupportField.entries.associateWith { TextFieldValue("") })
    val fieldValues: StateFlow<Map<TechSupportField, TextFieldValue>> = _fieldValues.asStateFlow()

    private val _focusedField = MutableStateFlow(TechSupportField.NAME)
    val focusedField: StateFlow<TechSupportField> = _focusedField.asStateFlow()

    fun setValue(field: TechSupportField, value: TextFieldValue) {
        _fieldValues.value = _fieldValues.value.toMutableMap().also { it[field] = value }
    }

    fun setFocused(field: TechSupportField) {
        _focusedField.value = field
    }

    fun handleKey(key: KeyAction) {
        val field = _focusedField.value
        val current = _fieldValues.value[field] ?: TextFieldValue("")
        val next = when (key) {
            is KeyAction.Insert -> current.insertAt(key.text)
            KeyAction.Backspace -> current.deleteBack()
            KeyAction.ClearField, KeyAction.ClearAll -> TextFieldValue("")
        }
        setValue(field, next)
    }

    fun isDirty(): Boolean {
        val values = _fieldValues.value
        return values[TechSupportField.NAME]?.text?.isNotBlank() == true ||
            values[TechSupportField.PROBLEM]?.text?.isNotBlank() == true
    }

    fun canSubmit(): Boolean =
        _fieldValues.value[TechSupportField.PROBLEM]?.text?.isNotBlank() == true

    suspend fun submitAwait(): Boolean {
        val values = _fieldValues.value
        val problem = values[TechSupportField.PROBLEM]?.text?.trim().orEmpty()
        if (problem.isEmpty()) return false
        val now = System.currentTimeMillis()
        val request = TechSupportRequest(
            id = "ts-${UUID.randomUUID()}",
            reporterName = values[TechSupportField.NAME]?.text?.trim().orEmpty(),
            problem = problem,
            createdAt = now,
        )
        withContext(Dispatchers.IO) {
            container.techSupportStore.add(request)
        }
        reset()
        return true
    }

    fun reset() {
        _fieldValues.value = TechSupportField.entries.associateWith { TextFieldValue("") }
        _focusedField.value = TechSupportField.NAME
    }
}

private fun TextFieldValue.insertAt(insertion: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val newText = buildString {
        append(text, 0, start)
        append(insertion)
        append(text, end, text.length)
    }
    val cursor = start + insertion.length
    return TextFieldValue(text = newText, selection = TextRange(cursor))
}

private fun TextFieldValue.deleteBack(): TextFieldValue {
    if (text.isEmpty()) return this
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    if (start != end) {
        val newText = buildString {
            append(text, 0, start)
            append(text, end, text.length)
        }
        return TextFieldValue(text = newText, selection = TextRange(start))
    }
    if (start == 0) return this
    val newText = buildString {
        append(text, 0, start - 1)
        append(text, start, text.length)
    }
    return TextFieldValue(text = newText, selection = TextRange(start - 1))
}
