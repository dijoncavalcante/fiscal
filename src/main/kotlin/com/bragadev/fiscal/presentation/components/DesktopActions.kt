package com.bragadev.fiscal.presentation.components

import com.bragadev.fiscal.data.logging.AppLog
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.nio.file.Path

/** Ações do Windows usadas pela interface (sem rede): copiar texto ou arquivos e abrir um arquivo ou uma pasta. */
object DesktopActions {
    fun copyToClipboard(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
            .onFailure { AppLog.warn("Não foi possível copiar para a área de transferência", it) }
    }

    /**
     * Copia os arquivos como o Ctrl+C do Explorer: depois é só colar (Ctrl+V) numa pasta, no WhatsApp ou num e-mail.
     * Nada é gravado agora; quem cola é que cria a cópia. Retorna `false` se a área de transferência estiver ocupada.
     */
    fun copyFilesToClipboard(files: List<Path>): Boolean =
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(FileSelection(files.map(Path::toFile)), null) }
            .onFailure { AppLog.warn("Não foi possível copiar os arquivos para a área de transferência", it) }
            .isSuccess

    fun openFile(file: Path) {
        runCatching { Desktop.getDesktop().open(file.toFile()) }
            .onFailure { AppLog.warn("Não foi possível abrir o arquivo $file", it) }
    }

    fun openFolder(folder: Path) {
        runCatching { Desktop.getDesktop().open(folder.toFile()) }
            .onFailure { AppLog.warn("Não foi possível abrir a pasta $folder", it) }
    }

    /** Lista de arquivos na área de transferência (no Windows vira o mesmo formato do Explorer). */
    private class FileSelection(private val files: List<File>) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return files
        }
    }
}
