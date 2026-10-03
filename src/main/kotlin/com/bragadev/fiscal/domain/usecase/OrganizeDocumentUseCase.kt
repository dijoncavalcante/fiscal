package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.DuplicateResolution
import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.OrganizationPlan
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.BackupStorage
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.SettingsRepository
import com.bragadev.fiscal.domain.rules.ConflictDecision
import com.bragadev.fiscal.domain.rules.ConflictPolicy
import com.bragadev.fiscal.domain.rules.DuplicateNameResolver
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import java.nio.file.Path

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
    private val mover: RecordedFileMover,
    private val backupStorage: BackupStorage,
    private val settingsRepository: SettingsRepository,
    private val periodPolicy: EditablePeriodPolicy,
) {
    suspend operator fun invoke(plan: OrganizationPlan, userChoice: DuplicateResolution? = null): Outcome<OrganizeResult> {
        if (!fileRepository.exists(plan.source)) return Outcome.Failure(FileOperationError.FileNotFound)
        periodPolicy.checkSource(plan.source.parent)?.let { return Outcome.Failure(it) }
        periodPolicy.checkSource(plan.targetDirectory)?.let { return Outcome.Failure(it) }
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

    private suspend fun moveAndRecord(plan: OrganizationPlan, target: Path, backupPath: Path?): Outcome<OrganizeResult> =
        when (val moved = mover.move(plan.source, target, plan.category.id, backupPath)) {
            is Outcome.Success -> Outcome.Success(OrganizeResult.Done(moved.value))
            is Outcome.Failure -> moved
        }
}
