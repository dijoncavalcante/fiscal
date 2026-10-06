package com.bragadev.fiscal.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.OperationType
import com.bragadev.fiscal.domain.model.UndoBlockReason
import com.bragadev.fiscal.domain.usecase.HistoryEntry
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.toUserMessage
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.IconText
import com.bragadev.fiscal.presentation.components.StatusColors
import com.bragadev.fiscal.presentation.components.dialogKeys
import java.nio.file.Path

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.onOpen() }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(Strings.BACK, Modifier.padding(start = 6.dp))
            }
            Text(Strings.HISTORY, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            Strings.HISTORY_EXPLANATION,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 12.dp),
        )
        Row(Modifier.padding(top = 12.dp, start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = { Text(Strings.HISTORY_SEARCH) },
                singleLine = true,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            )
            Text(
                Strings.historyCount(state.visible.size, state.entries.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        state.message?.let {
            Text(
                it,
                color = if (state.isError) MaterialTheme.colorScheme.error else StatusColors.Positive,
                modifier = Modifier.padding(top = 8.dp, start = 12.dp),
            )
        }
        if (state.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))

        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().weight(1f),
        ) {
            when {
                state.isLoading -> Centered(Strings.LOADING)
                state.entries.isEmpty() -> Centered(Strings.HISTORY_EMPTY)
                state.visible.isEmpty() -> Centered(Strings.NO_SEARCH_RESULTS)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.visible, key = { it.operation.id }) { entry ->
                        HistoryRow(entry, enabled = !state.isWorking, onUndo = { viewModel.onUndoRequested(entry) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    state.confirmUndo?.let { entry -> ConfirmUndoDialog(entry, viewModel::onUndoConfirmed, viewModel::onDismissUndo) }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, enabled: Boolean, onUndo: () -> Unit) {
    val operation = entry.operation
    val from = Path.of(operation.originalPath).parent
    val to = Path.of(operation.newPath).parent
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(150.dp)) {
            Text(HistoryViewModel.formatDate(entry), style = MaterialTheme.typography.bodySmall)
            val (icon, label) = if (operation.type == OperationType.MOVE) AppIcons.FolderOpen to Strings.HISTORY_MOVED else AppIcons.Pencil to Strings.HISTORY_RENAMED
            IconText(icon, label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, iconSize = 14.dp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(operation.newName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Strings.historyWasNamed(operation.originalName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val folders = if (from == to) Strings.historyFolder(to.toString()) else Strings.historyFromTo(from.toString(), to.toString())
            Text(
                folders,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (operation.backupPath != null) {
                Text(Strings.HISTORY_REPLACED, style = MaterialTheme.typography.labelSmall, color = StatusColors.Warning)
            }
        }
        Box(Modifier.width(260.dp), contentAlignment = Alignment.CenterEnd) {
            when {
                operation.undone -> IconText(
                    AppIcons.Undo,
                    Strings.HISTORY_UNDONE,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconSize = 14.dp,
                )
                entry.canUndo -> OutlinedButton(onClick = onUndo, enabled = enabled) {
                    Icon(AppIcons.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(Strings.UNDO, Modifier.padding(start = 6.dp))
                }
                else -> Text(
                    blockedText(entry.undoBlock),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
        }
    }
}

/** Motivo curto para a coluna da direita; a mensagem completa é a mesma do "Desfazer" da tela principal. */
private fun blockedText(error: FileOperationError?): String = when ((error as? FileOperationError.UndoNotPossible)?.reason) {
    UndoBlockReason.CURRENT_FILE_MISSING -> Strings.HISTORY_FILE_CHANGED
    UndoBlockReason.ORIGINAL_LOCATION_OCCUPIED -> Strings.HISTORY_ORIGINAL_OCCUPIED
    else -> error?.toUserMessage().orEmpty()
}

@Composable
private fun ConfirmUndoDialog(entry: HistoryEntry, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val operation = entry.operation
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogKeys(onConfirm, onDismiss),
        title = { Text(Strings.HISTORY_CONFIRM_TITLE) },
        text = {
            Column(Modifier.width(500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.historyConfirmBody(operation.newName, operation.originalName, Path.of(operation.originalPath).parent.toString()))
                if (operation.backupPath != null) Text(Strings.HISTORY_CONFIRM_BACKUP, color = StatusColors.Warning)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
        confirmButton = { Button(onClick = onConfirm) { Text(Strings.UNDO) } },
    )
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
