package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.AccountFolder
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.MonthTree
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.QuarterFolder
import com.bragadev.fiscal.domain.model.ServiceYearFolder
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.rules.MonthFolderParser
import java.nio.file.Path

/**
 * Monta a árvore conta → ano de serviço → trimestre → mês a partir da pasta raiz das contas,
 * para o usuário escolher o mês com um clique. Pastas que não levam a um mês (ex.:
 * "PASTA DE APROVAÇÕES PERMANENTES") são ignoradas. Nada é alterado em disco.
 */
class BrowseMonthFoldersUseCase(
    private val fileRepository: FileRepository,
    private val periodPolicy: EditablePeriodPolicy,
) {
    suspend operator fun invoke(root: Path): Outcome<MonthTree> {
        if (!fileRepository.isDirectory(root)) return Outcome.Failure(FileOperationError.FolderNotFound)
        val accounts = fileRepository.listSubfolders(root).mapNotNull { folder ->
            val account = MonthFolderParser.detectAccount(folder) ?: return@mapNotNull null
            AccountFolder(account, folder, serviceYears(folder)).takeIf { it.serviceYears.isNotEmpty() }
        }.sortedBy { it.account.ordinal }
        return Outcome.Success(MonthTree(root, accounts))
    }

    private suspend fun serviceYears(account: Path): List<ServiceYearFolder> =
        fileRepository.listSubfolders(account)
            .map { ServiceYearFolder(it.fileName.toString(), it, quarters(it)) }
            .filter { it.quarters.isNotEmpty() }
            .sortedBy { it.name.lowercase() }

    private suspend fun quarters(year: Path): List<QuarterFolder> =
        fileRepository.listSubfolders(year)
            .map { QuarterFolder(it.fileName.toString(), it, months(it)) }
            .filter { it.months.isNotEmpty() }
            .sortedWith(compareBy({ leadingNumber(it.name) }, { it.name.lowercase() }))

    private suspend fun months(quarter: Path) =
        fileRepository.listSubfolders(quarter)
            .map { periodPolicy.describe(it) }
            .filter { it.detectedMonth?.folderName == it.path.fileName.toString() }
            .sortedBy { it.detectedMonth!!.month }

    private fun leadingNumber(name: String): Int =
        Regex("""^\s*(\d+)""").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
}
