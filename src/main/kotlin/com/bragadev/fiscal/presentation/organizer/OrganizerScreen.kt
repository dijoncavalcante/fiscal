package com.bragadev.fiscal.presentation.organizer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.AccountGroup
import com.bragadev.fiscal.domain.model.CategoryNode
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.DragPayload
import com.bragadev.fiscal.presentation.components.Panel
import java.nio.file.Path

private const val LOCKED_ALPHA = 0.45f

/**
 * Lado direito: mês em edição e árvore de categorias. Cada categoria aceita PDFs arrastados
 * do Windows Explorer ou da lista; com o mês bloqueado, as categorias ficam desativadas.
 */
@Composable
fun OrganizerScreen(
    state: OrganizerUiState,
    selectedDocument: Path?,
    onDrop: (paths: List<Path>, categoryId: String) -> Unit,
    onCategoryClickedWithoutDocument: () -> Unit,
    onEditMonthFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(title = Strings.MONTH_PANEL, modifier = modifier) {
        MonthHeader(state.monthFolder, state.firstEditableMonth, onEditMonthFolder)
        HorizontalDivider()
        Text(
            text = Strings.DROP_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        val onCategoryClick: (DocumentCategory) -> Unit = { category ->
            if (selectedDocument != null) onDrop(listOf(selectedDocument), category.id) else onCategoryClickedWithoutDocument()
        }
        val enabled = state.canOrganize
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp).alpha(if (enabled) 1f else LOCKED_ALPHA),
        ) {
            state.groups.forEach { group -> accountGroup(group, enabled, onDrop, onCategoryClick) }
        }
    }
}

private fun LazyListScope.accountGroup(
    group: AccountGroup,
    enabled: Boolean,
    onDrop: (List<Path>, String) -> Unit,
    onClick: (DocumentCategory) -> Unit,
) {
    val isCatchAll = group.accountType.isCatchAll
    if (!isCatchAll) {
        item(key = "account-${group.accountType}") {
            Text(
                text = "📁 ${group.accountType.displayName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
            )
        }
    }
    items(flatten(group.nodes, depth = if (isCatchAll) 0 else 1), key = { it.node.category.id }) { entry ->
        CategoryRow(entry, enabled, onDrop, onClick)
    }
}

private data class TreeEntry(val node: CategoryNode, val depth: Int)

private fun flatten(nodes: List<CategoryNode>, depth: Int): List<TreeEntry> =
    nodes.flatMap { node -> listOf(TreeEntry(node, depth)) + flatten(node.children, depth + 1) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    entry: TreeEntry,
    enabled: Boolean,
    onDrop: (List<Path>, String) -> Unit,
    onClick: (DocumentCategory) -> Unit,
) {
    val category = entry.node.category
    val currentOnDrop by rememberUpdatedState(onDrop)
    var isDragOver by remember { mutableStateOf(false) }
    val dropTarget = remember(category.id) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isDragOver = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragOver = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragOver = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragOver = false
                val paths = DragPayload.paths(event)
                if (paths.isEmpty()) return false
                currentOnDrop(paths, category.id)
                return true
            }
        }
    }

    val showDropHighlight = isDragOver && enabled
    val shape = RoundedCornerShape(6.dp)
    val highlight = if (showDropHighlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val borderColor = if (showDropHighlight) MaterialTheme.colorScheme.primary else Color.Transparent
    val icon = if (entry.node.children.isNotEmpty()) "📁" else "📄"

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = (entry.depth * 16).dp, top = 1.dp, bottom = 1.dp)
            .background(highlight, shape)
            .border(1.dp, borderColor, shape)
            // Mesmo bloqueada, a categoria recebe o arquivo para avisar o motivo; o ViewModel recusa.
            .dragAndDropTarget(shouldStartDragAndDrop = DragPayload::canAccept, target = dropTarget)
            .clickable { onClick(category) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text("$icon ${category.label}", style = MaterialTheme.typography.bodyMedium)
        if (showDropHighlight) {
            Text(Strings.DROP_HERE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
