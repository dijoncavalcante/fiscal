package com.bragadev.fiscal.presentation.monthfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.LabeledValue
import com.bragadev.fiscal.presentation.components.PreviewDialog
import com.bragadev.fiscal.presentation.components.StatusColors

@Composable
fun MonthFilesDialogHost(state: MonthFilesUiState, viewModel: MonthFilesViewModel) {
    when (val dialog = state.dialog) {
        is MonthFileDialog.Rename -> RenameDialog(dialog, state.isWorking, viewModel)
        is MonthFileDialog.Remove -> RemoveDialog(dialog, state.isWorking, viewModel)
        is MonthFileDialog.Flag -> FlagDialog(dialog, viewModel)
        null -> Unit
    }
}

@Composable
private fun RenameDialog(dialog: MonthFileDialog.Rename, isWorking: Boolean, viewModel: MonthFilesViewModel) {
    PreviewDialog(
        title = Strings.RENAME_FILE_TITLE,
        file = dialog.file,
        onDismiss = viewModel::dismissDialog,
        onConfirm = if (dialog.canConfirm && !isWorking) viewModel::onRenameConfirmed else null,
        buttons = {
            TextButton(onClick = viewModel::dismissDialog) { Text(Strings.CANCEL) }
            Button(onClick = viewModel::onRenameConfirmed, enabled = dialog.canConfirm && !isWorking) { Text(Strings.RENAME) }
        },
    ) {
        LabeledValue(Strings.CURRENT_FILE, dialog.file.fileName.toString())
        LabeledValue(Strings.DESTINATION, dialog.file.parent.toString())
        if (dialog.numberedModeAvailable) ModeSelector(dialog.mode, viewModel::onRenameModeChanged)
        when (dialog.mode) {
            RenameMode.NUMBER_AND_DESCRIPTION -> NumberAndDescriptionFields(dialog, viewModel)
            RenameMode.FULL_NAME -> FullNameField(dialog, viewModel)
        }
        dialog.plan?.let { LabeledValue(Strings.NAME_PREVIEW, finalName(dialog)) }
        dialog.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StatusColors.Warning) }
    }
}

/** Despesas e Outros: escolher entre editar número + descrição ou o nome inteiro. */
@Composable
private fun ModeSelector(mode: RenameMode, onChange: (RenameMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        FilterChip(
            selected = mode == RenameMode.NUMBER_AND_DESCRIPTION,
            onClick = { onChange(RenameMode.NUMBER_AND_DESCRIPTION) },
            label = { Text(Strings.RENAME_MODE_NUMBERED) },
        )
        FilterChip(
            selected = mode == RenameMode.FULL_NAME,
            onClick = { onChange(RenameMode.FULL_NAME) },
            label = { Text(Strings.RENAME_MODE_FULL) },
        )
    }
}

/** Número da sequência (ex.: 3.2) e descrição lado a lado; o prefixo "Despesa - " é montado pelo app. */
@Composable
private fun NumberAndDescriptionFields(dialog: MonthFileDialog.Rename, viewModel: MonthFilesViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = dialog.number,
            onValueChange = viewModel::onRenameNumberChanged,
            label = { Text(Strings.NUMBER_LABEL) },
            singleLine = true,
            isError = dialog.inputError != null,
            modifier = Modifier.width(110.dp),
        )
        OutlinedTextField(
            value = dialog.description,
            onValueChange = viewModel::onRenameDescriptionChanged,
            label = { Text(Strings.DESCRIPTION_TITLE) },
            singleLine = true,
            isError = dialog.inputError != null,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        dialog.inputError ?: Strings.numberHint(dialog.category.number),
        style = MaterialTheme.typography.bodySmall,
        color = if (dialog.inputError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

@Composable
private fun FullNameField(dialog: MonthFileDialog.Rename, viewModel: MonthFilesViewModel) {
    OutlinedTextField(
        value = dialog.fullName,
        onValueChange = viewModel::onRenameFullNameChanged,
        label = { Text(Strings.NEW_FILE_NAME) },
        singleLine = true,
        isError = dialog.inputError != null,
        supportingText = { Text(dialog.inputError ?: Strings.FULL_NAME_HINT) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/** Nome que será usado; com cópia numerada automática, mostra o nome com "(2)". */
private fun finalName(dialog: MonthFileDialog.Rename): String {
    val plan = dialog.plan ?: return ""
    return if (plan.hasConflict) plan.numberedCopyName else plan.suggestedName
}

@Composable
private fun RemoveDialog(dialog: MonthFileDialog.Remove, isWorking: Boolean, viewModel: MonthFilesViewModel) {
    PreviewDialog(
        title = Strings.REMOVE_FROM_MONTH_TITLE,
        file = dialog.file,
        onDismiss = viewModel::dismissDialog,
        onConfirm = if (!isWorking) viewModel::onRemoveConfirmed else null,
        buttons = {
            TextButton(onClick = viewModel::dismissDialog) { Text(Strings.CANCEL) }
            Button(onClick = viewModel::onRemoveConfirmed, enabled = !isWorking) { Text(Strings.REMOVE) }
        },
    ) {
        Text(Strings.REMOVE_EXPLANATION, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
        LabeledValue(Strings.CURRENT_FILE, dialog.file.toString())
        LabeledValue(Strings.MOVE_TO, dialog.target.toString())
    }
}

@Composable
private fun FlagDialog(dialog: MonthFileDialog.Flag, viewModel: MonthFilesViewModel) {
    PreviewDialog(
        title = Strings.ISSUE_TITLE,
        file = dialog.file,
        onDismiss = viewModel::dismissDialog,
        onConfirm = if (dialog.note.isNotBlank()) viewModel::onFlagConfirmed else null,
        buttons = {
            TextButton(onClick = viewModel::dismissDialog) { Text(Strings.CANCEL) }
            Button(onClick = viewModel::onFlagConfirmed, enabled = dialog.note.isNotBlank()) { Text(Strings.MARK) }
        },
    ) {
        Text(Strings.ISSUE_EXPLANATION, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
        LabeledValue(Strings.CURRENT_FILE, dialog.file.fileName.toString())
        OutlinedTextField(
            value = dialog.note,
            onValueChange = viewModel::onFlagNoteChanged,
            label = { Text(Strings.ISSUE_NOTE) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
