package com.bragadev.fiscal.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.DragPayload
import com.bragadev.fiscal.presentation.components.FolderPathField
import com.bragadev.fiscal.presentation.components.Panel

/** Lado esquerdo: pasta do computador escolhida pelo usuário e os PDFs dela. */
@Composable
fun DocumentList(
    state: HomeUiState,
    onSelect: (Document) -> Unit,
    onChangeFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(title = Strings.DOCUMENTS, modifier = modifier) {
        FolderPathField(
            label = Strings.SOURCE_FOLDER_LABEL,
            path = state.sourceFolder,
            emptyText = Strings.SOURCE_FOLDER_EMPTY,
            editDescription = Strings.SOURCE_FOLDER_EDIT,
            onEdit = onChangeFolder,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        when {
            state.folderStatus == FolderStatus.NOT_SELECTED -> EmptyMessage(Strings.SOURCE_FOLDER_SELECT)
            state.folderStatus == FolderStatus.MISSING -> EmptyMessage(Strings.SOURCE_FOLDER_MISSING)
            state.isLoading && state.documents.isEmpty() -> EmptyMessage(Strings.LOADING)
            state.documents.isEmpty() -> EmptyMessage(Strings.NO_DOCUMENTS)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.documents, key = { it.path.toString() }) { document ->
                    DocumentRow(document, isSelected = document.path == state.selectedDocument?.path, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(document: Document, isSelected: Boolean, onSelect: (Document) -> Unit) {
    val background = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Text(
        text = "📄 ${document.name}",
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .dragAndDropSource { _ -> DragPayload.internalDocument(document.path) }
            .clickable { onSelect(document) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
