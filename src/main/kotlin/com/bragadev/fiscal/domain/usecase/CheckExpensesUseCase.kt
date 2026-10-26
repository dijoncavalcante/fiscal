package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.repository.*
import com.bragadev.fiscal.domain.rules.*
import java.nio.file.Path
import java.time.YearMonth

/**
 * Confere os PDFs do mês que têm regras: as despesas ("3. Despesas") das contas da Congregação e da Manutenção —
 * assinatura do responsável configurado, comprovante Pix/TED e, quando exigido, cupom/nota com o mesmo valor
 * ([ExpenseDocumentRules]) — e o extrato bancário de cada conta — conta certa, período do mês em edição e
 * assinatura de quem assina os extratos ([BankStatementRules]) — e a Folha de Contas (S-26) — título, nome da
 * conta e mês em edição ([AccountSheetRules]) — e o Relatório Mensal (S-30 / S-44) — título do formulário da
 * conta e mês em edição ([MonthlyReportRules]) — e os Donativos das Congregações — dois comprovantes do mês, um
 * de cada congregação, com valor, destino e origem combinados e na ordem da transferência ([DonationRules]).
 * Só lê os PDFs; nada é alterado no disco.
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
    /** Nomes dos arquivos conferidos neste mês: folha, donativos, despesas, relatório mensal e extrato bancário (vazio se a conta não tem). */
    fun expenseFiles(account: AccountType?, checklist: MonthChecklist): List<String> =
        listOfNotNull(
            AccountSheetRules.categoryFor(account),
            DonationRules.categoryFor(account),
            ExpenseDocumentRules.categoryFor(account),
            TransferRecordRules.categoryFor(account),
            MonthlyReportRules.categoryFor(account),
            BankStatementRules.categoryFor(account),
        )
            .flatMap(checklist::filesFor)

    /**
     * O que é exigido deste arquivo: o responsável da conta, a transferência no mês em edição e, na Congregação, a
     * isenção pelo nome.
     */
    fun requirementsFor(account: AccountType?, fileName: String, month: YearMonth? = null): ExpenseRequirements {
        val settings = settingsRepository.settings.value
        val exemptBy = if (ExpenseDocumentRules.hasExemptions(account)) {
            ExpenseExemptionRules.exemptBy(fileName, settings.expenseExemptTerms)
        } else null
        return ExpenseRequirements(settings.expenseSigners[account], exemptBy, month)
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
     * @param month mês em edição: o extrato e a Folha de Contas precisam ser dele.
     * @param only nomes (em minúsculas) que podem ser lidos agora; os demais só mostram o resultado guardado ou
     *   [ExpenseStatus.UNCHECKED]. `null` = todos (botão "Checar pendências").
     * @return `true` se alguma pendência foi criada, trocada ou removida (a lista precisa ser relida).
     */
    suspend operator fun invoke(
        monthFolder: Path,
        account: AccountType?,
        checklist: MonthChecklist,
        month: YearMonth? = null,
        only: Set<String>? = null,
        onResult: (String, ExpenseStatus, String?) -> Unit = { _, _, _ -> },
    ): Boolean {
        val statementCategory = BankStatementRules.categoryFor(account)
        val statements = statementCategory?.let(checklist::filesFor).orEmpty().map { it.lowercase() }.toSet()
        val sheets =
            AccountSheetRules.categoryFor(account)?.let(checklist::filesFor).orEmpty().map { it.lowercase() }.toSet()
        val reports =
            MonthlyReportRules.categoryFor(account)?.let(checklist::filesFor).orEmpty().map { it.lowercase() }.toSet()
        val names = expenseFiles(account, checklist)
        if (names.isEmpty()) return false
        val documents =
            fileRepository.listPdfFiles(monthFolder).getOrNull().orEmpty().associateBy { it.name.lowercase() }
        val stored = checkRepository.checksIn(monthFolder)
        var flagsChanged = false
        val donations = DonationRules.categoryFor(account)?.let(checklist::filesFor).orEmpty()
        if (donations.isNotEmpty()) {
            flagsChanged = checkDonations(donations, documents, stored, checklist, month, only, onResult)
        }
        val transferRecords = TransferRecordRules.categoryFor(account)?.let(checklist::filesFor).orEmpty()
        if (transferRecords.isNotEmpty()) {
            val remittances = TransferRecordRules.remittanceCategoryFor(account)?.let(checklist::filesFor).orEmpty()
            val changed =
                checkTransferRecords(transferRecords, remittances, documents, stored, checklist, only, onResult)
            flagsChanged = changed || flagsChanged
        }
        for (name in names) {
            if (name in donations || name in transferRecords) continue
            val document = documents[name.lowercase()] ?: continue
            val previous = stored[name.lowercase()]
            val plan = if (name.lowercase() in statements) {
                statementPlan(statementRequirementsFor(account, month) ?: continue)
            } else if (name.lowercase() in sheets) {
                sheetPlan(account ?: continue, month)
            } else if (name.lowercase() in reports) {
                reportPlan(account ?: continue, month)
            } else {
                expensePlan(requirementsFor(account, document.name, month))
            }
            if (previous != null && previous.matches(document, plan.key, plan.rulesVersion)) {
                onResult(name, statusOf(previous.check), previous.check.amountEvidence)
                continue
            }
            if (only != null && name.lowercase() !in only) {
                onResult(name, ExpenseStatus.UNCHECKED, null)
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

    private fun sheetPlan(account: AccountType, month: YearMonth?) = Plan(
        "folha|${month ?: ""}|${AccountSheetRules.headerFor(account)}",
        SHEET_RULES_VERSION,
        { AccountSheetRules.hasEnoughText(it, account, month) },
        { content -> AccountSheetRules.check(content, account, month).let { it.check to it.note } },
    )

    private fun reportPlan(account: AccountType, month: YearMonth?) = Plan(
        "relatorio|${month ?: ""}|${MonthlyReportRules.titleFor(account)}",
        REPORT_RULES_VERSION,
        { MonthlyReportRules.hasEnoughText(it, account, month) },
        { content -> MonthlyReportRules.check(content, account, month).let { it.check to it.note } },
    )

    /**
     * Os donativos são conferidos juntos (quantos são, de quais congregações, a ordem pela data e hora): o
     * resultado guardado vale enquanto nenhum arquivo da categoria mudar (nome, tamanho, data) nem o mês.
     */
    private suspend fun checkDonations(
        names: List<String>,
        documents: Map<String, Document>,
        stored: Map<String, StoredExpenseCheck>,
        checklist: MonthChecklist,
        month: YearMonth?,
        only: Set<String>?,
        onResult: (String, ExpenseStatus, String?) -> Unit,
    ): Boolean {
        val files = names.mapNotNull { name -> documents[name.lowercase()]?.let { name to it } }
        if (files.isEmpty()) return false
        val key = "donativos|${month ?: ""}|" +
                files.sortedBy { it.first.lowercase() }.joinToString("|") { (name, doc) ->
                    "${name.lowercase()}:${doc.sizeBytes}:${doc.lastModified.toEpochMilli()}"
                }
        val previous = files.map { (name, document) ->
            stored[name.lowercase()]?.takeIf { it.matches(document, key, DONATION_RULES_VERSION) }
        }
        if (previous.all { it != null }) {
            files.forEachIndexed { i, (name, _) ->
                onResult(name, statusOf(previous[i]!!.check), previous[i]!!.check.amountEvidence)
            }
            return false
        }
        if (only != null && files.none { it.first.lowercase() in only }) {
            files.forEach { (name, _) -> onResult(name, ExpenseStatus.UNCHECKED, null) }
            return false
        }
        val contents = mutableListOf<Pair<String, DocumentContent?>>()
        for ((name, document) in files) {
            when (val content = reader.read(document.path, DonationRules::hasEnoughText)) {
                is Outcome.Success -> contents += name to content.value
                is Outcome.Failure -> if (content.error == FileOperationError.InvalidPdf) contents += name to null else {
                    // Sem um dos arquivos não dá para conferir a ordem nem a quantidade: tenta tudo de novo depois.
                    files.forEach { (other, _) -> onResult(other, ExpenseStatus.NOT_CHECKED, null) }
                    return false
                }
            }
        }
        var flagsChanged = false
        val results = DonationRules.check(contents, month)
        for ((name, document) in files) {
            val (check, note) = results.getValue(name)
            checkRepository.save(
                document.path,
                StoredExpenseCheck(
                    document.sizeBytes,
                    document.lastModified,
                    check,
                    rulesVersion = DONATION_RULES_VERSION,
                    requirementsKey = key,
                ),
            )
            if (updateFlag(document.path, checklist.flagFor(name), note)) flagsChanged = true
            onResult(name, statusOf(check), check.amountEvidence)
        }
        return flagsChanged
    }

    /** Quem precisa assinar o TO-62: o servo de contas e o responsável das despesas da Congregação. */
    fun transferRequirements(): TransferRecordRules.Requirements {
        val settings = settingsRepository.settings.value
        return TransferRecordRules.Requirements(
            settings.accountsServant,
            settings.expenseSigners[AccountType.CONGREGACAO]
        )
    }

    /**
     * O TO-62 é conferido junto com a "5. Remessa Betel" do mês (o total precisa ser igual): o resultado guardado
     * vale enquanto o TO-62, os documentos da remessa (nome, tamanho, data) e quem assina não mudarem. Organizar
     * uma remessa também confere o TO-62 de novo.
     */
    private suspend fun checkTransferRecords(
        names: List<String>,
        remittanceNames: List<String>,
        documents: Map<String, Document>,
        stored: Map<String, StoredExpenseCheck>,
        checklist: MonthChecklist,
        only: Set<String>?,
        onResult: (String, ExpenseStatus, String?) -> Unit,
    ): Boolean {
        val requirements = transferRequirements()
        val remittanceFiles = remittanceNames.mapNotNull { documents[it.lowercase()] }
        val key = "to62|${requirements.key}|" + remittanceFiles.sortedBy { it.name.lowercase() }
            .joinToString("|") { "${it.name.lowercase()}:${it.sizeBytes}:${it.lastModified.toEpochMilli()}" }
        val canRead = only == null || remittanceNames.any { it.lowercase() in only }
        var remittance: TransferRecordRules.Remittance? = null
        var flagsChanged = false
        for (name in names) {
            val document = documents[name.lowercase()] ?: continue
            val previous = stored[name.lowercase()]
            if (previous != null && previous.matches(document, key, TRANSFER_RULES_VERSION)) {
                onResult(name, statusOf(previous.check), previous.check.amountEvidence)
                continue
            }
            if (!canRead && name.lowercase() !in only.orEmpty()) {
                onResult(name, ExpenseStatus.UNCHECKED, null)
                continue
            }
            remittance = remittance ?: readRemittance(remittanceFiles) ?: run {
                onResult(name, ExpenseStatus.NOT_CHECKED, null)
                return flagsChanged
            }
            val plan = Plan(
                key,
                TRANSFER_RULES_VERSION,
                { TransferRecordRules.hasEnoughText(it, requirements, remittance) },
                { content -> TransferRecordRules.check(content, requirements, remittance).let { it.check to it.note } },
            )
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
                    rulesVersion = TRANSFER_RULES_VERSION,
                    requirementsKey = key,
                ),
            )
            if (updateFlag(document.path, checklist.flagFor(name), note)) flagsChanged = true
            onResult(name, statusOf(check), check.amountEvidence)
        }
        return flagsChanged
    }

    /** Total da remessa do mês (soma, se houver mais de um documento); `null` = não deu para ler agora. */
    private suspend fun readRemittance(files: List<Document>): TransferRecordRules.Remittance? {
        var total: java.math.BigDecimal? = java.math.BigDecimal.ZERO
        for (file in files) {
            val content = when (val read = reader.read(file.path)) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> if (read.error == FileOperationError.InvalidPdf) null else return null
            }
            val value = content?.let(TransferRecordRules::remittanceTotal)
            total = if (value == null) null else total?.add(value)
        }
        return TransferRecordRules.Remittance(files.map { it.name }, total.takeIf { files.isNotEmpty() })
    }

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
                BankStatementRules.isAutomaticNote(current) || AccountSheetRules.isAutomaticNote(current) ||
                MonthlyReportRules.isAutomaticNote(current) || DonationRules.isAutomaticNote(current) ||
                TransferRecordRules.isAutomaticNote(current)
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
