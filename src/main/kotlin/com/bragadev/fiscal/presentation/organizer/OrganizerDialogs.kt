package com.bragadev.fiscal.presentation.organizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.DuplicateResolution
import com.bragadev.fiscal.domain.model.OrganizeMode
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.LabeledValue
import com.bragadev.fiscal.presentation.components.PreviewDialog

@Composable
fun OrganizerDialogHost(state: OrganizerUiState, viewModel: OrganizerViewModel) {
    when (val dialog = state.dialog) {
        is OrganizerDialog.Proposal -> ProposalDialog(dialog, state, viewModel)
        is OrganizerDialog.Conflict -> ConflictDialog(dialog, viewModel)
        is OrganizerDialog.Confirm -> ConfirmDialog(dialog, state, viewModel)
        null -> Unit
    }
}

@Composable
private fun ProposalDialog(dialog: OrganizerDialog.Proposal, state: OrganizerUiState, viewModel: OrganizerViewModel) {
    PreviewDialog(
        title = Strings.organizeTitle(dialog.category.label),
        file = dialog.source,
        onDismiss = viewModel::dismissDialog,
        buttons = {
            TextButton(onClick = viewModel::dismissDialog) { Text(Strings.CANCEL) }
            OutlinedButton(
                onClick = { viewModel.onModeChosen(OrganizeMode.RENAME_ONLY) },
                enabled = dialog.renamePlan != null,
            ) { Text(Strings.RENAME) }
            Button(
                onClick = { viewModel.onModeChosen(OrganizeMode.RENAME_AND_MOVE) },
                enabled = dialog.movePlan != null,
            ) { Text(Strings.RENAME_AND_MOVE) }
        },
    ) {
        LabeledValue(Strings.CURRENT_FILE, dialog.source.fileName.toString())
        if (dialog.needsDescription) DescriptionField(dialog, viewModel::onDescriptionChanged)
        dialog.movePlan?.let { plan ->
            LabeledValue(Strings.NEW_NAME, plan.suggestedName)
            MonthLine(state)
            LabeledValue(Strings.DESTINATION, plan.targetDirectory.toString())
        }
        dialog.renamePlan?.let { plan ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LabeledValue(Strings.CURRENT_FOLDER, plan.targetPath.toString())
        }
    }
}

@Composable
private fun ConflictDialog(dialog: OrganizerDialog.Conflict, viewModel: OrganizerViewModel) {
    var choice by remember(dialog) { mutableStateOf(DuplicateResolution.NUMBERED_COPY) }
    val options = listOf(
        DuplicateResolution.REPLACE to Strings.REPLACE,
        DuplicateResolution.NUMBERED_COPY to Strings.numberedCopy(dialog.plan.numberedCopyName),
        DuplicateResolution.CANCEL to Strings.CANCEL_OPERATION,
    )
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(Strings.CONFLICT_TITLE) },
        text = {
            Column(Modifier.width(460.dp)) {
                Text(Strings.conflictMessage(dialog.plan.suggestedName), style = MaterialTheme.typography.bodyLarge)
                Text(Strings.CONFLICT_CHOOSE, Modifier.padding(top = 12.dp, bottom = 4.dp))
                options.forEach { (resolution, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = choice == resolution, onClick = { choice = resolution }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == resolution, onClick = { choice = resolution })
                        Text(label)
                    }
                }
                if (choice == DuplicateResolution.REPLACE) {
                    Text(Strings.REPLACE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(Strings.CANCEL) } },
        confirmButton = { Button(onClick = { viewModel.onConflictResolved(choice) }) { Text(Strings.CONTINUE) } },
    )
}

@Composable
private fun ConfirmDialog(dialog: OrganizerDialog.Confirm, state: OrganizerUiState, viewModel: OrganizerViewModel) {
    PreviewDialog(
        title = Strings.CONFIRM_TITLE,
        file = dialog.plan.source,
        onDismiss = viewModel::dismissDialog,
        buttons = {
            TextButton(onClick = viewModel::dismissDialog) { Text(Strings.CANCEL) }
            Button(onClick = viewModel::onConfirmed) { Text(Strings.CONFIRM) }
        },
    ) {
        LabeledValue(Strings.CURRENT_FILE, dialog.plan.currentName)
        if (dialog.plan.mode == OrganizeMode.RENAME_AND_MOVE) MonthLine(state)
        LabeledValue(Strings.DESTINATION, dialog.plan.targetDirectory.toString())
        LabeledValue(Strings.NEW_NAME, dialog.finalName)
        if (dialog.resolution == DuplicateResolution.REPLACE) {
            Text(Strings.REPLACE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Mês de destino, repetido nos diálogos para evitar organizar no mês errado. */
@Composable
private fun MonthLine(state: OrganizerUiState) {
    state.monthFolder?.detectedMonth?.let { LabeledValue(Strings.MONTH, Strings.monthTitle(it.month)) }
}

/** Campo da descrição (ex.: Despesas): o nome final é recalculado enquanto o usuário digita. */
@Composable
private fun DescriptionField(dialog: OrganizerDialog.Proposal, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = dialog.description.orEmpty(),
        onValueChange = onChange,
        label = { Text(Strings.descriptionTitle(dialog.category.fileWord)) },
        placeholder = { Text(Strings.DESCRIPTION_HINT) },
        singleLine = true,
        isError = dialog.inputError != null,
        supportingText = dialog.inputError?.let { error -> { Text(error) } },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
