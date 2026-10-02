package com.bragadev.fiscal.presentation.home

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.usecase.ChangeRootFolderUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.ResolveRootFolderUseCase
import com.bragadev.fiscal.domain.usecase.ScanDocumentsUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.common.Strings
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

class HomeViewModel(
    private val resolveRootFolder: ResolveRootFolderUseCase,
    private val changeRootFolder: ChangeRootFolderUseCase,
    private val scanDocuments: ScanDocumentsUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val documentChanges: DocumentChangeNotifier,
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()

    private val currentRoot: Path? get() = observeSettings().value.rootPath

    init {
        scope.launch {
            resolveRootFolder()
            observeSettings().map { it.rootPath }.distinctUntilChanged().collect { refresh(it) }
        }
        scope.launch {
            documentChanges.changes.collect { change -> refresh(currentRoot, change.selectPath) }
        }
    }

    fun onRefresh() {
        scope.launch { refresh(currentRoot) }
    }

    fun onRootSelected(path: Path) {
        scope.launch {
            val result = changeRootFolder(path)
            if (result is Outcome.Failure) state.update { it.copy(error = result.error.toUserMessage()) }
        }
    }

    fun onDocumentSelected(document: Document) {
        state.update { it.copy(selectedDocument = document) }
    }

    fun onErrorShown() {
        state.update { it.copy(error = null) }
    }

    private suspend fun refresh(root: Path?, selectPath: Path? = null) {
        state.update { it.copy(rootPath = root?.toString().orEmpty(), isLoading = true) }
        if (root == null) return showScanFailure(FileOperationError.RootNotConfigured)
        when (val result = scanDocuments(root)) {
            is Outcome.Success -> showDocuments(root, result.value, selectPath)
            is Outcome.Failure -> showScanFailure(result.error)
        }
    }

    private fun showDocuments(root: Path, documents: List<Document>, selectPath: Path?) {
        val wantedSelection = selectPath ?: state.value.selectedDocument?.path
        state.update {
            it.copy(
                rootStatus = RootStatus.READY,
                documents = documents.map { document -> DocumentListItem(document, folderLabel(root, document)) },
                selectedDocument = documents.firstOrNull { document -> document.path == wantedSelection },
                isLoading = false,
            )
        }
    }

    private fun showScanFailure(error: FileOperationError) {
        // Pasta raiz ausente tem tela própria; os demais erros viram mensagem.
        val (status, message) = when (error) {
            FileOperationError.RootNotConfigured -> RootStatus.NOT_CONFIGURED to null
            FileOperationError.RootNotFound -> RootStatus.MISSING to null
            else -> RootStatus.READY to error.toUserMessage()
        }
        state.update {
            it.copy(rootStatus = status, documents = emptyList(), selectedDocument = null, isLoading = false, error = message)
        }
    }

    private fun folderLabel(root: Path, document: Document): String {
        val relative = document.path.parent?.let { root.relativize(it).toString() }.orEmpty()
        return relative.ifBlank { Strings.ROOT_FOLDER_LABEL }
    }
}
