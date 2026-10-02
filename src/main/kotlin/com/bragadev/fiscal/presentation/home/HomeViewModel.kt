package com.bragadev.fiscal.presentation.home

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.usecase.ChangeSourceFolderUseCase
import com.bragadev.fiscal.domain.usecase.LoadInitialFoldersUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.ScanDocumentsUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.time.Instant

/** Lado esquerdo: a pasta do computador escolhida pelo usuário e seus PDFs. */
class HomeViewModel(
    private val loadInitialFolders: LoadInitialFoldersUseCase,
    private val changeSourceFolder: ChangeSourceFolderUseCase,
    private val scanDocuments: ScanDocumentsUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val documentChanges: DocumentChangeNotifier,
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()

    val currentSourceFolder: Path? get() = observeSettings().value.sourceFolder

    init {
        scope.launch {
            loadInitialFolders()
            observeSettings().map { it.sourceFolder }.distinctUntilChanged().collect { refresh(it) }
        }
        scope.launch {
            documentChanges.changes.collect { change -> refresh(currentSourceFolder, change.selectPath) }
        }
    }

    /** Relê as duas pastas: a de origem (aqui) e a do mês (no organizador). */
    fun onRefresh() {
        documentChanges.notifyChanged()
    }

    fun onSourceFolderSelected(path: Path) {
        scope.launch {
            val result = changeSourceFolder(path)
            if (result is Outcome.Failure) state.update { it.copy(error = result.error.toUserMessage()) }
        }
    }

    fun onDocumentSelected(document: Document) {
        state.update { it.copy(selectedDocument = document) }
    }

    fun onErrorShown() {
        state.update { it.copy(error = null) }
    }

    private suspend fun refresh(folder: Path?, selectPath: Path? = null) {
        state.update { it.copy(sourceFolder = folder?.toString().orEmpty(), isLoading = true) }
        when (val result = scanDocuments(folder)) {
            is Outcome.Success -> showDocuments(result.value, selectPath)
            is Outcome.Failure -> showScanFailure(result.error)
        }
    }

    private fun showDocuments(documents: List<Document>, selectPath: Path?) {
        val wantedSelection = selectPath ?: state.value.selectedDocument?.path
        state.update {
            it.copy(
                folderStatus = FolderStatus.READY,
                documents = documents,
                // O documento organizado pode ter saído desta pasta; nesse caso ele segue no preview.
                selectedDocument = documents.firstOrNull { document -> document.path == wantedSelection }
                    ?: selectPath?.let(::documentAt),
                isLoading = false,
            )
        }
    }

    private fun showScanFailure(error: FileOperationError) {
        // Pasta ausente tem aviso próprio no painel; os demais erros viram mensagem.
        val (status, message) = when (error) {
            FileOperationError.FolderNotSelected -> FolderStatus.NOT_SELECTED to null
            FileOperationError.FolderNotFound -> FolderStatus.MISSING to null
            else -> FolderStatus.READY to error.toUserMessage()
        }
        state.update {
            it.copy(folderStatus = status, documents = emptyList(), selectedDocument = null, isLoading = false, error = message)
        }
    }

    private fun documentAt(path: Path) = Document(path, sizeBytes = 0, lastModified = Instant.EPOCH)
}
