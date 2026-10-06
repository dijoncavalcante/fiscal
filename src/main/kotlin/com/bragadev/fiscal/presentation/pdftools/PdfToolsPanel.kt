package com.bragadev.fiscal.presentation.pdftools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.domain.rules.ImageFileRules
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.DragPayload
import com.bragadev.fiscal.presentation.components.FolderPathField
import com.bragadev.fiscal.presentation.components.Panel
import com.bragadev.fiscal.presentation.components.ReorderableColumn
import com.bragadev.fiscal.presentation.components.pickFiles
import com.bragadev.fiscal.presentation.components.pickFolder
import java.nio.file.Path

private const val MIN_PDFS_TO_MERGE = 2
private val ROW_HEIGHT = 40.dp

/**
 * Painel de "Converter JPEG para PDF" e "Juntar PDFs", no lugar do mês em edição enquanto estiver aberto.
 * A tela principal continua livre: o usuário clica num arquivo da lista para ver no preview e arrasta
 * para cá; a ordem é ajustada arrastando a alça ⠿.
 *
 * @param onPreview mostra o arquivo no preview central.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfToolsPanel(
    dialog: PdfToolDialog,
    isWorking: Boolean,
    viewModel: PdfToolsViewModel,
    onPreview: (Path) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isImages = dialog.tool == PdfTool.IMAGES_TO_PDF
    val currentAdd by rememberUpdatedState(viewModel::onFilesAdded)
    var isDragOver by remember { mutableStateOf(false) }
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { isDragOver = true }
            override fun onExited(event: DragAndDropEvent) { isDragOver = false }
            override fun onEnded(event: DragAndDropEvent) { isDragOver = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragOver = false
                val paths = DragPayload.paths(event)
                if (paths.isEmpty()) return false
                currentAdd(paths)
                return true
            }
        }
    }

    Panel(title = if (isImages) Strings.IMAGES_TO_PDF.uppercase() else Strings.MERGE_PDFS.uppercase(), modifier = modifier) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (isDragOver) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .dragAndDropTarget(shouldStartDragAndDrop = DragPayload::canAccept, target = dropTarget)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(
                if (isImages) Strings.IMAGES_TO_PDF_HINT else Strings.MERGE_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddFilesRow(dialog, isImages, viewModel)
            if (dialog.items.isEmpty()) {
                DropZone(if (isImages) Strings.DROP_IMAGES_HERE else Strings.DROP_PDFS_HERE, isDragOver)
            } else {
                ItemList(dialog, isImages, viewModel, onPreview)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            OutputFields(dialog, viewModel)
            dialog.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        HorizontalDivider()
        Buttons(dialog, isImages, isWorking, viewModel)
    }
}

@Composable
private fun AddFilesRow(dialog: PdfToolDialog, isImages: Boolean, viewModel: PdfToolsViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedButton(onClick = {
            val files = if (isImages) {
                pickFiles(Strings.IMAGES_PICKER_TITLE, viewModel.browseFolder, Strings.IMAGES_FILTER, ImageFileRules.SUPPORTED_EXTENSIONS)
            } else {
                pickFiles(Strings.PDFS_PICKER_TITLE, viewModel.browseFolder, Strings.PDFS_FILTER, listOf("pdf"))
            }
            if (files.isNotEmpty()) viewModel.onFilesAdded(files)
        }) { Text(if (isImages) Strings.ADD_IMAGES else Strings.ADD_PDFS) }
        Text(Strings.pageCountLabel(dialog.items.size), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun DropZone(text: String, isDragOver: Boolean) {
    val color = if (isDragOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.fillMaxWidth().heightIn(min = 140.dp).border(2.dp, color, MaterialTheme.shapes.medium).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ItemList(dialog: PdfToolDialog, isImages: Boolean, viewModel: PdfToolsViewModel, onPreview: (Path) -> Unit) {
    Text(Strings.DRAG_TO_REORDER, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ReorderableColumn(
        items = dialog.items,
        key = { it.path },
        itemHeight = ROW_HEIGHT,
        onMove = viewModel::onMove,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
    ) { index, item, handle, isDragging ->
        ItemRow(dialog, index, item, isImages, isDragging, handle, viewModel, onPreview)
    }
}

@Composable
private fun ItemRow(
    dialog: PdfToolDialog,
    index: Int,
    item: ImagePage,
    isImages: Boolean,
    isDragging: Boolean,
    handle: Modifier,
    viewModel: PdfToolsViewModel,
    onPreview: (Path) -> Unit,
) {
    val selected = index == dialog.selectedIndex
    val background = when {
        isDragging -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(background)
            .clickable { viewModel.onItemSelected(index); onPreview(item.path) }
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "⠿",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = handle.padding(horizontal = 10.dp).semantics { contentDescription = Strings.DRAG_TO_REORDER },
        )
        val rotation = if (isImages && item.rotationDegrees != 0) "  ↻${item.rotationDegrees}°" else ""
        Text(
            "${index + 1}. ${item.path.fileName}$rotation",
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SmallAction("↑", Strings.MOVE_UP, enabled = index > 0) { viewModel.onMoveUp(index) }
        SmallAction("↓", Strings.MOVE_DOWN, enabled = index < dialog.items.lastIndex) { viewModel.onMoveDown(index) }
        if (isImages) SmallAction("↻", Strings.ROTATE) { viewModel.onRotate(index) }
        SmallAction("✕", Strings.REMOVE_ITEM) { viewModel.onRemove(index) }
    }
}

@Composable
private fun SmallAction(symbol: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width = 36.dp, height = 32.dp).semantics { contentDescription = description },
    ) {
        Text(symbol, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OutputFields(dialog: PdfToolDialog, viewModel: PdfToolsViewModel) {
    FolderPathField(
        label = Strings.OUTPUT_FOLDER,
        path = dialog.outputFolder?.toString().orEmpty(),
        emptyText = Strings.SOURCE_FOLDER_EMPTY,
        editDescription = Strings.OUTPUT_FOLDER_EDIT,
        onEdit = { pickFolder(Strings.OUTPUT_FOLDER_PICKER_TITLE, dialog.outputFolder)?.let(viewModel::onOutputFolderSelected) },
    )
    OutlinedTextField(
        value = dialog.outputName,
        onValueChange = viewModel::onOutputNameChanged,
        label = { Text(Strings.OUTPUT_NAME) },
        suffix = { Text(".pdf") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

@Composable
private fun Buttons(dialog: PdfToolDialog, isImages: Boolean, isWorking: Boolean, viewModel: PdfToolsViewModel) {
    val enoughFiles = if (isImages) dialog.items.isNotEmpty() else dialog.items.size >= MIN_PDFS_TO_MERGE
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = viewModel::dismissDialog) { Text(Strings.CLOSE_TOOL) }
        Button(onClick = viewModel::onConfirm, enabled = enoughFiles && dialog.outputName.isNotBlank() && !isWorking) {
            Text(Strings.CREATE_PDF)
        }
    }
}
