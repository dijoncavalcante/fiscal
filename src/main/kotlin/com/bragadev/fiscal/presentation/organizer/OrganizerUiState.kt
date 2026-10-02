package com.bragadev.fiscal.presentation.organizer

import com.bragadev.fiscal.domain.model.AccountGroup
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.DuplicateResolution
import com.bragadev.fiscal.domain.model.MonthChecklist
import com.bragadev.fiscal.domain.model.MonthFolderInfo
import com.bragadev.fiscal.domain.model.OrganizationPlan
import com.bragadev.fiscal.presentation.common.UserMessage
import java.nio.file.Path
import java.time.YearMonth
import java.util.UUID

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
) {
    /** Só é possível organizar documentos com um mês identificado e liberado. */
    val canOrganize: Boolean get() = monthFolder?.isEditable == true
}

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
