package com.bragadev.fiscal.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.repository.DiagnosticsRepository
import com.bragadev.fiscal.presentation.components.DesktopActions
import com.bragadev.fiscal.presentation.components.dialogKeys
import org.koin.compose.koinInject

/** "Algo deu errado": explica sem termos técnicos e oferece copiar o diagnóstico e abrir os logs. */
@Composable
fun UnexpectedErrorDialog() {
    val error by UnexpectedErrors.current.collectAsState()
    val current = error ?: return
    val diagnostics = koinInject<DiagnosticsRepository>()
    var copied by remember(current) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = UnexpectedErrors::dismiss,
        modifier = Modifier.dialogKeys(UnexpectedErrors::dismiss, UnexpectedErrors::dismiss),
        title = { Text(Strings.UNEXPECTED_ERROR_TITLE) },
        text = {
            Column(Modifier.width(480.dp)) {
                Text(Strings.UNEXPECTED_ERROR_BODY)
                if (copied) Text(Strings.DIAGNOSTICS_COPIED, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                diagnostics.logFolder?.let { folder ->
                    TextButton(onClick = { DesktopActions.openFolder(folder) }) { Text(Strings.OPEN_LOG_FOLDER) }
                }
                OutlinedButton(onClick = {
                    DesktopActions.copyToClipboard(diagnostics.report(current))
                    copied = true
                }) { Text(Strings.COPY_DIAGNOSTICS) }
            }
        },
        confirmButton = { Button(onClick = UnexpectedErrors::dismiss) { Text(Strings.CLOSE_TOOL) } },
    )
}
