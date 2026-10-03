package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.UndoBlockReason
import com.bragadev.fiscal.domain.repository.DocumentRepository
import com.bragadev.fiscal.domain.repository.FileFlagRepository
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.OperationHistoryRepository
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import java.nio.file.Path
import java.util.UUID

/**
 * Desfaz uma operação: devolve o arquivo ao caminho original e, se a operação
 * substituiu outro arquivo, restaura o arquivo substituído.
 *
 * Antes de mexer em disco, valida que o desfazer é seguro; caso contrário nada é alterado.
 */
class UndoOperationUseCase(
    private val fileRepository: FileRepository,
    private val historyRepository: OperationHistoryRepository,
    private val documentRepository: DocumentRepository,
    private val flagRepository: FileFlagRepository,
    private val periodPolicy: EditablePeriodPolicy,
) {
    suspend operator fun invoke(operationId: UUID): Outcome<FileOperation> {
        val operation = historyRepository.find(operationId) ?: return blocked(UndoBlockReason.OPERATION_NOT_FOUND)
        validate(operation)?.let { return blocked(it) }

        val current = Path.of(operation.newPath)
        val original = Path.of(operation.originalPath)
        listOf(current.parent, original.parent).forEach { folder ->
            periodPolicy.checkSource(folder)?.let { return Outcome.Failure(it) }
        }
        val restored = fileRepository.move(current, original)
        if (restored is Outcome.Failure) return restored

        operation.backupPath?.let { fileRepository.move(Path.of(it), current) }
        historyRepository.markUndone(operation.id)
        documentRepository.recordRestored(current, original)
        flagRepository.move(current, original)
        return Outcome.Success(operation.copy(undone = true))
    }

    suspend fun lastUndoable(): FileOperation? = historyRepository.lastUndoable()

    private suspend fun validate(operation: FileOperation): UndoBlockReason? = when {
        operation.undone -> UndoBlockReason.ALREADY_UNDONE
        !fileRepository.exists(Path.of(operation.newPath)) -> UndoBlockReason.CURRENT_FILE_MISSING
        fileRepository.exists(Path.of(operation.originalPath)) -> UndoBlockReason.ORIGINAL_LOCATION_OCCUPIED
        operation.backupPath != null && !fileRepository.exists(Path.of(operation.backupPath)) -> UndoBlockReason.BACKUP_MISSING
        else -> null
    }

    private fun blocked(reason: UndoBlockReason) = Outcome.Failure(FileOperationError.UndoNotPossible(reason))
}
