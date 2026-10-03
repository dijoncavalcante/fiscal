package com.bragadev.fiscal.presentation.monthfiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.rules.FileNameRules
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.LabeledValue
import com.bragadev.fiscal.presentation.components.PreviewDialog

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
        buttons = {
            TextButton(onClick = viewModel::dismissDialog) { Text(Strings.CANCEL) }
            Button(onClick = viewModel::onRenameConfirmed, enabled = dialog.canConfirm && !isWorking) { Text(Strings.RENAME) }
        },
    ) {
        LabeledValue(Strings.CURRENT_FILE, dialog.file.fileName.toString())
        LabeledValue(Strings.DESTINATION, dialog.file.parent.toString())
        OutlinedTextField(
            value = dialog.input,
            onValueChange = viewModel::onRenameInputChanged,
            label = {
                Text(if (dialog.keepsNumber) Strings.descriptionTitle(dialog.category.fileWord) else Strings.NEW_FILE_NAME)
            },
            singleLine = true,
            isError = dialog.inputError != null,
            supportingText = {
                Text(dialog.inputError ?: if (dialog.keepsNumber) Strings.KEEPS_NUMBER else FileNameRules.PDF_EXTENSION)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        dialog.plan?.let { LabeledValue(Strings.NAME_PREVIEW, finalName(dialog)) }
    }
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
