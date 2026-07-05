package com.mh.librarymanager.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.components.AppPaneDivider
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.PublicBackBar
import com.mh.librarymanager.ui.search.HebrewKeyboard
import com.mh.librarymanager.ui.search.KeyboardEditField
import com.mh.librarymanager.ui.search.SuppressPlatformKeyboardEffect
import kotlinx.coroutines.launch

/**
 * Public "request a book" screen reached from the home page.
 *
 * Left pane: the form (name + anonymous shortcut, book name, details, send).
 * Right pane: the in-app Hebrew keyboard. After a successful submit we show a
 * thank-you confirmation with the option to send another request.
 */
@Composable
fun PublicRequestScreen(
    viewModel: PublicRequestViewModel,
    onBack: () -> Unit,
) {
    val fieldValues by viewModel.fieldValues.collectAsStateWithLifecycle()
    val focusedField by viewModel.focusedField.collectAsStateWithLifecycle()
    var submitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val anonymousLabel = stringResource(R.string.request_anonymous_label)

    SuppressPlatformKeyboardEffect()

    DisposableEffect(Unit) {
        onDispose { viewModel.reset() }
    }

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            PublicBackBar(
                onBack = onBack,
                title = stringResource(R.string.requests_public_title),
            )

            if (submitted) {
                SentConfirmation(
                    onSendAnother = { submitted = false },
                    onBack = onBack,
                )
                return@Column
            }

            Row(modifier = Modifier.fillMaxSize()) {
            FormPane(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                fieldValues = fieldValues,
                focusedField = focusedField,
                canSubmit = fieldValues[RequestField.BOOK]?.text?.isNotBlank() == true,
                onSetValue = viewModel::setValue,
                onSetFocused = viewModel::setFocused,
                onAnonymous = { viewModel.setAnonymous(anonymousLabel) },
                onSend = {
                    scope.launch {
                        if (viewModel.submitAwait()) submitted = true
                    }
                },
            )

            AppPaneDivider()

            HebrewKeyboard(
                onKey = viewModel::handleKey,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            }
        }
    }
}

@Composable
private fun FormPane(
    modifier: Modifier,
    fieldValues: Map<RequestField, TextFieldValue>,
    focusedField: RequestField,
    canSubmit: Boolean,
    onSetValue: (RequestField, TextFieldValue) -> Unit,
    onSetFocused: (RequestField) -> Unit,
    onAnonymous: () -> Unit,
    onSend: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.requests_public_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 18.dp),
        )

        Row(verticalAlignment = Alignment.Bottom) {
            KeyboardEditField(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.request_field_name),
                value = fieldValues[RequestField.NAME] ?: TextFieldValue(""),
                onValueChange = { onSetValue(RequestField.NAME, it) },
                isActive = focusedField == RequestField.NAME,
                onFocus = { onSetFocused(RequestField.NAME) },
                onClear = {
                    onSetValue(RequestField.NAME, TextFieldValue(""))
                    onSetFocused(RequestField.NAME)
                },
            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(
                onClick = onAnonymous,
                modifier = Modifier.height(52.dp),
            ) {
                Text(stringResource(R.string.request_anonymous))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        KeyboardEditField(
            label = stringResource(R.string.request_field_book),
            value = fieldValues[RequestField.BOOK] ?: TextFieldValue(""),
            onValueChange = { onSetValue(RequestField.BOOK, it) },
            isActive = focusedField == RequestField.BOOK,
            onFocus = { onSetFocused(RequestField.BOOK) },
            onClear = {
                onSetValue(RequestField.BOOK, TextFieldValue(""))
                onSetFocused(RequestField.BOOK)
            },
        )

        Spacer(modifier = Modifier.height(14.dp))

        KeyboardEditField(
            label = stringResource(R.string.request_field_details),
            value = fieldValues[RequestField.DETAILS] ?: TextFieldValue(""),
            onValueChange = { onSetValue(RequestField.DETAILS, it) },
            isActive = focusedField == RequestField.DETAILS,
            onFocus = { onSetFocused(RequestField.DETAILS) },
            onClear = {
                onSetValue(RequestField.DETAILS, TextFieldValue(""))
                onSetFocused(RequestField.DETAILS)
            },
            singleLine = false,
            minHeight = 110.dp,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSend,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(
                text = stringResource(R.string.request_send),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (!canSubmit) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.request_book_required),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SentConfirmation(
    onSendAnother: () -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cs.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.width(460.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(cs.primary.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.displaySmall,
                        color = cs.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.request_sent_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.request_sent_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.back))
                    }
                    Button(onClick = onSendAnother) {
                        Text(stringResource(R.string.request_send_another))
                    }
                }
            }
        }
    }
}
