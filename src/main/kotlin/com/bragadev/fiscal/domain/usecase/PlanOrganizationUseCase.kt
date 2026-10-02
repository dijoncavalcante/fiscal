package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.NamingRule
import com.bragadev.fiscal.domain.model.OrganizationPlan
import com.bragadev.fiscal.domain.model.OrganizeMode
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.CategoryRepository
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.SettingsRepository
import com.bragadev.fiscal.domain.rules.CategoryHierarchy
import com.bragadev.fiscal.domain.rules.CategoryNaming
import com.bragadev.fiscal.domain.rules.DescribedSequenceNaming
import com.bragadev.fiscal.domain.rules.DuplicateNameResolver
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.rules.FileNameRules
import java.nio.file.Path

/**
 * Calcula destino, novo nome e conflitos de uma organização, sem alterar nada em disco.
 *
 * - "Renomear e Mover" leva o arquivo para a pasta do mês em edição.
 * - "Renomear" mantém o arquivo na pasta onde ele está.
 * Em ambos os casos, nenhuma pasta de mês bloqueado pode ser alterada.
 */
class PlanOrganizationUseCase(
    private val categoryRepository: CategoryRepository,
    private val fileRepository: FileRepository,
    private val settingsRepository: SettingsRepository,
    private val periodPolicy: EditablePeriodPolicy,
) {
    /**
     * @param description descrição informada pelo usuário; obrigatória para categorias como Despesas.
     */
    suspend operator fun invoke(
        source: Path,
        categoryId: String,
        mode: OrganizeMode,
        description: String? = null,
    ): Outcome<OrganizationPlan> {
        if (!fileRepository.exists(source)) return Outcome.Failure(FileOperationError.FileNotFound)
        if (!fileRepository.isPdf(source)) return Outcome.Failure(FileOperationError.InvalidPdf)
        periodPolicy.checkSource(source.parent)?.let { return Outcome.Failure(it) }

        val targetDirectory = when (mode) {
            OrganizeMode.RENAME_ONLY -> source.parent
            OrganizeMode.RENAME_AND_MOVE -> settingsRepository.settings.value.monthFolder
                ?: return Outcome.Failure(FileOperationError.MonthFolderNotSelected)
        }
        if (mode == OrganizeMode.RENAME_AND_MOVE) {
            periodPolicy.checkDestination(targetDirectory)?.let { return Outcome.Failure(it) }
        }

        val hierarchy = CategoryHierarchy(categoryRepository.getCategories())
        val category = hierarchy.find(categoryId) ?: return Outcome.Failure(FileOperationError.CategoryNotFound)
        val targetAccount = periodPolicy.describe(targetDirectory).account
        if (!hierarchy.isAllowedIn(category, targetAccount)) {
            return Outcome.Failure(FileOperationError.CategoryNotInMonthAccount)
        }

        val sourceName = source.fileName.toString()
        val isSameDirectory = targetDirectory.normalize() == source.parent.normalize()
        if (isSameDirectory && CategoryNaming.isAlreadyNamedFor(category, sourceName)) {
            return Outcome.Failure(FileOperationError.AlreadyInPlace)
        }
        val existingNames = fileRepository.listFileNames(targetDirectory)
            .filterNot { isSameDirectory && it.equals(sourceName, ignoreCase = true) }
            .toSet()

        val suggestedName = CategoryNaming.suggestedName(
            category, existingNames, description, currentFileName = sourceName.takeIf { isSameDirectory },
        ) ?: return Outcome.Failure(FileOperationError.DescriptionRequired)
        if (!FileNameRules.isValid(suggestedName)) return Outcome.Failure(FileOperationError.InvalidFileName)
        if (isSameDirectory && suggestedName == sourceName) return Outcome.Failure(FileOperationError.AlreadyInPlace)

        return Outcome.Success(
            OrganizationPlan(
                source = source,
                category = category,
                mode = mode,
                targetDirectory = targetDirectory,
                suggestedName = suggestedName,
                hasConflict = FileNameRules.containsIgnoringCase(existingNames, suggestedName),
                numberedCopyName = DuplicateNameResolver.nextNumberedCopy(suggestedName, existingNames),
            ),
        )
    }

    /** Descrição sugerida para categorias descritas (ex.: Despesas); `null` para as demais. */
    suspend fun suggestDescription(source: Path, categoryId: String): String? {
        val category = CategoryHierarchy(categoryRepository.getCategories()).find(categoryId) ?: return null
        if (category.namingRule != NamingRule.DESCRIBED_SEQUENCE) return null
        return DescribedSequenceNaming.suggestDescription(category, source.fileName.toString())
    }
}
