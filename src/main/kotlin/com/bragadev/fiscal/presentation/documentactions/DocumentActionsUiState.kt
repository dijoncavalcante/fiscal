package com.bragadev.fiscal.presentation.documentactions

import com.bragadev.fiscal.presentation.common.UserMessage
import java.nio.file.Path

/** Ações do menu do botão direito na lista de documentos (esquerda): renomear, copiar e excluir. */
data class DocumentActionsUiState(
    val dialog: DocumentActionDialog? = null,
    val isWorking: Boolean = false,
    val message: UserMessage? = null,
)

sealed interface DocumentActionDialog {
    val file: Path

    /**
     * Renomear na própria pasta.
     *
     * @param name nome digitado, sem a extensão (a extensão do arquivo é mantida).
     * @param target caminho com o novo nome; `null` enquanto não houver mudança válida.
     */
    data class Rename(
        override val file: Path,
        val name: String,
        val target: Path? = null,
        val inputError: String? = null,
    ) : DocumentActionDialog {
        val canConfirm: Boolean get() = target != null && inputError == null
    }

    /**
     * Confirmar a exclusão (o arquivo vai para a lixeira do FISCAL).
     *
     * @param selectAfter documento que fica selecionado depois (o vizinho na lista).
     */
    data class Delete(override val file: Path, val selectAfter: Path? = null) : DocumentActionDialog
}
