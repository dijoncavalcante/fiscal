package com.bragadev.fiscal.presentation.organizer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.AccountGroup
import com.bragadev.fiscal.domain.model.CategoryNode
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.MonthChecklist
import com.bragadev.fiscal.domain.model.NamingRule
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.DragPayload
import com.bragadev.fiscal.presentation.components.Panel
import com.bragadev.fiscal.presentation.components.StatusColors
import java.nio.file.Path

private const val LOCKED_ALPHA = 0.45f
private const val UNMATCHED_GROUP_KEY = "unmatched"

/**
 * Lado direito: mês em edição e categorias agrupadas por conta (cada grupo pode ser recolhido).
 * Cada categoria mostra se já existe arquivo na pasta do mês ou se está faltando, e aceita PDFs
 * arrastados do Windows Explorer ou da lista; com o mês bloqueado, as categorias ficam desativadas.
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
    // Grupos começam expandidos, exceto a lista de arquivos sem número.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

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
        val rowsAlpha = if (state.canOrganize) 1f else LOCKED_ALPHA
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
            state.groups.forEach { group ->
                accountGroup(group, state.checklist, state.canOrganize, rowsAlpha, expanded, onDrop, onCategoryClick)
            }
            state.checklist?.unmatchedFiles?.takeIf { it.isNotEmpty() }?.let { files ->
                unmatchedFiles(files, expanded)
            }
        }
    }
}

private fun LazyListScope.accountGroup(
    group: AccountGroup,
    checklist: MonthChecklist?,
    enabled: Boolean,
    rowsAlpha: Float,
    expanded: SnapshotStateMap<String, Boolean>,
    onDrop: (List<Path>, String) -> Unit,
    onClick: (DocumentCategory) -> Unit,
) {
    val key = group.accountType.name
    val isExpanded = expanded[key] ?: true
    val entries = flatten(group.nodes, depth = 1)
    item(key = "header-$key") {
        GroupHeader(
            title = "📁 ${group.accountType.displayName}",
            summary = checklist?.let { summaryFor(entries, it) },
            isExpanded = isExpanded,
            onToggle = { expanded[key] = !isExpanded },
        )
    }
    if (!isExpanded) return
    items(entries, key = { "$key-${it.node.category.id}" }) { entry ->
        CategoryRow(entry, checklist, enabled, onDrop, onClick, Modifier.alpha(rowsAlpha))
    }
}

private fun LazyListScope.unmatchedFiles(files: List<String>, expanded: SnapshotStateMap<String, Boolean>) {
    val isExpanded = expanded[UNMATCHED_GROUP_KEY] ?: false
    item(key = "header-$UNMATCHED_GROUP_KEY") {
        GroupHeader(
            title = "📂 ${Strings.UNMATCHED_FILES}",
            summary = Strings.sequentialCount(files.size),
            isExpanded = isExpanded,
            onToggle = { expanded[UNMATCHED_GROUP_KEY] = !isExpanded },
        )
    }
    if (!isExpanded) return
    items(files, key = { "$UNMATCHED_GROUP_KEY-$it" }) { name ->
        Text(
            text = "📄 $name",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
        )
    }
}

private data class TreeEntry(val node: CategoryNode, val depth: Int)

private fun flatten(nodes: List<CategoryNode>, depth: Int): List<TreeEntry> =
    nodes.flatMap { node -> listOf(TreeEntry(node, depth)) + flatten(node.children, depth + 1) }

/** Resumo do grupo: quantas categorias obrigatórias já têm arquivo ("Outros" não conta). */
private fun summaryFor(entries: List<TreeEntry>, checklist: MonthChecklist): String {
    val required = entries.map { it.node.category }.filter { it.namingRule == NamingRule.CATEGORY_NAME }
    return Strings.groupSummary(required.count { checklist.isPresent(it.id) }, required.size)
}

@Composable
private fun GroupHeader(title: String, summary: String?, isExpanded: Boolean, onToggle: () -> Unit) {
    val action = if (isExpanded) Strings.COLLAPSE else Strings.EXPAND
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .clickable(onClick = onToggle)
            .semantics { contentDescription = "$action $title" }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (isExpanded) "▾" else "▸", modifier = Modifier.width(18.dp), color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        summary?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    entry: TreeEntry,
    checklist: MonthChecklist?,
    enabled: Boolean,
    onDrop: (List<Path>, String) -> Unit,
    onClick: (DocumentCategory) -> Unit,
    modifier: Modifier = Modifier,
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
    val files = checklist?.filesFor(category.id).orEmpty()

    Column(
        modifier
            .fillMaxWidth()
            .padding(start = (entry.depth * 16).dp, top = 1.dp, bottom = 1.dp)
            .background(highlight, shape)
            .border(1.dp, borderColor, shape)
            // Mesmo bloqueada, a categoria recebe o arquivo para avisar o motivo; o ViewModel recusa.
            .dragAndDropTarget(shouldStartDragAndDrop = DragPayload::canAccept, target = dropTarget)
            .clickable { onClick(category) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            checklist?.let { FileStatus(category, files) }
        }
        files.forEach { name ->
            Text(
                text = "📄 $name",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 1.dp),
            )
        }
        if (showDropHighlight) {
            Text(Strings.DROP_HERE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Selo à direita da categoria: ✓ Já existe (verde) ou ○ Faltando (âmbar). "Outros" mostra só a quantidade. */
@Composable
private fun FileStatus(category: DocumentCategory, files: List<String>) {
    val (text, color, background) = when {
        category.namingRule == NamingRule.SEQUENTIAL ->
            Triple(Strings.sequentialCount(files.size), MaterialTheme.colorScheme.onSurfaceVariant, Color.Transparent)
        files.isNotEmpty() -> Triple("✓ ${Strings.FILE_PRESENT}", StatusColors.Positive, StatusColors.PositiveBackground)
        else -> Triple("○ ${Strings.FILE_MISSING}", StatusColors.Warning, StatusColors.WarningBackground)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.background(background, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
