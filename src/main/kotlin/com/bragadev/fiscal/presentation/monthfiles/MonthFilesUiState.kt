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

sealed interface MonthFileDialog {
    val file: Path

    /**
     * Renomear um arquivo do mês. Para Despesas, [input] é só a descrição e o número é mantido;
     * nas demais categorias, [input] é o nome completo (sem ".pdf").
     */
    data class Rename(
        override val file: Path,
        val category: DocumentCategory,
        val input: String,
        val keepsNumber: Boolean,
        val plan: OrganizationPlan? = null,
        val inputError: String? = null,
    ) : MonthFileDialog {
        val canConfirm: Boolean get() = plan != null && inputError == null
    }

    /** Retirar do mês: o arquivo volta para a pasta de origem ([target]). */
    data class Remove(override val file: Path, val target: Path) : MonthFileDialog

    /** Marcar pendência no arquivo, com uma nota curta. */
    data class Flag(override val file: Path, val note: String) : MonthFileDialog
}
