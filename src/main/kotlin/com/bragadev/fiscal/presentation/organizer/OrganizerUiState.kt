package com.bragadev.fiscal.presentation.organizer

import com.bragadev.fiscal.domain.model.AccountGroup
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.DuplicateResolution
import com.bragadev.fiscal.domain.model.OrganizationPlan
import com.bragadev.fiscal.presentation.common.UserMessage
import java.nio.file.Path
import java.util.UUID

data class OrganizerUiState(
    val groups: List<AccountGroup> = emptyList(),
    val rootPath: Path? = null,
    val dialog: OrganizerDialog? = null,
    val isWorking: Boolean = false,
    val lastUndoableOperationId: UUID? = null,
    val message: UserMessage? = null,
) {
    /** Destino mostrado ao usuário, relativo à pasta raiz: "Conta da Congregação/8. Extrato Bancário/". */
    fun destinationLabel(directory: Path): String {
        val root = rootPath
        val relative = if (root != null && directory.startsWith(root)) root.relativize(directory) else directory
        val segments = relative.map { it.toString() }.filter { it.isNotBlank() }
        return if (segments.isEmpty()) "${root?.fileName ?: directory}/" else segments.joinToString("/", postfix = "/")
    }
}

sealed interface OrganizerDialog {
    /**
     * Proposta inicial após soltar o arquivo: mostra nome atual, novo nome e destino.
     * Um dos planos pode ser nulo quando aquela opção não se aplica.
     */
    data class Proposal(
        val source: Path,
        val category: DocumentCategory,
        val renamePlan: OrganizationPlan?,
        val movePlan: OrganizationPlan?,
    ) : OrganizerDialog

    /** O nome de destino já existe e o usuário precisa escolher o que fazer. */
    data class Conflict(val plan: OrganizationPlan) : OrganizerDialog

    /** Confirmação final, com o nome que será efetivamente usado. */
    data class Confirm(val plan: OrganizationPlan, val resolution: DuplicateResolution?) : OrganizerDialog {
        val finalName: String
            get() = if (resolution == DuplicateResolution.NUMBERED_COPY) plan.numberedCopyName else plan.suggestedName
    }
}
