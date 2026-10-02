package com.bragadev.fiscal.data.repository

import com.bragadev.fiscal.data.database.DocumentDao
import com.bragadev.fiscal.domain.repository.DocumentRepository
import java.nio.file.Path

class DocumentRepositoryImpl(private val documentDao: DocumentDao) : DocumentRepository {
    override suspend fun recordOrganized(originalPath: Path, currentPath: Path, categoryId: String?) =
        documentDao.upsertMove(originalPath.toString(), currentPath.toString(), currentPath.fileName.toString(), categoryId)

    override suspend fun recordRestored(currentPath: Path, restoredPath: Path) =
        documentDao.upsertMove(currentPath.toString(), restoredPath.toString(), restoredPath.fileName.toString(), null)
}
