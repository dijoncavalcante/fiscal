package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.repository.DocumentContentReader
import com.bragadev.fiscal.domain.repository.ExpenseCheckRepository
import com.bragadev.fiscal.domain.repository.FileFlagRepository
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.rules.ExpenseDocumentRules
import java.nio.file.Path

/**
 * Confere as despesas ("3. Despesas") da Conta da Manutenção: cupom ou nota fiscal, comprovante Pix/TED e
 * assinatura (regras em [ExpenseDocumentRules]). Só lê os PDFs; nada é alterado no disco.
 *
 * - Despesa incompleta recebe uma pendência dizendo o que falta; ao ser corrigida, a pendência sai sozinha.
 *   Pendências escritas pelo usuário nunca são trocadas nem removidas.
 * - O resultado fica guardado enquanto o arquivo não mudar (tamanho e data): o OCR não se repete, e uma
 *   pendência automática que o usuário removeu não volta.
 */
class CheckExpensesUseCase(
    private val fileRepository: FileRepository,
    private val reader: DocumentContentReader,
    private val checkRepository: ExpenseCheckRepository,
    private val flagRepository: FileFlagRepository,
) {
    /** Nomes dos arquivos conferidos neste mês (vazio se o mês não é da Conta da Manutenção). */
    fun expenseFiles(account: AccountType?, checklist: MonthChecklist): List<String> =
        if (account == AccountType.MANUTENCAO) checklist.filesFor(ExpenseDocumentRules.CATEGORY_ID) else emptyList()

    /**
     * Confere cada despesa da pasta do mês, uma por vez, avisando cada resultado em [onResult]
     * (nome do arquivo, situação).
     *
     * @return `true` se alguma pendência foi criada, trocada ou removida (a lista precisa ser relida).
     */
    suspend operator fun invoke(
        monthFolder: Path,
        account: AccountType?,
        checklist: MonthChecklist,
        onResult: (String, ExpenseStatus) -> Unit = { _, _ -> },
    ): Boolean {
        val names = expenseFiles(account, checklist)
        if (names.isEmpty()) return false
        val documents =
            fileRepository.listPdfFiles(monthFolder).getOrNull().orEmpty().associateBy { it.name.lowercase() }
        val stored = checkRepository.checksIn(monthFolder)
        var flagsChanged = false
        for (name in names) {
            val document = documents[name.lowercase()] ?: continue
            val previous = stored[name.lowercase()]
            if (previous != null && previous.matches(document)) {
                onResult(name, statusOf(previous.check))
                continue
            }
            val check = checkDocument(document)
            if (check == null) {
                onResult(name, ExpenseStatus.NOT_CHECKED)
                continue
            }
            checkRepository.save(document.path, StoredExpenseCheck(document.sizeBytes, document.lastModified, check))
            if (updateFlag(
                    document.path,
                    checklist.flagFor(name),
                    ExpenseDocumentRules.issueNote(check)
                )
            ) flagsChanged = true
            onResult(name, statusOf(check))
        }
        return flagsChanged
    }

    /** `null` quando não deu para ler agora (arquivo em uso, OCR indisponível): tenta de novo depois. */
    private suspend fun checkDocument(document: Document): ExpenseCheck? =
        when (val content = reader.read(document.path, ExpenseDocumentRules::hasEnoughText)) {
            is Outcome.Success -> ExpenseDocumentRules.check(content.value)
            is Outcome.Failure -> if (content.error == FileOperationError.InvalidPdf) ExpenseCheck(unreadable = true) else null
        }

    /** Grava, troca ou remove a pendência automática. Devolve `true` se mudou algo. */
    private suspend fun updateFlag(file: Path, current: String?, note: String?): Boolean {
        val isOurs = current == null || ExpenseDocumentRules.isAutomaticNote(current)
        return when {
            !isOurs || current == note -> false
            note == null -> {
                flagRepository.clear(file)
                true
            }

            else -> {
                flagRepository.set(file, note)
                true
            }
        }
    }

    private fun statusOf(check: ExpenseCheck): ExpenseStatus =
        if (check.isValid) ExpenseStatus.VERIFIED else ExpenseStatus.HAS_ISSUES
}
