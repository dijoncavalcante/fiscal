package com.bragadev.fiscal.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.DragPayload
import com.bragadev.fiscal.presentation.components.Panel

@Composable
fun DocumentList(
    documents: List<DocumentListItem>,
    selected: Document?,
    isLoading: Boolean,
    onSelect: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(title = Strings.DOCUMENTS, modifier = modifier) {
        when {
            isLoading && documents.isEmpty() -> EmptyMessage(Strings.LOADING)
            documents.isEmpty() -> EmptyMessage(Strings.NO_DOCUMENTS)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(documents, key = { it.document.path.toString() }) { item ->
                    DocumentRow(item, isSelected = item.document.path == selected?.path, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(item: DocumentListItem, isSelected: Boolean, onSelect: (Document) -> Unit) {
    val background = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .dragAndDropSource { _ -> DragPayload.internalDocument(item.document.path) }
            .clickable { onSelect(item.document) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text("📄 ${item.document.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            item.folderLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
