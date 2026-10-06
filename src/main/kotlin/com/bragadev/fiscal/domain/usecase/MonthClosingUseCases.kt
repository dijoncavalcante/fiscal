package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.MonthReview
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.map
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.PdfToolsRepository
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.rules.MonthReviewBuilder
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * "Concluir mês", passo 1: confere a pasta do mês — o que existe em cada categoria, o que falta
 * e o que tem pendência. Só lê; funciona também em meses fechados (para gerar o relatório de novo).
 */
class ReviewMonthUseCase(
    private val fileRepository: FileRepository,
    private val getCategoryTree: GetCategoryTreeUseCase,
    private val getMonthChecklist: GetMonthChecklistUseCase,
    private val periodPolicy: EditablePeriodPolicy,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(monthFolder: Path): Outcome<MonthReview> {
        if (!fileRepository.isDirectory(monthFolder)) return Outcome.Failure(FileOperationError.FolderNotFound)
        val info = periodPolicy.describe(monthFolder)
        val month = info.detectedMonth?.month ?: return Outcome.Failure(FileOperationError.MonthNotIdentified)
        val groups = getCategoryTree(info.account)
        val checklist = getMonthChecklist(monthFolder, info.account)
        return Outcome.Success(MonthReviewBuilder.build(groups, checklist, month, info.account, monthFolder, now()))
    }
}

/** "Concluir mês", passo 2: grava o relatório em PDF. Nunca sobrescreve (usa "(2)") nem grava em mês fechado. */
class GenerateMonthReportUseCase(
    private val pdfTools: PdfToolsRepository,
    private val outputResolver: PdfOutputResolver,
) {
    suspend operator fun invoke(review: MonthReview, folder: Path, name: String): Outcome<Path> {
        val target = when (val resolved = outputResolver.resolve(folder, name)) {
            is Outcome.Success -> resolved.value
            is Outcome.Failure -> return resolved
        }
        return pdfTools.monthReport(review, target).map { target }
    }
}
