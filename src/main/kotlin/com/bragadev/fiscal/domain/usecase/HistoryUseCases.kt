package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.repository.OperationHistoryRepository

/** Uma linha do histórico: a operação e, se não puder ser desfeita agora, o motivo. */
data class HistoryEntry(val operation: FileOperation, val undoBlock: FileOperationError?) {
    val canUndo: Boolean get() = undoBlock == null
}

/** Todas as operações, da mais recente para a mais antiga, já dizendo quais podem ser desfeitas. */
class ListHistoryUseCase(
    private val historyRepository: OperationHistoryRepository,
    private val undoOperation: UndoOperationUseCase,
) {
    suspend operator fun invoke(): List<HistoryEntry> =
        historyRepository.all().map { operation -> HistoryEntry(operation, undoOperation.check(operation)) }
}
