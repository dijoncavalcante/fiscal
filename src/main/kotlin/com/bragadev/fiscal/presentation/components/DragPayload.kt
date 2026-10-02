package com.bragadev.fiscal.presentation.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Path

/**
 * Conversão entre o arrastar e soltar e caminhos de arquivo.
 *
 * Arquivos vindos do Windows Explorer chegam como lista de arquivos.
 * Documentos arrastados de dentro do app chegam como texto com um prefixo próprio,
 * para que nunca sejam soltos por engano em outro programa como arquivo.
 */
@OptIn(ExperimentalComposeUiApi::class)
object DragPayload {
    private const val INTERNAL_PREFIX = "fiscal-document:"

    fun internalDocument(path: Path): DragAndDropTransferData = DragAndDropTransferData(
        transferable = DragAndDropTransferable(StringSelection(INTERNAL_PREFIX + path.toString())),
        supportedActions = listOf(DragAndDropTransferAction.Move),
    )

    fun canAccept(event: DragAndDropEvent): Boolean {
        val transferable = event.awtTransferable
        return transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
            transferable.isDataFlavorSupported(DataFlavor.stringFlavor)
    }

    fun paths(event: DragAndDropEvent): List<Path> = runCatching {
        val transferable = event.awtTransferable
        when {
            transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ->
                (transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>)
                    .filterIsInstance<File>()
                    .map(File::toPath)

            transferable.isDataFlavorSupported(DataFlavor.stringFlavor) ->
                (transferable.getTransferData(DataFlavor.stringFlavor) as String)
                    .takeIf { it.startsWith(INTERNAL_PREFIX) }
                    ?.let { listOf(Path.of(it.removePrefix(INTERNAL_PREFIX))) }
                    .orEmpty()

            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}
