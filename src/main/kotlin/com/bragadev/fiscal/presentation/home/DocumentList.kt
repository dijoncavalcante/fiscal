package com.bragadev.fiscal.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.DocumentSort
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.DragPayload
import com.bragadev.fiscal.presentation.components.FolderPathField
import com.bragadev.fiscal.presentation.components.Panel
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val modifiedFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

/** Marca da lista para os testes de teclado. */
internal const val DOCUMENT_LIST_TAG = "lista-documentos"

/** O que o menu do botão direito (e o ⋮) faz com um documento da lista. */
class DocumentActions(
    val onRename: (Path) -> Unit,
    val onCopy: (Path) -> Unit,
    val onDelete: (Path) -> Unit,
)

/**
 * Lado esquerdo: pasta do computador escolhida pelo usuário e os PDFs dela.
 * Botão direito (ou ⋮) num documento: renomear, copiar e excluir; com a lista focada, F2, Ctrl+C e Delete.
 */
@Composable
fun DocumentList(
    state: HomeUiState,
    onSelect: (Document) -> Unit,
    actions: DocumentActions,
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
            else -> {
                val listFocus = remember { FocusRequester() }
                // Só vale para um documento desta lista (o preview pode estar mostrando um arquivo do mês).
                val selected = state.selectedDocument?.path?.takeIf { path -> state.documents.any { it.path == path } }
                val select = { document: Document ->
                    onSelect(document)
                    runCatching { listFocus.requestFocus() }
                    Unit
                }
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .testTag(DOCUMENT_LIST_TAG)
                        .focusRequester(listFocus)
                        .onKeyEvent { event -> handleDocumentKey(event, selected, actions) }
                        .focusable(),
                ) {
                    items(state.visibleDocuments, key = { it.path.toString() }) { document ->
                        DocumentRow(document, isSelected = document.path == state.selectedDocument?.path, onSelect = select, actions = actions)
                    }
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

/** F2 renomeia, Ctrl+C copia e Delete exclui o documento selecionado (só com a lista focada). */
internal fun handleDocumentKey(event: KeyEvent, selected: Path?, actions: DocumentActions): Boolean {
    if (event.type != KeyEventType.KeyDown || selected == null || event.isAltPressed || event.isShiftPressed) return false
    val action = when {
        event.key == Key.F2 && !event.isCtrlPressed -> actions.onRename
        event.key == Key.Delete && !event.isCtrlPressed -> actions.onDelete
        event.key == Key.C && event.isCtrlPressed -> actions.onCopy
        else -> null
    } ?: return false
    action(selected)
    return true
}

/**
 * Um documento: clique seleciona (e mostra no preview), arrastar leva para uma categoria. O botão direito
 * abre o menu onde o mouse está; o ⋮ (visível com o mouse em cima ou no selecionado) abre o mesmo menu.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DocumentRow(document: Document, isSelected: Boolean, onSelect: (Document) -> Unit, actions: DocumentActions) {
    val background = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    // Onde o botão direito foi clicado (o menu abre ali); null = menu fechado.
    var pointerMenuAt by remember { mutableStateOf<Offset?>(null) }
    var buttonMenuOpen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(background)
            .hoverable(hover)
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    onSelect(document)
                    pointerMenuAt = event.changes.first().position
                }
            }
            .dragAndDropSource { _ -> DragPayload.internalDocument(document.path) }
            .clickable { onSelect(document) },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
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
            // Espaço sempre reservado: o ⋮ aparecer não empurra o nome.
            Box(Modifier.size(28.dp)) {
                if (hovered || isSelected || buttonMenuOpen) {
                    IconButton(onClick = { onSelect(document); buttonMenuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(AppIcons.MoreVertical, Strings.FILE_ACTIONS, Modifier.size(16.dp))
                    }
                }
                DocumentMenu(buttonMenuOpen, document.path, actions, onDismiss = { buttonMenuOpen = false })
            }
        }
        pointerMenuAt?.let { at ->
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
                DocumentMenu(expanded = true, document.path, actions, onDismiss = { pointerMenuAt = null })
            }
        }
    }
}

@Composable
private fun DocumentMenu(expanded: Boolean, file: Path, actions: DocumentActions, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem(AppIcons.Pencil, Strings.RENAME, Strings.SHORTCUT_RENAME) { onDismiss(); actions.onRename(file) }
        MenuItem(AppIcons.Copy, Strings.COPY_FILE, Strings.SHORTCUT_COPY) { onDismiss(); actions.onCopy(file) }
        HorizontalDivider()
        MenuItem(AppIcons.Delete, Strings.DELETE_FILE, Strings.SHORTCUT_DELETE, color = MaterialTheme.colorScheme.error) {
            onDismiss()
            actions.onDelete(file)
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    text: String,
    shortcut: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            Text(shortcut, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        onClick = onClick,
    )
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
