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

/** Abre a janela "Salvar como" nativa. Retorna `null` se o usuário cancelar. Nunca sobrescreve: arquivos existentes são recusados por quem salva. */
fun pickSaveFile(title: String, initialFolder: Path?, suggestedName: String, filterDescription: String, extension: String): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileFilter = FileNameExtensionFilter(filterDescription, extension)
        initialFolder?.toFile()?.takeIf { it.exists() }?.let { currentDirectory = it }
        selectedFile = java.io.File(currentDirectory, suggestedName)
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val chosen = chooser.selectedFile.toPath()
    return if (chosen.fileName.toString().endsWith(".$extension", ignoreCase = true)) chosen else chosen.resolveSibling("${chosen.fileName}.$extension")
}
