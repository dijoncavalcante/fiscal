package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.DuplicateResolution
import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.OperationType
import com.bragadev.fiscal.domain.model.OrganizationPlan
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.BackupStorage
import com.bragadev.fiscal.domain.repository.DocumentRepository
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.OperationHistoryRepository
import com.bragadev.fiscal.domain.repository.SettingsRepository
import com.bragadev.fiscal.domain.rules.ConflictDecision
import com.bragadev.fiscal.domain.rules.ConflictPolicy
import com.bragadev.fiscal.domain.rules.DuplicateNameResolver
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID

sealed interface OrganizeResult {
    data class Done(val operation: FileOperation) : OrganizeResult

    /** O destino já existe e a configuração exige que o usuário escolha o que fazer. */
    data object NeedsConflictChoice : OrganizeResult

    data object Cancelled : OrganizeResult
}

/**
 * Executa uma [OrganizationPlan] já confirmada pelo usuário.
 *
 * O conflito é verificado novamente no momento da execução, pois o disco pode ter mudado
 * desde que a proposta foi calculada. Nenhum arquivo é sobrescrito: ao substituir,
 * o arquivo existente é guardado no [BackupStorage] para permitir desfazer.
 */
class OrganizeDocumentUseCase(
    private val fileRepository: FileRepository,
    private val historyRepository: OperationHistoryRepository,
    private val documentRepository: DocumentRepository,
    private val backupStorage: BackupStorage,
    private val settingsRepository: SettingsRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend operator fun invoke(plan: OrganizationPlan, userChoice: DuplicateResolution? = null): Outcome<OrganizeResult> {
        if (!fileRepository.exists(plan.source)) return Outcome.Failure(FileOperationError.FileNotFound)
        if (!fileRepository.exists(plan.targetPath)) return moveAndRecord(plan, plan.targetPath, backupPath = null)

        val policy = settingsRepository.settings.value.duplicatePolicy
        return when (ConflictPolicy.decide(policy, userChoice)) {
            ConflictDecision.Forbidden -> Outcome.Failure(FileOperationError.DuplicatesNotAllowed)
            ConflictDecision.NeedsUserChoice -> Outcome.Success(OrganizeResult.NeedsConflictChoice)
            ConflictDecision.Cancel -> Outcome.Success(OrganizeResult.Cancelled)
            ConflictDecision.NumberedCopy -> moveAndRecord(plan, freshNumberedCopyPath(plan), backupPath = null)
            ConflictDecision.Replace -> replace(plan)
        }
    }

    private suspend fun freshNumberedCopyPath(plan: OrganizationPlan): Path {
        val existing = fileRepository.listFileNames(plan.targetDirectory)
        return plan.targetDirectory.resolve(DuplicateNameResolver.nextNumberedCopy(plan.suggestedName, existing))
    }

    private suspend fun replace(plan: OrganizationPlan): Outcome<OrganizeResult> {
        val backupPath = backupStorage.newBackupPath(plan.suggestedName)
        val backup = fileRepository.move(plan.targetPath, backupPath)
        if (backup is Outcome.Failure) return backup

        val result = moveAndRecord(plan, plan.targetPath, backupPath)
        if (result is Outcome.Failure) fileRepository.move(backupPath, plan.targetPath)
        return result
    }

    private suspend fun moveAndRecord(plan: OrganizationPlan, target: Path, backupPath: Path?): Outcome<OrganizeResult> {
        val moved = fileRepository.move(plan.source, target)
        if (moved is Outcome.Failure) return moved

        val operation = FileOperation(
            id = newId(),
            type = if (plan.source.parent.normalize() == target.parent.normalize()) OperationType.RENAME else OperationType.MOVE,
            originalPath = plan.source.toString(),
            originalName = plan.source.fileName.toString(),
            newPath = target.toString(),
            newName = target.fileName.toString(),
            timestamp = now(),
            backupPath = backupPath?.toString(),
        )
        historyRepository.save(operation)
        documentRepository.recordOrganized(plan.source, target, plan.category.id)
        return Outcome.Success(OrganizeResult.Done(operation))
    }
}
