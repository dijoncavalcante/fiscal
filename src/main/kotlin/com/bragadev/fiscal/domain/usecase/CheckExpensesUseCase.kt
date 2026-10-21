package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.repository.*
import com.bragadev.fiscal.domain.rules.ExpenseDocumentRules
import com.bragadev.fiscal.domain.rules.ExpenseExemptionRules
import java.nio.file.Path

/**
 * Confere as despesas ("3. Despesas") das contas da Congregação e da Manutenção: assinatura do responsável
 * configurado, comprovante Pix/TED e, quando exigido, cupom/nota com o mesmo valor (regras em
 * [ExpenseDocumentRules]). Só lê os PDFs; nada é alterado no disco.
 *
 * - Despesa incompleta recebe uma pendência dizendo o que falta; ao ser corrigida, a pendência sai sozinha.
 *   Pendências escritas pelo usuário nunca são trocadas nem removidas.
 * - O resultado fica guardado enquanto o arquivo e as exigências não mudarem (tamanho, data, responsável e
 *   isenção): o OCR não se repete, e uma pendência automática que o usuário removeu não volta.
 */
class CheckExpensesUseCase(
    private val fileRepository: FileRepository,
    private val reader: DocumentContentReader,
    private val checkRepository: ExpenseCheckRepository,
    private val flagRepository: FileFlagRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** Nomes dos arquivos conferidos neste mês (vazio se a conta não tem despesas conferidas). */
    fun expenseFiles(account: AccountType?, checklist: MonthChecklist): List<String> =
        ExpenseDocumentRules.categoryFor(account)?.let(checklist::filesFor).orEmpty()

    /** O que é exigido deste arquivo: o responsável da conta e, na Congregação, a isenção pelo nome. */
    fun requirementsFor(account: AccountType?, fileName: String): ExpenseRequirements {
        val settings = settingsRepository.settings.value
        val exemptBy = if (ExpenseDocumentRules.hasExemptions(account)) {
            ExpenseExemptionRules.exemptBy(fileName, settings.expenseExemptTerms)
        } else null
        return ExpenseRequirements(settings.expenseSigners[account], exemptBy)
    }

    /**
     * Confere cada despesa da pasta do mês, uma por vez, avisando cada resultado em [onResult]
     * (nome do arquivo, situação, de onde veio o total quando o valor bateu).
     *
     * @return `true` se alguma pendência foi criada, trocada ou removida (a lista precisa ser relida).
     */
    suspend operator fun invoke(
        monthFolder: Path,
        account: AccountType?,
        checklist: MonthChecklist,
        onResult: (String, ExpenseStatus, String?) -> Unit = { _, _, _ -> },
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
            val requirements = requirementsFor(account, document.name)
            if (previous != null && previous.matches(document, requirements)) {
                onResult(name, statusOf(previous.check), previous.check.amountEvidence)
                continue
            }
            val check = checkDocument(document, requirements)
            if (check == null) {
                onResult(name, ExpenseStatus.NOT_CHECKED, null)
                continue
            }
            checkRepository.save(
                document.path,
                StoredExpenseCheck(
                    document.sizeBytes,
                    document.lastModified,
                    check,
                    requirementsKey = requirements.key
                ),
            )
            if (updateFlag(
                    document.path,
                    checklist.flagFor(name),
                    ExpenseDocumentRules.issueNote(check)
                )
            ) flagsChanged = true
            onResult(name, statusOf(check), check.amountEvidence)
        }
        return flagsChanged
    }

    /** `null` quando não deu para ler agora (arquivo em uso, OCR indisponível): tenta de novo depois. */
    private suspend fun checkDocument(document: Document, requirements: ExpenseRequirements): ExpenseCheck? =
        when (val content = reader.read(document.path) { ExpenseDocumentRules.hasEnoughText(it, requirements) }) {
            is Outcome.Success -> ExpenseDocumentRules.check(content.value, requirements)
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
