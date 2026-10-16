package com.bragadev.fiscal.presentation.documentactions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.rules.FileNameRules
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.LabeledValue
import com.bragadev.fiscal.presentation.components.PreviewDialog

@Composable
fun DocumentActionsDialogHost(state: DocumentActionsUiState, viewModel: DocumentActionsViewModel) {
    when (val dialog = state.dialog) {
        is DocumentActionDialog.Rename -> RenameDialog(dialog, state.isWorking, viewModel)
        is DocumentActionDialog.Delete -> DeleteDialog(dialog, state.isWorking, viewModel)
        null -> Unit
    }
}

@Composable
private fun RenameDialog(dialog: DocumentActionDialog.Rename, isWorking: Boolean, viewModel: DocumentActionsViewModel) {
    val extension = FileNameRules.extension(dialog.file.fileName.toString())
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
        LabeledValue(Strings.FOLDER, dialog.file.parent.toString())
        NameField(dialog, extension, viewModel::onRenameNameChanged)
        dialog.target?.let { LabeledValue(Strings.NAME_PREVIEW, it.fileName.toString()) }
    }
}

/** Campo do nome já focado e com o texto selecionado (como o F2 do Explorer): é só digitar o nome novo. */
@Composable
private fun NameField(dialog: DocumentActionDialog.Rename, extension: String, onChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var value by remember(dialog.file) { mutableStateOf(TextFieldValue(dialog.name, TextRange(0, dialog.name.length))) }
    LaunchedEffect(focusRequester) { runCatching { focusRequester.requestFocus() } }
    OutlinedTextField(
        value = value,
        onValueChange = { edited ->
            value = edited
            if (edited.text != dialog.name) onChange(edited.text)
        },
        label = { Text(Strings.NEW_FILE_NAME) },
        suffix = extension.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        singleLine = true,
        isError = dialog.inputError != null,
        supportingText = { Text(dialog.inputError ?: Strings.nameWithExtension(extension)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focusRequester),
    )
}

@Composable
private fun DeleteDialog(dialog: DocumentActionDialog.Delete, isWorking: Boolean, viewModel: DocumentActionsViewModel) {
    PreviewDialog(
        title = Strings.DELETE_FILE_TITLE,
        file = dialog.file,
        onDismiss = viewModel::dismissDialog,
        onConfirm = if (!isWorking) viewModel::onDeleteConfirmed else null,
        buttons = {
            TextButton(onClick = viewModel::dismissDialog) { Text(Strings.CANCEL) }
            Button(
                onClick = viewModel::onDeleteConfirmed,
                enabled = !isWorking,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(Strings.DELETE_FILE) }
        },
    ) {
        Text(Strings.DELETE_EXPLANATION, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
        LabeledValue(Strings.CURRENT_FILE, dialog.file.fileName.toString())
        LabeledValue(Strings.FOLDER, dialog.file.parent.toString())
    }
}
