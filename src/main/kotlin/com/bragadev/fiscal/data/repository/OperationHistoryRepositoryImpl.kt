package com.bragadev.fiscal.data.repository

import com.bragadev.fiscal.data.database.FileOperationDao
import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.repository.OperationHistoryRepository
import java.util.UUID

class OperationHistoryRepositoryImpl(private val fileOperationDao: FileOperationDao) : OperationHistoryRepository {
    override suspend fun save(operation: FileOperation) = fileOperationDao.insert(operation)

    override suspend fun find(id: UUID): FileOperation? = fileOperationDao.findById(id)

    override suspend fun markUndone(id: UUID) = fileOperationDao.markUndone(id)

    override suspend fun all(): List<FileOperation> = fileOperationDao.all()

    override suspend fun lastUndoable(): FileOperation? = fileOperationDao.lastNotUndone()
}
