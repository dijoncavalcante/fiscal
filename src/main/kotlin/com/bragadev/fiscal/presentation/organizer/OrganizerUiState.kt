package com.bragadev.fiscal.presentation.organizer

import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.rules.*
import com.bragadev.fiscal.presentation.common.UserMessage
import java.nio.file.Path
import java.time.YearMonth
import java.util.*

data class OrganizerUiState(
    val groups: List<AccountGroup> = emptyList(),
    val monthFolder: MonthFolderInfo? = null,
    /** O que já existe na pasta do mês; `null` sem pasta de mês selecionada. */
    val checklist: MonthChecklist? = null,
    val firstEditableMonth: YearMonth = YearMonth.now(),
    val dialog: OrganizerDialog? = null,
    val isWorking: Boolean = false,
    val lastUndoableOperationId: UUID? = null,
    val message: UserMessage? = null,
    /** Conferência das despesas e do extrato bancário, por nome de arquivo em minúsculas. */
    val expenseChecks: Map<String, ExpenseStatus> = emptyMap(),
    /** De onde veio o total quando o valor bateu (ex.: "R$ 50,00 abaixo de "TOTAL (R$)", página 1 (foto)"). */
    val expenseEvidence: Map<String, String> = emptyMap(),
    /** "Checar pendências" em andamento (o botão mostra que está conferindo). */
    val isCheckingAll: Boolean = false,
) {
    fun expenseStatus(fileName: String): ExpenseStatus? = expenseChecks[fileName.lowercase()]

    fun expenseEvidence(fileName: String): String? = expenseEvidence[fileName.lowercase()]

    /** Só é possível organizar documentos com um mês identificado e liberado. */
    val canOrganize: Boolean get() = monthFolder?.isEditable == true

    /** "Checar pendências" só aparece em conta com documentos conferidos (folha, donativos, despesas, TO-62, relatório, extrato). */
    val canCheckIssues: Boolean
        get() = monthFolder?.account.let { account ->
            AccountSheetRules.categoryFor(account) != null || ExpenseDocumentRules.categoryFor(account) != null ||
                    BankStatementRules.categoryFor(account) != null || MonthlyReportRules.categoryFor(account) != null ||
                    DonationRules.categoryFor(account) != null || TransferRecordRules.categoryFor(account) != null
        }

    companion object {
        /** Como os arquivos desta categoria são conferidos (muda os textos do "certo" e do "conferindo"). */
        fun checkedDocumentOf(category: DocumentCategory?): CheckedDocument = when {
            BankStatementRules.isStatementCategory(category?.id) -> CheckedDocument.BANK_STATEMENT
            AccountSheetRules.isSheetCategory(category?.id) -> CheckedDocument.ACCOUNT_SHEET
            MonthlyReportRules.isReportCategory(category?.id) -> CheckedDocument.MONTHLY_REPORT
            DonationRules.isDonationCategory(category?.id) -> CheckedDocument.DONATION
            TransferRecordRules.isTransferRecordCategory(category?.id) -> CheckedDocument.TRANSFER_RECORD
            else -> CheckedDocument.EXPENSE
        }
    }
}

/** Documento conferido automaticamente: despesa, extrato bancário, Folha de Contas, Relatório Mensal, donativo ou TO-62. */
enum class CheckedDocument { EXPENSE, BANK_STATEMENT, ACCOUNT_SHEET, MONTHLY_REPORT, DONATION, TRANSFER_RECORD }

sealed interface OrganizerDialog {
    /**
     * Proposta inicial após soltar o arquivo: mostra nome atual, novo nome, mês e destino.
     * Um dos planos pode ser nulo quando aquela opção não se aplica.
     */
    data class Proposal(
        val source: Path,
        val category: DocumentCategory,
        val renamePlan: OrganizationPlan?,
        val movePlan: OrganizationPlan?,
        /** Texto editável que entra no nome (ex.: Despesas); `null` quando a categoria não usa descrição. */
        val description: String? = null,
        /** Problema com a descrição digitada, mostrado logo abaixo do campo. */
        val inputError: String? = null,
    ) : OrganizerDialog {
        val needsDescription: Boolean get() = description != null
    }

    /** O nome de destino já existe e o usuário precisa escolher o que fazer. */
    data class Conflict(val plan: OrganizationPlan) : OrganizerDialog

    /** Confirmação final, com o nome que será efetivamente usado. */
    data class Confirm(val plan: OrganizationPlan, val resolution: DuplicateResolution?) : OrganizerDialog {
        val finalName: String
            get() = if (resolution == DuplicateResolution.NUMBERED_COPY) plan.numberedCopyName else plan.suggestedName
    }
}
