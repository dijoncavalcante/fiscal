package com.bragadev.fiscal.presentation.monthfiles

import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.OrganizationPlan
import com.bragadev.fiscal.presentation.common.UserMessage
import java.nio.file.Path

/** Ações sobre arquivos que já estão na pasta do mês: renomear, retirar e marcar pendência. */
data class MonthFilesUiState(
    val dialog: MonthFileDialog? = null,
    val isWorking: Boolean = false,
    val message: UserMessage? = null,
)

/** Como o arquivo é renomeado. */
enum class RenameMode {
    /** Despesas e Outros: número da sequência ("3", "3.1", "3.2") e descrição, em campos separados. */
    NUMBER_AND_DESCRIPTION,

    /** Nome completo livre (sem ".pdf"), para qualquer arquivo. */
    FULL_NAME,
}

sealed interface MonthFileDialog {
    val file: Path

    /**
     * Renomear um arquivo do mês.
     *
     * @param description descrição (modo número e descrição).
     * @param number número na sequência (modo número e descrição); vazio = próximo número livre.
     * @param fullName nome completo sem ".pdf" (modo nome completo).
     * @param numberedModeAvailable a categoria usa número + descrição (Despesas, Outros).
     * @param warning aviso que não impede a operação (ex.: outro arquivo com o mesmo número).
     */
    data class Rename(
        override val file: Path,
        val category: DocumentCategory,
        val mode: RenameMode,
        val numberedModeAvailable: Boolean,
        val number: String = "",
        val description: String = "",
        val fullName: String = "",
        val plan: OrganizationPlan? = null,
        val inputError: String? = null,
        val warning: String? = null,
    ) : MonthFileDialog {
        val canConfirm: Boolean get() = plan != null && inputError == null
    }

    /** Retirar do mês: o arquivo volta para a pasta de origem ([target]). */
    data class Remove(override val file: Path, val target: Path) : MonthFileDialog

    /** Marcar pendência no arquivo, com uma nota curta. */
    data class Flag(override val file: Path, val note: String) : MonthFileDialog
}
