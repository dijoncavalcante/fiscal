package com.bragadev.fiscal.presentation.components

import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Abre o seletor de pastas nativo. Retorna `null` se o usuário cancelar. */
fun pickFolder(title: String, initial: Path?): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        initial?.toFile()?.takeIf { it.exists() }?.let { currentDirectory = it }
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}

/** Abre o seletor de arquivos nativo com seleção múltipla. Retorna lista vazia se o usuário cancelar. */
fun pickFiles(title: String, initial: Path?, filterDescription: String, extensions: List<String>): List<Path> {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = true
        fileFilter = FileNameExtensionFilter(filterDescription, *extensions.toTypedArray())
        initial?.toFile()?.takeIf { it.exists() }?.let { currentDirectory = it }
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return emptyList()
    return chooser.selectedFiles.map { it.toPath() }
}
