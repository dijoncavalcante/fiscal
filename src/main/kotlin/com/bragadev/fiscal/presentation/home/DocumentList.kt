package com.bragadev.fiscal.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.DocumentSort
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.DragPayload
import com.bragadev.fiscal.presentation.components.FolderPathField
import com.bragadev.fiscal.presentation.components.Panel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val modifiedFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

/** Lado esquerdo: pasta do computador escolhida pelo usuário e os PDFs dela. */
@Composable
fun DocumentList(
    state: HomeUiState,
    onSelect: (Document) -> Unit,
    onChangeFolder: () -> Unit,
    onRefresh: () -> Unit,
    onSortChanged: (DocumentSort) -> Unit,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(title = Strings.DOCUMENTS, modifier = modifier) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
            FolderPathField(
                label = Strings.SOURCE_FOLDER_LABEL,
                path = state.sourceFolder,
                emptyText = Strings.SOURCE_FOLDER_EMPTY,
                editDescription = Strings.SOURCE_FOLDER_EDIT,
                onEdit = onChangeFolder,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(AppIcons.Refresh, contentDescription = Strings.REFRESH_FOLDER, tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (state.folderStatus == FolderStatus.READY) ListControls(state, onSortChanged, onQueryChanged)
        HorizontalDivider()
        when {
            state.folderStatus == FolderStatus.NOT_SELECTED -> EmptyMessage(Strings.SOURCE_FOLDER_SELECT)
            state.folderStatus == FolderStatus.MISSING -> EmptyMessage(Strings.SOURCE_FOLDER_MISSING)
            state.isLoading && state.documents.isEmpty() -> EmptyMessage(Strings.LOADING)
            state.documents.isEmpty() -> EmptyMessage(Strings.NO_DOCUMENTS)
            state.visibleDocuments.isEmpty() -> EmptyMessage(Strings.NO_SEARCH_RESULTS)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.visibleDocuments, key = { it.path.toString() }) { document ->
                    DocumentRow(document, isSelected = document.path == state.selectedDocument?.path, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun ListControls(state: HomeUiState, onSortChanged: (DocumentSort) -> Unit, onQueryChanged: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            placeholder = { Text(Strings.SEARCH) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.sort == DocumentSort.MODIFIED_NEWEST_FIRST,
                onClick = { onSortChanged(DocumentSort.MODIFIED_NEWEST_FIRST) },
                label = { Text(Strings.SORT_NEWEST) },
            )
            FilterChip(
                selected = state.sort == DocumentSort.NAME,
                onClick = { onSortChanged(DocumentSort.NAME) },
                label = { Text(Strings.SORT_NAME) },
            )
        }
    }
}

@Composable
private fun DocumentRow(document: Document, isSelected: Boolean, onSelect: (Document) -> Unit) {
    val background = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .dragAndDropSource { _ -> DragPayload.internalDocument(document.path) }
            .clickable { onSelect(document) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (document.isImage) AppIcons.Image else AppIcons.Document,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp, end = 6.dp).size(16.dp),
            )
            Text(text = document.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = Strings.modifiedAt(modifiedFormat.format(document.lastModified)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp),
        )
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
