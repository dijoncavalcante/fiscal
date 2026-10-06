package com.bragadev.fiscal.presentation.pdftools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.domain.rules.ImageFileRules
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.FolderPathField
import com.bragadev.fiscal.presentation.components.ImageThumbnail
import com.bragadev.fiscal.presentation.components.PdfThumbnail
import com.bragadev.fiscal.presentation.components.pickFiles
import com.bragadev.fiscal.presentation.components.pickFolder

private const val MIN_PDFS_TO_MERGE = 2

/** Janela de "Converter JPEG para PDF" e "Juntar PDFs": visualização à esquerda, lista e opções à direita. */
@Composable
fun PdfToolsDialogHost(state: PdfToolsUiState, viewModel: PdfToolsViewModel) {
    val dialog = state.dialog ?: return
    val isImages = dialog.tool == PdfTool.IMAGES_TO_PDF
    Dialog(onDismissRequest = viewModel::dismissDialog, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, modifier = Modifier.width(1000.dp).height(660.dp)) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Preview(dialog.selected, isImages, Modifier.width(400.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(if (isImages) Strings.IMAGES_TO_PDF else Strings.MERGE_PDFS, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (isImages) Strings.IMAGES_TO_PDF_HINT else Strings.MERGE_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    AddFilesRow(dialog, isImages, viewModel)
                    ItemList(dialog, isImages, viewModel, Modifier.weight(1f).fillMaxWidth())
                    OutputFields(dialog, viewModel)
                    dialog.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Buttons(dialog, isImages, state.isWorking, viewModel)
                }
            }
        }
    }
}

@Composable
private fun Preview(item: ImagePage?, isImages: Boolean, modifier: Modifier) {
    when {
        item == null -> Box(modifier.background(Color(0xFFE9ECEF)), contentAlignment = Alignment.Center) {
            Text(Strings.EMPTY_TOOL_LIST, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        isImages -> ImageThumbnail(item, modifier)
        else -> PdfThumbnail(item.path, modifier)
    }
}

@Composable
private fun AddFilesRow(dialog: PdfToolDialog, isImages: Boolean, viewModel: PdfToolsViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            val files = if (isImages) {
                pickFiles(Strings.IMAGES_PICKER_TITLE, viewModel.browseFolder, Strings.IMAGES_FILTER, ImageFileRules.SUPPORTED_EXTENSIONS)
            } else {
                pickFiles(Strings.PDFS_PICKER_TITLE, viewModel.browseFolder, Strings.PDFS_FILTER, listOf("pdf"))
            }
            viewModel.onFilesAdded(files)
        }) { Text(if (isImages) Strings.ADD_IMAGES else Strings.ADD_PDFS) }
        Text(
            Strings.pageCountLabel(dialog.items.size),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ItemList(dialog: PdfToolDialog, isImages: Boolean, viewModel: PdfToolsViewModel, modifier: Modifier) {
    LazyColumn(
        modifier.padding(vertical = 8.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
    ) {
        itemsIndexed(dialog.items, key = { index, item -> "$index-${item.path}" }) { index, item ->
            val selected = index == dialog.selectedIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { viewModel.onItemSelected(index) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}. ${item.path.fileName}",
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
    }
}

@Composable
private fun SmallAction(symbol: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width = 40.dp, height = 32.dp).semantics { contentDescription = description },
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
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = viewModel::dismissDialog) { Text(Strings.CANCEL) }
        Button(
            onClick = viewModel::onConfirm,
            enabled = enoughFiles && dialog.outputName.isNotBlank() && !isWorking,
        ) { Text(Strings.CREATE_PDF) }
    }
}
