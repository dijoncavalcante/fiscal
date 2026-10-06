package com.bragadev.fiscal.presentation.history

import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.usecase.HistoryEntry
import com.bragadev.fiscal.domain.usecase.ListHistoryUseCase
import com.bragadev.fiscal.domain.usecase.UndoOperationUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.text.Normalizer
import java.time.format.DateTimeFormatter

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    /** Operação esperando o "sim" do usuário para ser desfeita. */
    val confirmUndo: HistoryEntry? = null,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val visible: List<HistoryEntry> get() = HistoryViewModel.filter(entries, query)
}

/** Tela "Histórico": todas as operações, com busca e "Desfazer" em qualquer uma que ainda possa ser desfeita. */
class HistoryViewModel(
    private val listHistory: ListHistoryUseCase,
    private val undoOperation: UndoOperationUseCase,
    private val documentChanges: DocumentChangeNotifier,
) : ViewModel() {
    private val state = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = state.asStateFlow()

    init {
        // Organizar, renomear ou desfazer em outra tela muda o que pode ser desfeito aqui.
        scope.launch { documentChanges.changes.collect { reload() } }
    }

    /** Recarrega ao abrir a tela: arquivos podem ter sido mexidos no Explorer. */
    fun onOpen() {
        state.update { it.copy(message = null) }
        scope.launch { reload() }
    }

    fun onQueryChanged(text: String) {
        state.update { it.copy(query = text) }
    }

    fun onUndoRequested(entry: HistoryEntry) {
        if (entry.canUndo) state.update { it.copy(confirmUndo = entry, message = null) }
    }

    fun onDismissUndo() {
        state.update { it.copy(confirmUndo = null) }
    }

    fun onUndoConfirmed() {
        val entry = state.value.confirmUndo ?: return
        state.update { it.copy(confirmUndo = null, isWorking = true) }
        scope.launch {
            when (val result = undoOperation(entry.operation.id)) {
                is Outcome.Success -> {
                    state.update { it.copy(message = Strings.UNDONE, isError = false) }
                    documentChanges.notifyChanged(selectPath = Path.of(result.value.originalPath))
                }
                is Outcome.Failure -> state.update { it.copy(message = result.error.toUserMessage(), isError = true) }
            }
            reload()
            state.update { it.copy(isWorking = false) }
        }
    }

    private suspend fun reload() {
        val entries = listHistory()
        state.update { it.copy(entries = entries, isLoading = false) }
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

        fun formatDate(entry: HistoryEntry): String = dateFormat.format(entry.operation.timestamp)

        /**
         * Busca sem diferenciar maiúsculas nem acentos, por nome antigo/novo, pastas ou data
         * ("despesa", "junho", "05/10/2026"). Todas as palavras digitadas precisam aparecer.
         */
        fun filter(entries: List<HistoryEntry>, query: String): List<HistoryEntry> {
            val words = normalize(query).split(' ').filter { it.isNotBlank() }
            if (words.isEmpty()) return entries
            return entries.filter { entry ->
                val operation = entry.operation
                val text = normalize(
                    listOf(operation.originalName, operation.newName, operation.originalPath, operation.newPath, formatDate(entry))
                        .joinToString(" "),
                )
                words.all { it in text }
            }
        }

        private fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }
}
