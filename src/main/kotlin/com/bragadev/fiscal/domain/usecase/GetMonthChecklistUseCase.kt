package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.MonthChecklist
import com.bragadev.fiscal.domain.model.getOrNull
import com.bragadev.fiscal.domain.repository.CategoryRepository
import com.bragadev.fiscal.domain.repository.FileFlagRepository
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.rules.CategoryHierarchy
import com.bragadev.fiscal.domain.rules.MonthChecklistBuilder
import java.nio.file.Path

/** Mostra quais categorias já têm arquivo na pasta do mês e quais estão faltando. */
class GetMonthChecklistUseCase(
    private val fileRepository: FileRepository,
    private val categoryRepository: CategoryRepository,
    private val flagRepository: FileFlagRepository,
) {
    suspend operator fun invoke(monthFolder: Path, account: AccountType?): MonthChecklist {
        val pdfNames = fileRepository.listPdfFiles(monthFolder).getOrNull().orEmpty().map { it.name }
        val categories = categoryRepository.getCategories()
        val hierarchy = CategoryHierarchy(categories)
        val allowed = categories.filter { hierarchy.isAllowedIn(it, account) }
        return MonthChecklistBuilder.build(allowed, pdfNames).copy(flags = flagRepository.flagsIn(monthFolder))
    }
}
