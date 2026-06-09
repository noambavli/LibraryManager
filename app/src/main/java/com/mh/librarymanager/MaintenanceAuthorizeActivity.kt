package com.mh.librarymanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mh.librarymanager.ui.theme.LibraryManagerTheme
import kotlinx.coroutines.delay

/**
 * Full-screen technician instructions while kiosk is briefly paused.
 */
class MaintenanceAuthorizeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UsbMaintenance.applyUsbDefaults(this)

        setContent {
            LibraryManagerTheme {
                MaintenanceAuthorizeScreen(
                    remainingMs = { MaintenanceMode.remainingMs(this) },
                    onFinished = {
                        MaintenanceMode.exit(this)
                        finish()
                    },
                )
            }
        }
    }

}

@Composable
private fun MaintenanceAuthorizeScreen(
    remainingMs: () -> Long,
    onFinished: () -> Unit,
) {
    var secondsLeft by mutableLongStateOf(remainingMs() / 1000L)

    LaunchedEffect(Unit) {
        while (secondsLeft > 0L) {
            delay(1000L)
            secondsLeft = remainingMs() / 1000L
        }
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF3CD))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.pc_authorize_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Color(0xFF7A4F01),
        )
        Text(
            text = stringResource(R.string.pc_authorize_body),
            modifier = Modifier.padding(top = 24.dp),
            fontSize = 22.sp,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center,
            color = Color(0xFF333333),
        )
        Text(
            text = stringResource(R.string.pc_authorize_timer, secondsLeft),
            modifier = Modifier.padding(top = 32.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF7A4F01),
        )
    }
}
