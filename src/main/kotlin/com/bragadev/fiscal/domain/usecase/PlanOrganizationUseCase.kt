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
import com.bragadev.fiscal.domain.rules.DuplicateNameResolver
import com.bragadev.fiscal.domain.rules.FileNameRules
import com.bragadev.fiscal.domain.rules.SequentialNaming
import java.nio.file.Path

/**
 * Calcula destino, novo nome e conflitos de uma organização, sem alterar nada em disco.
 */
class PlanOrganizationUseCase(
    private val categoryRepository: CategoryRepository,
    private val fileRepository: FileRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(source: Path, categoryId: String, mode: OrganizeMode): Outcome<OrganizationPlan> {
        val root = settingsRepository.settings.value.rootPath
            ?: return Outcome.Failure(FileOperationError.RootNotConfigured)
        if (!fileRepository.exists(source)) return Outcome.Failure(FileOperationError.FileNotFound)
        if (!fileRepository.isPdf(source)) return Outcome.Failure(FileOperationError.InvalidPdf)

        val hierarchy = CategoryHierarchy(categoryRepository.getCategories())
        val category = hierarchy.find(categoryId) ?: return Outcome.Failure(FileOperationError.CategoryNotFound)
        val targetDirectory = targetDirectoryFor(source, root, category, hierarchy, mode)

        val sourceName = source.fileName.toString()
        val isSameDirectory = targetDirectory.normalize() == source.parent.normalize()
        val existingNames = fileRepository.listFileNames(targetDirectory)
            .filterNot { isSameDirectory && it.equals(sourceName, ignoreCase = true) }
            .toSet()

        if (isSameDirectory && alreadyNamedFor(category, sourceName)) {
            return Outcome.Failure(FileOperationError.AlreadyInPlace)
        }

        val suggestedName = CategoryNaming.suggestedName(category, existingNames)
        if (!FileNameRules.isValid(suggestedName)) return Outcome.Failure(FileOperationError.InvalidFileName)

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

    private fun targetDirectoryFor(
        source: Path,
        root: Path,
        category: DocumentCategory,
        hierarchy: CategoryHierarchy,
        mode: OrganizeMode,
    ): Path = when (mode) {
        OrganizeMode.RENAME_ONLY -> source.parent
        OrganizeMode.RENAME_AND_MOVE -> hierarchy.folderSegments(category).fold(root) { path, segment -> path.resolve(segment) }
    }

    private fun alreadyNamedFor(category: DocumentCategory, fileName: String): Boolean = when (category.namingRule) {
        NamingRule.CATEGORY_NAME -> fileName.equals(FileNameRules.withPdfExtension(category.name), ignoreCase = true)
        NamingRule.SEQUENTIAL -> SequentialNaming.matches(category.name, fileName)
    }
}
