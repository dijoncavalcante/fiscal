package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.OperationType
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.DocumentRepository
import com.bragadev.fiscal.domain.repository.FileFlagRepository
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.OperationHistoryRepository
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID

/**
 * Move ou renomeia um arquivo e registra a operação no histórico, para que possa ser desfeita.
 * Usado por organizar, renomear e retirar do mês. A pendência do arquivo acompanha o novo caminho.
 */
class RecordedFileMover(
    private val fileRepository: FileRepository,
    private val historyRepository: OperationHistoryRepository,
    private val documentRepository: DocumentRepository,
    private val flagRepository: FileFlagRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun move(source: Path, target: Path, categoryId: String?, backupPath: Path? = null): Outcome<FileOperation> {
        val moved = fileRepository.move(source, target)
        if (moved is Outcome.Failure) return moved

        val operation = FileOperation(
            id = newId(),
            type = if (source.parent.normalize() == target.parent.normalize()) OperationType.RENAME else OperationType.MOVE,
            originalPath = source.toString(),
            originalName = source.fileName.toString(),
            newPath = target.toString(),
            newName = target.fileName.toString(),
            timestamp = now(),
            backupPath = backupPath?.toString(),
        )
        historyRepository.save(operation)
        documentRepository.recordOrganized(source, target, categoryId)
        flagRepository.move(source, target)
        return Outcome.Success(operation)
    }
}
