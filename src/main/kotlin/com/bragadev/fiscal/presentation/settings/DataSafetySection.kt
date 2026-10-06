package com.bragadev.fiscal.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.pickFiles
import com.bragadev.fiscal.presentation.components.pickSaveFile
import java.time.LocalDate

/** Configurações → "Dados e segurança": backups, cópia de segurança dos dados e diagnóstico. */
@Composable
fun DataSafetySection(viewModel: DataSafetyViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackupsBlock(state, viewModel)
        ExportImportBlock(viewModel)
        DiagnosticsBlock(viewModel)
        state.message?.let { Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
    }
    state.confirmation?.let { ConfirmationDialog(it, state, viewModel) }
}

@Composable
private fun BackupsBlock(state: DataSafetyUiState, viewModel: DataSafetyViewModel) = Block(Strings.BACKUPS_TITLE, Strings.BACKUPS_EXPLANATION) {
    state.backupStats?.let { Text(Strings.backupStats(it.fileCount, it.totalBytes), fontWeight = FontWeight.SemiBold) }
    Row(
        Modifier.fillMaxWidth().toggleable(state.autoCleanBackups, onValueChange = viewModel::onAutoCleanChanged),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = state.autoCleanBackups, onCheckedChange = viewModel::onAutoCleanChanged)
        Text(Strings.AUTO_CLEAN)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppSettings.BACKUP_RETENTION_OPTIONS.forEach { days ->
            FilterChip(
                selected = state.backupRetentionDays == days,
                onClick = { viewModel.onRetentionChanged(days) },
                label = { Text("$days ${Strings.DAYS}") },
            )
        }
    }
    OutlinedButton(onClick = viewModel::onCleanBackupsRequested, enabled = (state.backupStats?.fileCount ?: 0) > 0) {
        Text(Strings.CLEAN_NOW)
    }
}

@Composable
private fun ExportImportBlock(viewModel: DataSafetyViewModel) = Block(Strings.EXPORT_IMPORT_TITLE, Strings.EXPORT_IMPORT_EXPLANATION) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            pickSaveFile(Strings.EXPORT_PICKER_TITLE, null, Strings.exportFileName(LocalDate.now().toString()), Strings.BACKUP_FILE_FILTER, "db")
                ?.let(viewModel::onExport)
        }) { Text(Strings.EXPORT_DATA) }
        OutlinedButton(onClick = {
            pickFiles(Strings.IMPORT_PICKER_TITLE, null, Strings.BACKUP_FILE_FILTER, listOf("db")).firstOrNull()
                ?.let(viewModel::onImportSelected)
        }) { Text(Strings.IMPORT_DATA) }
    }
}

@Composable
private fun DiagnosticsBlock(viewModel: DataSafetyViewModel) = Block(Strings.DIAGNOSTICS_TITLE, Strings.DIAGNOSTICS_EXPLANATION) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::onCopyDiagnostics) { Text(Strings.COPY_DIAGNOSTICS) }
        if (viewModel.logFolder != null) TextButton(onClick = viewModel::onOpenLogs) { Text(Strings.OPEN_LOG_FOLDER) }
    }
}

@Composable
private fun Block(title: String, explanation: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ConfirmationDialog(confirmation: DataSafetyConfirmation, state: DataSafetyUiState, viewModel: DataSafetyViewModel) {
    val (title, body) = when (confirmation) {
        DataSafetyConfirmation.CleanBackups -> Strings.CLEAN_CONFIRM_TITLE to Strings.cleanConfirmBody(state.backupRetentionDays)
        is DataSafetyConfirmation.Import -> Strings.IMPORT_CONFIRM_TITLE to Strings.IMPORT_CONFIRM_BODY
    }
    AlertDialog(
        onDismissRequest = viewModel::onDismissConfirmation,
        title = { Text(title) },
        text = { Text(body, Modifier.width(460.dp).padding(top = 4.dp)) },
        dismissButton = { TextButton(onClick = viewModel::onDismissConfirmation) { Text(Strings.CANCEL) } },
        confirmButton = { Button(onClick = viewModel::onConfirmed) { Text(Strings.CONFIRM) } },
    )
}
