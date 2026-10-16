package com.bragadev.fiscal.presentation.documentactions

import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.FileNameRules
import com.bragadev.fiscal.domain.usecase.DeleteDocumentUseCase
import com.bragadev.fiscal.domain.usecase.RenameDocumentUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.UserMessage
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import com.bragadev.fiscal.presentation.components.DesktopActions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

/**
 * Renomear, copiar e excluir documentos da lista da esquerda (menu do botão direito, F2, Ctrl+C e Delete).
 * Renomear e excluir entram no histórico e podem ser desfeitos; excluir nunca apaga (vai para a lixeira do FISCAL).
 *
 * @param copyFiles coloca os arquivos na área de transferência do Windows; `false` se não conseguiu.
 */
class DocumentActionsViewModel(
    private val renameDocument: RenameDocumentUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val documentChanges: DocumentChangeNotifier,
    private val copyFiles: (List<Path>) -> Boolean = DesktopActions::copyFilesToClipboard,
) : ViewModel() {
    private val state = MutableStateFlow(DocumentActionsUiState())
    val uiState: StateFlow<DocumentActionsUiState> = state.asStateFlow()
    private var inputJob: Job? = null

    // ---- Renomear ----

    fun onRenameRequested(file: Path) {
        if (state.value.dialog != null) return
        val name = file.fileName.toString()
        // Ainda sem alteração: o botão fica desativado até o usuário mudar o nome.
        state.update { it.copy(dialog = DocumentActionDialog.Rename(file, name.dropLast(FileNameRules.extension(name).length))) }
    }

    /** Aplica a digitação e confere o novo nome depois de uma pausa. */
    fun onRenameNameChanged(text: String) {
        val dialog = state.value.dialog as? DocumentActionDialog.Rename ?: return
        val edited = dialog.copy(name = text, target = null, inputError = null)
        state.update { it.copy(dialog = edited) }
        inputJob?.cancel()
        inputJob = scope.launch {
            delay(INPUT_DEBOUNCE_MS)
            val checked = withPlan(edited)
            state.update { current -> if (current.dialog == edited) current.copy(dialog = checked) else current }
        }
    }

    fun onRenameConfirmed() {
        val dialog = state.value.dialog as? DocumentActionDialog.Rename ?: return
        inputJob?.cancel()
        scope.launch {
            // Confere de novo: outro arquivo pode ter ocupado o nome enquanto o diálogo estava aberto.
            val fresh = withPlan(dialog)
            if (!fresh.canConfirm) return@launch state.update { it.copy(dialog = fresh) }
            run(Strings.FILE_RENAMED) { renameDocument(dialog.file, dialog.name) }
        }
    }

    // ---- Excluir ----

    /** @param selectAfter documento que fica selecionado depois da exclusão. */
    fun onDeleteRequested(file: Path, selectAfter: Path? = null) {
        if (state.value.dialog != null) return
        scope.launch {
            when (val error = deleteDocument.check(file)) {
                null -> state.update { it.copy(dialog = DocumentActionDialog.Delete(file, selectAfter)) }
                else -> showError(error.toUserMessage())
            }
        }
    }

    fun onDeleteConfirmed() {
        val dialog = state.value.dialog as? DocumentActionDialog.Delete ?: return
        scope.launch { run(Strings.FILE_DELETED, removed = dialog) { deleteDocument(dialog.file) } }
    }

    // ---- Copiar ----

    fun onCopyRequested(file: Path) {
        val copied = copyFiles(listOf(file))
        state.update {
            it.copy(message = if (copied) UserMessage(Strings.FILE_COPIED) else UserMessage(Strings.FILE_COPY_FAILED, isError = true))
        }
    }

    fun dismissDialog() {
        inputJob?.cancel()
        state.update { it.copy(dialog = null) }
    }

    fun onMessageShown() {
        state.update { it.copy(message = null) }
    }

    // ---- Internos ----

    private suspend fun withPlan(dialog: DocumentActionDialog.Rename): DocumentActionDialog.Rename =
        when (val plan = renameDocument.plan(dialog.file, dialog.name)) {
            is Outcome.Success -> dialog.copy(target = plan.value, inputError = null)
            is Outcome.Failure -> dialog.copy(target = null, inputError = renameError(plan.error))
        }

    private fun renameError(error: FileOperationError): String? = when (error) {
        // Nome igual ao atual não é erro: só não há o que renomear.
        FileOperationError.AlreadyInPlace -> null
        FileOperationError.DestinationAlreadyExists -> Strings.NAME_TAKEN
        else -> error.toUserMessage()
    }

    /**
     * Executa a operação, mostra o resultado com "Desfazer" e atualiza a lista.
     * @param removed exclusão: o arquivo sai da lista e a seleção passa para o vizinho.
     */
    private suspend fun run(
        successText: String,
        removed: DocumentActionDialog.Delete? = null,
        operation: suspend () -> Outcome<FileOperation>,
    ) {
        state.update { it.copy(isWorking = true) }
        when (val result = operation()) {
            is Outcome.Success -> {
                val done = result.value
                state.update { it.copy(dialog = null, message = UserMessage(successText, undoOperationId = done.id)) }
                if (removed != null) {
                    documentChanges.notifyChanged(selectPath = removed.selectAfter, removedPath = removed.file)
                } else {
                    documentChanges.notifyChanged(selectPath = Path.of(done.newPath))
                }
            }
            is Outcome.Failure -> showError(result.error.toUserMessage())
        }
        state.update { it.copy(isWorking = false) }
    }

    private fun showError(text: String) {
        state.update { it.copy(message = UserMessage(text, isError = true)) }
    }

    private companion object {
        const val INPUT_DEBOUNCE_MS = 250L
    }
}
