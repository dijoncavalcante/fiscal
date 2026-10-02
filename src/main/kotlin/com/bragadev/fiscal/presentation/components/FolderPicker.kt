package com.bragadev.fiscal.presentation.components

import com.bragadev.fiscal.presentation.common.Strings
import java.nio.file.Path
import javax.swing.JFileChooser

/** Abre o seletor de pastas nativo. Retorna `null` se o usuário cancelar. */
fun pickFolder(initial: Path?): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = Strings.ROOT_PICKER_TITLE
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        initial?.toFile()?.takeIf { it.exists() }?.let { currentDirectory = it }
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}
