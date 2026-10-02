package com.bragadev.fiscal.domain.repository

import java.nio.file.Path

/** Registro local (SQLite) dos documentos organizados pela aplicação. */
interface DocumentRepository {
    suspend fun recordOrganized(originalPath: Path, currentPath: Path, categoryId: String?)

    suspend fun recordRestored(currentPath: Path, restoredPath: Path)
}
