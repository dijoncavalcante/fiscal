package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.DocumentCategory
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
     * @param sequenceIndex número escolhido pelo usuário nessas categorias (0 = "3.", 2 = "3.2"); `null` = automático.
     */
    suspend operator fun invoke(
        source: Path,
        categoryId: String,
        mode: OrganizeMode,
        description: String? = null,
        sequenceIndex: Int? = null,
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
            category, existingNames, description,
            currentFileName = sourceName.takeIf { isSameDirectory },
            sequenceIndex = sequenceIndex,
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
                sameNumberFiles = sameNumberFiles(category, suggestedName, existingNames),
            ),
        )
    }

    /** Arquivos com o mesmo número na sequência (ex.: outro "3.2 Despesa - ..."), só para categorias descritas. */
    private fun sameNumberFiles(category: DocumentCategory, newName: String, existingNames: Set<String>): List<String> {
        if (category.namingRule != NamingRule.DESCRIBED_SEQUENCE) return emptyList()
        val index = DescribedSequenceNaming.indexOf(category, newName) ?: return emptyList()
        return existingNames.filter { DescribedSequenceNaming.indexOf(category, it) == index && !it.equals(newName, ignoreCase = true) }
            .sorted()
    }

    /** Descrição sugerida para categorias descritas (ex.: Despesas); `null` para as demais. */
    suspend fun suggestDescription(source: Path, categoryId: String): String? {
        val category = CategoryHierarchy(categoryRepository.getCategories()).find(categoryId) ?: return null
        if (category.namingRule != NamingRule.DESCRIBED_SEQUENCE) return null
        return DescribedSequenceNaming.suggestDescription(category, source.fileName.toString())
    }

    /** Número atual do arquivo na sequência ("3", "3.2"); vazio se ele ainda não tiver número desta categoria. */
    suspend fun currentNumber(source: Path, categoryId: String): String {
        val category = CategoryHierarchy(categoryRepository.getCategories()).find(categoryId) ?: return ""
        val index = DescribedSequenceNaming.indexOf(category, source.fileName.toString()) ?: return ""
        return DescribedSequenceNaming.numberText(category, index)
    }
}
