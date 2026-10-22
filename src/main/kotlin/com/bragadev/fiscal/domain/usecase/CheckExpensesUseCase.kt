package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.repository.*
import com.bragadev.fiscal.domain.rules.BankStatementRules
import com.bragadev.fiscal.domain.rules.ExpenseDocumentRules
import com.bragadev.fiscal.domain.rules.ExpenseExemptionRules
import java.nio.file.Path
import java.time.YearMonth

/**
 * Confere os PDFs do mês que têm regras: as despesas ("3. Despesas") das contas da Congregação e da Manutenção —
 * assinatura do responsável configurado, comprovante Pix/TED e, quando exigido, cupom/nota com o mesmo valor
 * ([ExpenseDocumentRules]) — e o extrato bancário de cada conta — conta certa, período do mês em edição e
 * assinatura de quem assina os extratos ([BankStatementRules]). Só lê os PDFs; nada é alterado no disco.
 *
 * - Documento incompleto recebe uma pendência dizendo o que falta; ao ser corrigido, a pendência sai sozinha.
 *   Pendências escritas pelo usuário nunca são trocadas nem removidas.
 * - O resultado fica guardado enquanto o arquivo e as exigências não mudarem (tamanho, data, responsável,
 *   isenção, mês): o OCR não se repete, e uma pendência automática que o usuário removeu não volta.
 */
class CheckExpensesUseCase(
    private val fileRepository: FileRepository,
    private val reader: DocumentContentReader,
    private val checkRepository: ExpenseCheckRepository,
    private val flagRepository: FileFlagRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** Nomes dos arquivos conferidos neste mês: despesas e extrato bancário (vazio se a conta não tem). */
    fun expenseFiles(account: AccountType?, checklist: MonthChecklist): List<String> =
        listOfNotNull(ExpenseDocumentRules.categoryFor(account), BankStatementRules.categoryFor(account))
            .flatMap(checklist::filesFor)

    /** O que é exigido deste arquivo: o responsável da conta e, na Congregação, a isenção pelo nome. */
    fun requirementsFor(account: AccountType?, fileName: String): ExpenseRequirements {
        val settings = settingsRepository.settings.value
        val exemptBy = if (ExpenseDocumentRules.hasExemptions(account)) {
            ExpenseExemptionRules.exemptBy(fileName, settings.expenseExemptTerms)
        } else null
        return ExpenseRequirements(settings.expenseSigners[account], exemptBy)
    }

    /** O que é exigido do extrato bancário da conta no mês; `null` se a conta não tem extrato conferido. */
    fun statementRequirementsFor(account: AccountType?, month: YearMonth?): StatementRequirements? {
        val profile = BankStatementRules.accountFor(account) ?: return null
        return StatementRequirements(settingsRepository.settings.value.statementSigner, month, profile)
    }

    /**
     * Confere cada arquivo da pasta do mês, um por vez, avisando cada resultado em [onResult]
     * (nome do arquivo, situação, de onde veio o total / o que foi conferido, quando está certo).
     *
     * @param month mês em edição: o extrato precisa ser dele.
     * @return `true` se alguma pendência foi criada, trocada ou removida (a lista precisa ser relida).
     */
    suspend operator fun invoke(
        monthFolder: Path,
        account: AccountType?,
        checklist: MonthChecklist,
        month: YearMonth? = null,
        onResult: (String, ExpenseStatus, String?) -> Unit = { _, _, _ -> },
    ): Boolean {
        val statementCategory = BankStatementRules.categoryFor(account)
        val statements = statementCategory?.let(checklist::filesFor).orEmpty().map { it.lowercase() }.toSet()
        val names = expenseFiles(account, checklist)
        if (names.isEmpty()) return false
        val documents =
            fileRepository.listPdfFiles(monthFolder).getOrNull().orEmpty().associateBy { it.name.lowercase() }
        val stored = checkRepository.checksIn(monthFolder)
        var flagsChanged = false
        for (name in names) {
            val document = documents[name.lowercase()] ?: continue
            val previous = stored[name.lowercase()]
            val plan = if (name.lowercase() in statements) {
                statementPlan(statementRequirementsFor(account, month) ?: continue)
            } else {
                expensePlan(requirementsFor(account, document.name))
            }
            if (previous != null && previous.matches(document, plan.key, plan.rulesVersion)) {
                onResult(name, statusOf(previous.check), previous.check.amountEvidence)
                continue
            }
            val (check, note) = checkDocument(document, plan)
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
                    rulesVersion = plan.rulesVersion,
                    requirementsKey = plan.key,
                ),
            )
            if (updateFlag(document.path, checklist.flagFor(name), note)) flagsChanged = true
            onResult(name, statusOf(check), check.amountEvidence)
        }
        return flagsChanged
    }

    /** Como conferir um arquivo: regras, exigências (chave do resultado guardado) e o texto da pendência. */
    private class Plan(
        val key: String,
        val rulesVersion: Int,
        val isEnough: (DocumentContent) -> Boolean,
        val check: (DocumentContent) -> Pair<ExpenseCheck, String?>,
    )

    private fun expensePlan(requirements: ExpenseRequirements) = Plan(
        requirements.key,
        EXPENSE_RULES_VERSION,
        { ExpenseDocumentRules.hasEnoughText(it, requirements) },
        { content ->
            val check = ExpenseDocumentRules.check(content, requirements)
            check to ExpenseDocumentRules.issueNote(check)
        },
    )

    private fun statementPlan(requirements: StatementRequirements) = Plan(
        requirements.key,
        STATEMENT_RULES_VERSION,
        { BankStatementRules.hasEnoughText(it, requirements) },
        { content -> BankStatementRules.check(content, requirements).let { it.check to it.note } },
    )

    /** Resultado `null` quando não deu para ler agora (arquivo em uso, OCR indisponível): tenta de novo depois. */
    private suspend fun checkDocument(document: Document, plan: Plan): Pair<ExpenseCheck?, String?> =
        when (val content = reader.read(document.path, plan.isEnough)) {
            is Outcome.Success -> plan.check(content.value)
            is Outcome.Failure -> if (content.error == FileOperationError.InvalidPdf) {
                val check = ExpenseCheck(unreadable = true)
                check to ExpenseDocumentRules.issueNote(check)
            } else null to null
        }

    /** Grava, troca ou remove a pendência automática. Devolve `true` se mudou algo. */
    private suspend fun updateFlag(file: Path, current: String?, note: String?): Boolean {
        val isOurs = current == null || ExpenseDocumentRules.isAutomaticNote(current) ||
                BankStatementRules.isAutomaticNote(current)
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
