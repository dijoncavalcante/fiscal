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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.AccountGroup
import com.bragadev.fiscal.domain.model.CategoryNode
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.MonthChecklist
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.DragPayload
import com.bragadev.fiscal.presentation.components.ExpandIcon
import com.bragadev.fiscal.presentation.components.IconText
import com.bragadev.fiscal.presentation.components.Panel
import com.bragadev.fiscal.presentation.components.StatusColors
import java.nio.file.Path

private const val LOCKED_ALPHA = 0.45f
private const val UNMATCHED_GROUP_KEY = "unmatched"

/** Tudo o que as linhas da lista precisam saber sobre a pasta do mês. */
private class MonthContext(
    val checklist: MonthChecklist?,
    val monthFolder: Path?,
    val editable: Boolean,
    val fileActions: MonthFileActions,
    val onDrop: (List<Path>, String) -> Unit,
    val onCategoryClick: (DocumentCategory) -> Unit,
    /** Arquivo aberto no preview, destacado na lista como no lado esquerdo. */
    val selectedFile: Path?,
) {
    fun fileIn(name: String): Path? = monthFolder?.resolve(name)

    fun isSelected(file: Path): Boolean = selectedFile?.normalize() == file.normalize()
}

/**
 * Lado direito: mês em edição e categorias agrupadas por conta (cada grupo pode ser recolhido).
 * Cada categoria mostra se já existe arquivo na pasta do mês, se está faltando ou com pendência,
 * e aceita PDFs arrastados do Windows Explorer ou da lista. Os arquivos listados podem ser vistos,
 * renomeados, retirados do mês ou marcados com pendência.
 */
@Composable
fun OrganizerScreen(
    state: OrganizerUiState,
    selectedDocument: Path?,
    onDrop: (paths: List<Path>, categoryId: String) -> Unit,
    onCategoryClickedWithoutDocument: () -> Unit,
    fileActions: MonthFileActions,
    modifier: Modifier = Modifier,
    /** Seletor de mês, mostrado no topo da lista (rola junto com as categorias). */
    navigator: @Composable () -> Unit = {},
) {
    // Grupos começam expandidos, exceto a lista de arquivos sem número.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val context = MonthContext(
        checklist = state.checklist,
        monthFolder = state.monthFolder?.path,
        editable = state.canOrganize,
        fileActions = fileActions,
        onDrop = onDrop,
        onCategoryClick = { category ->
            if (selectedDocument != null) onDrop(listOf(selectedDocument), category.id) else onCategoryClickedWithoutDocument()
        },
        selectedFile = selectedDocument,
    )

    Panel(title = Strings.MONTH_PANEL, modifier = modifier) {
        MonthHeader(state.monthFolder, state.firstEditableMonth)
        HorizontalDivider()
        Text(
            text = Strings.DROP_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
            item(key = "navigator") { navigator() }
            state.groups.forEach { group -> accountGroup(group, context, expanded) }
            state.checklist?.unmatchedFiles?.takeIf { it.isNotEmpty() }?.let { files -> unmatchedFiles(files, context, expanded) }
        }
    }
}

private fun LazyListScope.accountGroup(group: AccountGroup, context: MonthContext, expanded: SnapshotStateMap<String, Boolean>) {
    val key = group.accountType.name
    val isExpanded = expanded[key] ?: true
    val entries = flatten(group.nodes, depth = 1)
    item(key = "header-$key") {
        GroupHeader(
            icon = AppIcons.Folder,
            title = group.accountType.displayName,
            summary = context.checklist?.let { summaryFor(entries, it) },
            isExpanded = isExpanded,
            onToggle = { expanded[key] = !isExpanded },
        )
    }
    if (!isExpanded) return
    items(entries, key = { "$key-${it.node.category.id}" }) { entry -> CategoryRow(entry, context) }
}

private fun LazyListScope.unmatchedFiles(files: List<String>, context: MonthContext, expanded: SnapshotStateMap<String, Boolean>) {
    val isExpanded = expanded[UNMATCHED_GROUP_KEY] ?: false
    item(key = "header-$UNMATCHED_GROUP_KEY") {
        GroupHeader(
            icon = AppIcons.FolderOpen,
            title = Strings.UNMATCHED_FILES,
            summary = Strings.sequentialCount(files.size),
            isExpanded = isExpanded,
            onToggle = { expanded[UNMATCHED_GROUP_KEY] = !isExpanded },
        )
    }
    if (!isExpanded) return
    items(files, key = { "$UNMATCHED_GROUP_KEY-$it" }) { name ->
        val file = context.fileIn(name) ?: return@items
        MonthFileRow(
            file = file,
            category = null,
            issue = context.checklist?.flagFor(name),
            editable = context.editable,
            isSelected = context.isSelected(file),
            actions = context.fileActions,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
}

private data class TreeEntry(val node: CategoryNode, val depth: Int)

private fun flatten(nodes: List<CategoryNode>, depth: Int): List<TreeEntry> =
    nodes.flatMap { node -> listOf(TreeEntry(node, depth)) + flatten(node.children, depth + 1) }

/**
 * Resumo do grupo: quantas categorias obrigatórias estão resolvidas — com arquivo e sem pendência.
 * Categorias opcionais ("Outros") não contam.
 */
private fun summaryFor(entries: List<TreeEntry>, checklist: MonthChecklist): String {
    val required = entries.map { it.node.category }.filterNot { it.optional }
    val done = required.count { checklist.isPresent(it.id) && !checklist.hasPendingIssue(it.id) }
    return Strings.groupSummary(done, required.size)
}

@Composable
private fun GroupHeader(icon: ImageVector, title: String, summary: String?, isExpanded: Boolean, onToggle: () -> Unit) {
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
        ExpandIcon(isExpanded, Modifier.padding(end = 4.dp))
        IconText(
            icon = icon,
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        summary?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(entry: TreeEntry, context: MonthContext) {
    val category = entry.node.category
    val currentOnDrop by rememberUpdatedState(context.onDrop)
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

    val checklist = context.checklist
    val showDropHighlight = isDragOver && context.editable
    val shape = RoundedCornerShape(6.dp)
    val highlight = if (showDropHighlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val borderColor = if (showDropHighlight) MaterialTheme.colorScheme.primary else Color.Transparent
    val files = checklist?.filesFor(category.id).orEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = (entry.depth * 16).dp, top = 1.dp, bottom = 1.dp)
            .background(highlight, shape)
            .border(1.dp, borderColor, shape)
            // Mesmo bloqueada, a categoria recebe o arquivo para avisar o motivo; o ViewModel recusa.
            .dragAndDropTarget(shouldStartDragAndDrop = DragPayload::canAccept, target = dropTarget)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { context.onCategoryClick(category) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val labelAlpha = if (context.editable) 1f else LOCKED_ALPHA
            Text(
                category.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = labelAlpha),
                modifier = Modifier.weight(1f),
            )
            checklist?.let { FileStatus(category, files, it) }
        }
        files.forEach { name ->
            val file = context.fileIn(name) ?: return@forEach
            MonthFileRow(
                file = file,
                category = category,
                issue = checklist?.flagFor(name),
                editable = context.editable,
                isSelected = context.isSelected(file),
                actions = context.fileActions,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (showDropHighlight) {
            Text(Strings.DROP_HERE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Selo à direita da categoria: Já existe (verde), Com pendência (vermelho claro) ou Faltando (âmbar).
 * Categorias opcionais ("Outros") mostram só a quantidade de arquivos.
 */
@Composable
private fun FileStatus(category: DocumentCategory, files: List<String>, checklist: MonthChecklist) {
    if (category.optional) {
        Text(
            text = Strings.sequentialCount(files.size),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        return
    }
    val status = when {
        checklist.hasPendingIssue(category.id) ->
            StatusBadge(AppIcons.Warning, Strings.STATUS_ISSUE, MaterialTheme.colorScheme.error, StatusColors.LockedBackground)
        files.isNotEmpty() -> StatusBadge(AppIcons.CheckCircle, Strings.FILE_PRESENT, StatusColors.Positive, StatusColors.PositiveBackground)
        else -> StatusBadge(AppIcons.CircleOutline, Strings.FILE_MISSING, StatusColors.Warning, StatusColors.WarningBackground)
    }
    IconText(
        icon = status.icon,
        text = status.text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = status.color,
        iconSize = 13.dp,
        modifier = Modifier.background(status.background, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private data class StatusBadge(val icon: ImageVector, val text: String, val color: Color, val background: Color)

