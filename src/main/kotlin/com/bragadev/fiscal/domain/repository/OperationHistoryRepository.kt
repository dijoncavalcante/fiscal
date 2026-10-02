package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.FileOperation
import java.util.UUID

interface OperationHistoryRepository {
    suspend fun save(operation: FileOperation)

    suspend fun find(id: UUID): FileOperation?

    suspend fun markUndone(id: UUID)

    /** Última operação ainda não desfeita, se houver. */
    suspend fun lastUndoable(): FileOperation?
}
