package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.OrganizationPlan
import com.bragadev.fiscal.domain.model.OrganizeMode
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.CategoryRepository
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.rules.CategoryHierarchy
import com.bragadev.fiscal.domain.rules.DuplicateNameResolver
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.rules.FileNameRules
import java.nio.file.Path

/**
 * Renomeia livremente um arquivo que já está na pasta do mês (ex.: corrigir um nome digitado errado).
 * O arquivo fica na mesma pasta; para Despesas use [PlanOrganizationUseCase] com a descrição,
 * que mantém a numeração "3.1", "3.2"...
 */
class PlanRenameUseCase(
    private val fileRepository: FileRepository,
    private val categoryRepository: CategoryRepository,
    private val periodPolicy: EditablePeriodPolicy,
) {
    suspend operator fun invoke(file: Path, categoryId: String, newName: String): Outcome<OrganizationPlan> {
        if (!fileRepository.exists(file)) return Outcome.Failure(FileOperationError.FileNotFound)
        periodPolicy.checkSource(file.parent)?.let { return Outcome.Failure(it) }
        val category = CategoryHierarchy(categoryRepository.getCategories()).find(categoryId)
            ?: return Outcome.Failure(FileOperationError.CategoryNotFound)

        val baseName = FileNameRules.baseName(newName.trim()).trim()
        if (baseName.isEmpty()) return Outcome.Failure(FileOperationError.NameRequired)
        val fileName = FileNameRules.withPdfExtension(baseName)
        if (!FileNameRules.isValid(fileName)) return Outcome.Failure(FileOperationError.InvalidFileName)

        val currentName = file.fileName.toString()
        if (fileName == currentName) return Outcome.Failure(FileOperationError.AlreadyInPlace)
        // Mudar só maiúsculas/minúsculas não conflita com o próprio arquivo.
        val existing = fileRepository.listFileNames(file.parent).filterNot { it.equals(currentName, ignoreCase = true) }.toSet()

        return Outcome.Success(
            OrganizationPlan(
                source = file,
                category = category,
                mode = OrganizeMode.RENAME_ONLY,
                targetDirectory = file.parent,
                suggestedName = fileName,
                hasConflict = FileNameRules.containsIgnoringCase(existing, fileName),
                numberedCopyName = DuplicateNameResolver.nextNumberedCopy(fileName, existing),
            ),
        )
    }
}
