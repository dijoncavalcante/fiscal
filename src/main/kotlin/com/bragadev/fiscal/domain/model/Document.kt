package com.bragadev.fiscal.domain.model

import com.bragadev.fiscal.domain.rules.ImageFileRules
import java.nio.file.Path
import java.time.Instant

data class Document(
    val path: Path,
    val sizeBytes: Long,
    val lastModified: Instant,
) {
    val name: String get() = path.fileName.toString()

    /** Imagem (JPEG/PNG) que pode ser convertida em PDF. */
    val isImage: Boolean get() = ImageFileRules.isSupported(name)
}
