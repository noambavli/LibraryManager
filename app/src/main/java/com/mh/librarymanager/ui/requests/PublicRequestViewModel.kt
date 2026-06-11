package com.mh.librarymanager.ui.requests

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.PublicRequest
import com.mh.librarymanager.domain.RequestStatus
import com.mh.librarymanager.ui.search.KeyAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/** Typed fields on the public request form, driven by the in-app keyboard. */
enum class RequestField { NAME, BOOK, DETAILS }

/**
 * View-model backing the public "request a book" form.
 *
 * Uses the same focused-field + in-app Hebrew keyboard pattern as search, so
 * the system IME never needs to appear. Submission writes straight to
 * [com.mh.librarymanager.data.store.PublicRequestStore] with status RECEIVED.
 */
class PublicRequestViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    private val _fieldValues: MutableStateFlow<Map<RequestField, TextFieldValue>> =
        MutableStateFlow(RequestField.entries.associateWith { TextFieldValue("") })
    val fieldValues: StateFlow<Map<RequestField, TextFieldValue>> = _fieldValues.asStateFlow()

    private val _focusedField = MutableStateFlow(RequestField.NAME)
    val focusedField: StateFlow<RequestField> = _focusedField.asStateFlow()

    fun setValue(field: RequestField, value: TextFieldValue) {
        _fieldValues.value = _fieldValues.value.toMutableMap().also { it[field] = value }
    }

    fun setFocused(field: RequestField) {
        _focusedField.value = field
    }

    fun handleKey(key: KeyAction) {
        val field = _focusedField.value
        val current = _fieldValues.value[field] ?: TextFieldValue("")
        val next = when (key) {
            is KeyAction.Insert -> current.insertAt(key.text)
            KeyAction.Backspace -> current.deleteBack()
            KeyAction.ClearField -> TextFieldValue("")
            KeyAction.ClearAll -> TextFieldValue("")
        }
        setValue(field, next)
    }

    /** Fills the name field with the supplied "anonymous" label and focuses the book field. */
    fun setAnonymous(label: String) {
        setValue(RequestField.NAME, TextFieldValue(label, TextRange(label.length)))
        _focusedField.value = RequestField.BOOK
    }

    /** True when the form has the minimum needed to submit (a book name). */
    fun canSubmit(): Boolean =
        _fieldValues.value[RequestField.BOOK]?.text?.isNotBlank() == true

    /** Persists the request. Returns false (and does nothing) if invalid. */
    suspend fun submitAwait(): Boolean {
        val values = _fieldValues.value
        val bookName = values[RequestField.BOOK]?.text?.trim().orEmpty()
        if (bookName.isEmpty()) return false
        val now = System.currentTimeMillis()
        val request = PublicRequest(
            id = "req-${UUID.randomUUID()}",
            requesterName = values[RequestField.NAME]?.text?.trim().orEmpty(),
            bookName = bookName,
            details = values[RequestField.DETAILS]?.text?.trim().orEmpty(),
            status = RequestStatus.RECEIVED,
            createdAt = now,
            updatedAt = now,
        )
        withContext(Dispatchers.IO) {
            container.requestStore.add(request)
        }
        reset()
        return true
    }

    /** Wipes the form so the next kiosk visitor starts with blank fields. */
    fun reset() {
        _fieldValues.value = RequestField.entries.associateWith { TextFieldValue("") }
        _focusedField.value = RequestField.NAME
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
