package com.bragadev.fiscal.domain.model

import java.nio.file.Path
import java.time.Instant

data class Document(
    val path: Path,
    val sizeBytes: Long,
    val lastModified: Instant,
) {
    val name: String get() = path.fileName.toString()
}
