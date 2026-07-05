package com.mh.librarymanager.ui.management

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import com.mh.librarymanager.ui.text.stringResource

/**
 * Developer-only dashboard, reachable only after entering the developer key.
 *
 * It is the ONLY place in the app that can install an APK (app update) and the
 * only place the management password can be changed or reset. Regular
 * management screens intentionally expose neither.
 */
@Composable
fun DeveloperDashboardScreen(
    viewModel: WindowsToolViewModel,
    session: ManagementSession,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val opStatus by viewModel.opStatus.collectAsStateWithLifecycle()

    var pendingApk by remember { mutableStateOf<Uri?>(null) }
    var changingPassword by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    // Kept in state so the "current password" label refreshes after a change.
    var currentPassword by remember { mutableStateOf(session.currentManagementPassword()) }
    var passwordFeedback by remember { mutableStateOf<String?>(null) }

    val inExternalFlow = pendingApk != null ||
        changingPassword ||
        confirmReset ||
        opStatus !is WindowsToolViewModel.OpStatus.Idle

    LaunchedEffect(inExternalFlow) {
        if (!inExternalFlow && session.isExternalTaskActive()) {
            session.endExternalTask()
        }
    }

    // Keep the kiosk from auto-logging-out while an install is running.
    val busy = opStatus is WindowsToolViewModel.OpStatus.Working
    DisposableEffect(busy) {
        if (busy) session.beginExternalTask()
        onDispose { if (busy) session.endExternalTask() }
    }

    // Safety net: if the screen is left (e.g. hardware back while the picker
    // confirm dialog is open), release any external-task hold started here so
    // idle auto-logout can never get stuck permanently off.
    DisposableEffect(Unit) {
        onDispose { if (session.isExternalTaskActive()) session.endExternalTask() }
    }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingApk = uri
        } else {
            session.endExternalTask()
        }
    }
    val apkTypes = arrayOf(
        "application/vnd.android.package-archive",
        "application/octet-stream",
        "*/*",
    )

    AppScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ManagementHeader(
                    title = stringResource(R.string.developer_title),
                    onBack = onBack,
                    onLogout = onLogout,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                ) {
                    Column(modifier = Modifier.widthIn(max = 720.dp)) {
                        DevSection(
                            title = stringResource(R.string.developer_app_update_section),
                            subtitle = stringResource(R.string.developer_app_update_desc),
                        ) {
                            DevActionButton(
                                text = stringResource(R.string.developer_app_update_button),
                                onClick = {
                                    session.beginExternalTask()
                                    apkPicker.launch(apkTypes)
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        DevSection(
                            title = stringResource(R.string.developer_password_section),
                            subtitle = stringResource(R.string.developer_password_desc),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.developer_password_current,
                                    currentPassword,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.TextPrimary,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            DevActionButton(
                                text = stringResource(R.string.developer_password_change_button),
                                onClick = {
                                    passwordFeedback = null
                                    changingPassword = true
                                },
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            DevActionButton(
                                text = stringResource(R.string.developer_password_reset_button),
                                onClick = {
                                    passwordFeedback = null
                                    confirmReset = true
                                },
                            )
                            passwordFeedback?.let { msg ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        text = msg,
                                        modifier = Modifier.padding(14.dp),
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        when (val status = opStatus) {
                            is WindowsToolViewModel.OpStatus.Working -> {
                                Spacer(modifier = Modifier.height(16.dp))
                                DevWorkingCard(message = status.message)
                            }
                            is WindowsToolViewModel.OpStatus.Success -> {
                                Spacer(modifier = Modifier.height(16.dp))
                                DevFeedbackCard(
                                    message = status.message,
                                    isError = false,
                                    onDismiss = { viewModel.dismissStatus() },
                                )
                            }
                            is WindowsToolViewModel.OpStatus.Error -> {
                                Spacer(modifier = Modifier.height(16.dp))
                                DevFeedbackCard(
                                    message = status.message,
                                    isError = true,
                                    onDismiss = { viewModel.dismissStatus() },
                                )
                            }
                            WindowsToolViewModel.OpStatus.Idle -> Unit
                        }

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }

    pendingApk?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                pendingApk = null
                session.endExternalTask()
            },
            title = { Text(stringResource(R.string.developer_app_update_confirm_title)) },
            text = { Text(stringResource(R.string.developer_app_update_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingApk = null
                    viewModel.installAppUpdate(uri)
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingApk = null
                    session.endExternalTask()
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (changingPassword) {
        val successTemplate = stringResource(R.string.developer_password_change_success)
        ChangePasswordDialog(
            onDismiss = { changingPassword = false },
            onConfirm = { newCode ->
                if (session.setManagementPassword(newCode)) {
                    currentPassword = session.currentManagementPassword()
                    passwordFeedback = successTemplate.format(currentPassword)
                }
                changingPassword = false
            },
        )
    }

    if (confirmReset) {
        val resetTemplate = stringResource(R.string.developer_password_reset_success)
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.developer_password_reset_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.developer_password_reset_confirm_body,
                        ManagementSession.DEFAULT_MANAGEMENT_PASSWORD,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    session.resetManagementPassword()
                    currentPassword = session.currentManagementPassword()
                    passwordFeedback = resetTemplate.format(currentPassword)
                    confirmReset = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.developer_password_change_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.developer_password_change_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (code.isEmpty()) "—" else code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Keypad(
                    modifier = Modifier.fillMaxWidth(),
                    onDigit = { d -> if (code.length < ManagementSession.CODE_MAX_LEN) code += d },
                    onBackspace = { if (code.isNotEmpty()) code = code.dropLast(1) },
                    onSubmit = { if (code.isNotEmpty()) onConfirm(code) },
                    submitEnabled = code.isNotEmpty(),
                    submitLabel = stringResource(R.string.developer_password_change_save),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DevSection(
    title: String,
    subtitle: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun DevActionButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DevWorkingCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .height(24.dp)
                    .padding(end = 14.dp),
            )
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DevFeedbackCard(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) cs.errorContainer else cs.primaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(message, fontWeight = FontWeight.Medium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.windows_tool_dismiss)) }
        }
    }
}
