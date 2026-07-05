package com.mh.librarymanager.ui.support

import androidx.activity.compose.BackHandler
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

@Composable
fun TechSupportScreen(
    viewModel: TechSupportViewModel,
    onBack: () -> Unit,
) {
    val fieldValues by viewModel.fieldValues.collectAsStateWithLifecycle()
    val focusedField by viewModel.focusedField.collectAsStateWithLifecycle()
    var submitted by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()

    SuppressPlatformKeyboardEffect()

    fun attemptExit(action: () -> Unit) {
        if (viewModel.isDirty()) pendingExit = action else action()
    }
    BackHandler(enabled = true) { attemptExit(onBack) }

    DisposableEffect(Unit) {
        onDispose { viewModel.reset() }
    }

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            PublicBackBar(
                onBack = { attemptExit(onBack) },
                title = stringResource(R.string.tech_support_public_title),
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
                canSubmit = fieldValues[TechSupportField.PROBLEM]?.text?.isNotBlank() == true,
                onSetValue = viewModel::setValue,
                onSetFocused = viewModel::setFocused,
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

    pendingExit?.let { exit ->
        DiscardDialog(
            onStay = { pendingExit = null },
            onLeave = {
                pendingExit = null
                exit()
            },
        )
    }
}

@Composable
private fun FormPane(
    modifier: Modifier,
    fieldValues: Map<TechSupportField, TextFieldValue>,
    focusedField: TechSupportField,
    canSubmit: Boolean,
    onSetValue: (TechSupportField, TextFieldValue) -> Unit,
    onSetFocused: (TechSupportField) -> Unit,
    onSend: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.tech_support_public_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 18.dp),
        )

        KeyboardEditField(
            label = stringResource(R.string.tech_support_field_name),
            value = fieldValues[TechSupportField.NAME] ?: TextFieldValue(""),
            onValueChange = { onSetValue(TechSupportField.NAME, it) },
            isActive = focusedField == TechSupportField.NAME,
            onFocus = { onSetFocused(TechSupportField.NAME) },
            onClear = {
                onSetValue(TechSupportField.NAME, TextFieldValue(""))
                onSetFocused(TechSupportField.NAME)
            },
        )

        Spacer(modifier = Modifier.height(14.dp))

        KeyboardEditField(
            label = stringResource(R.string.tech_support_field_problem),
            value = fieldValues[TechSupportField.PROBLEM] ?: TextFieldValue(""),
            onValueChange = { onSetValue(TechSupportField.PROBLEM, it) },
            isActive = focusedField == TechSupportField.PROBLEM,
            onFocus = { onSetFocused(TechSupportField.PROBLEM) },
            onClear = {
                onSetValue(TechSupportField.PROBLEM, TextFieldValue(""))
                onSetFocused(TechSupportField.PROBLEM)
            },
            singleLine = false,
            minHeight = 130.dp,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSend,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(
                text = stringResource(R.string.tech_support_send),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (!canSubmit) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tech_support_problem_required),
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
                    text = stringResource(R.string.tech_support_sent_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.tech_support_sent_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.back))
                    }
                    Button(onClick = onSendAnother) {
                        Text(stringResource(R.string.tech_support_send_another))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscardDialog(onStay: () -> Unit, onLeave: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.discard_changes_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.discard_changes_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onStay) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onLeave) { Text(stringResource(R.string.discard_changes_confirm)) }
                }
            }
        }
    }
}
