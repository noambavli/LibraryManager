package com.mh.librarymanager.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.search.HebrewKeyboard
import com.mh.librarymanager.ui.search.KeyAction
import com.mh.librarymanager.ui.search.KeyboardEditField

@Composable
fun TypedConfirmDialog(
    title: String,
    body: String,
    requiredPhrase: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(true) }
    val matches = text.text.trim() == requiredPhrase

    fun handleKey(action: KeyAction) {
        text = when (action) {
            is KeyAction.Insert -> text.insertAt(action.text)
            KeyAction.Backspace -> text.deleteBack()
            KeyAction.ClearField, KeyAction.ClearAll -> TextFieldValue("")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(560.dp).heightIn(max = 640.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                KeyboardEditField(
                    label = requiredPhrase,
                    value = text,
                    onValueChange = { text = it },
                    isActive = focused,
                    onFocus = { focused = true },
                    onClear = { text = TextFieldValue("") },
                )
                Spacer(modifier = Modifier.height(14.dp))
                HebrewKeyboard(
                    onKey = ::handleKey,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp).height(300.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onConfirmed,
                        enabled = matches,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.error,
                            contentColor = cs.onError,
                            disabledContainerColor = cs.error.copy(alpha = 0.35f),
                            disabledContentColor = cs.onError.copy(alpha = 0.5f),
                        ),
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}

private fun TextFieldValue.insertAt(chunk: String): TextFieldValue {
    val pos = selection.start.coerceIn(0, text.length)
    val next = text.substring(0, pos) + chunk + text.substring(pos)
    val cursor = pos + chunk.length
    return copy(text = next, selection = androidx.compose.ui.text.TextRange(cursor))
}

private fun TextFieldValue.deleteBack(): TextFieldValue {
    if (!selection.collapsed) {
        val start = selection.min
        val end = selection.max
        return copy(text = text.removeRange(start, end), selection = androidx.compose.ui.text.TextRange(start))
    }
    val pos = selection.start
    if (pos <= 0) return this
    return copy(text = text.removeRange(pos - 1, pos), selection = androidx.compose.ui.text.TextRange(pos - 1))
}
