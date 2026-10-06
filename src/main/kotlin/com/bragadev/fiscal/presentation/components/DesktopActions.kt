package com.bragadev.fiscal.presentation.components

import com.bragadev.fiscal.data.logging.AppLog
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Path

/** Ações do Windows usadas pela interface (sem rede): copiar texto e abrir um arquivo ou uma pasta. */
object DesktopActions {
    fun copyToClipboard(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
            .onFailure { AppLog.warn("Não foi possível copiar para a área de transferência", it) }
    }

    fun openFile(file: Path) {
        runCatching { Desktop.getDesktop().open(file.toFile()) }
            .onFailure { AppLog.warn("Não foi possível abrir o arquivo $file", it) }
    }

    fun openFolder(folder: Path) {
        runCatching { Desktop.getDesktop().open(folder.toFile()) }
            .onFailure { AppLog.warn("Não foi possível abrir a pasta $folder", it) }
    }
}
