package com.bragadev.fiscal.presentation.home

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.DocumentSort
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.DocumentSorting
import com.bragadev.fiscal.domain.usecase.ChangeSourceFolderUseCase
import com.bragadev.fiscal.domain.usecase.LoadInitialFoldersUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.ScanDocumentsUseCase
import com.bragadev.fiscal.domain.usecase.UpdateSettingsUseCase
import com.bragadev.fiscal.domain.usecase.WatchFolderUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val updateSettings: UpdateSettingsUseCase,
    private val watchFolder: WatchFolderUseCase,
    private val documentChanges: DocumentChangeNotifier,
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()

    val currentSourceFolder: Path? get() = observeSettings().value.sourceFolder

    init {
        scope.launch {
            loadInitialFolders()
            observeSettings().map { it.sourceFolder }.distinctUntilChanged().collectLatest { folder ->
                refresh(folder)
                // Mudanças feitas fora do app (ex.: renomear no Explorer) atualizam a lista sozinhas.
                folder?.let { watchFolder(it).collect { refresh(folder) } }
            }
        }
        scope.launch {
            observeSettings().map { it.documentSort }.distinctUntilChanged().collect { sort ->
                updateList { it.copy(sort = sort) }
            }
        }
        scope.launch {
            documentChanges.changes.collect { change -> refresh(currentSourceFolder, change.selectPath) }
        }
    }

    fun onRefresh() {
        documentChanges.notifyChanged()
    }

    fun onSourceFolderSelected(path: Path) {
        scope.launch {
            val result = changeSourceFolder(path)
            if (result is Outcome.Failure) state.update { it.copy(error = result.error.toUserMessage()) }
        }
    }

    fun onSortChanged(sort: DocumentSort) {
        scope.launch { updateSettings { it.copy(documentSort = sort) } }
    }

    fun onQueryChanged(query: String) = updateList { it.copy(query = query) }

    fun onDocumentSelected(document: Document) {
        state.update { it.copy(selectedDocument = document) }
    }

    /** Mostra no preview um arquivo que não está na lista (ex.: um arquivo da pasta do mês). */
    fun onPreviewFile(path: Path) {
        val listed = state.value.documents.firstOrNull { it.path == path }
        onDocumentSelected(listed ?: documentAt(path))
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
        val current = state.value.selectedDocument
        val wanted = selectPath ?: current?.path
        // Um arquivo que saiu desta pasta (ex.: foi para o mês) continua no preview.
        val selection = documents.firstOrNull { it.path == wanted } ?: selectPath?.let(::documentAt) ?: current
        updateList {
            it.copy(folderStatus = FolderStatus.READY, documents = documents, selectedDocument = selection, isLoading = false)
        }
    }

    private fun showScanFailure(error: FileOperationError) {
        // Pasta ausente tem aviso próprio no painel; os demais erros viram mensagem.
        val (status, message) = when (error) {
            FileOperationError.FolderNotSelected -> FolderStatus.NOT_SELECTED to null
            FileOperationError.FolderNotFound -> FolderStatus.MISSING to null
            else -> FolderStatus.READY to error.toUserMessage()
        }
        updateList {
            it.copy(folderStatus = status, documents = emptyList(), selectedDocument = null, isLoading = false, error = message)
        }
    }

    /** Aplica a mudança e recalcula a lista visível (busca + ordenação). */
    private fun updateList(transform: (HomeUiState) -> HomeUiState) {
        state.update { old ->
            val new = transform(old)
            new.copy(visibleDocuments = DocumentSorting.apply(new.documents, new.sort, new.query))
        }
    }

    private fun documentAt(path: Path) = Document(path, sizeBytes = 0, lastModified = Instant.EPOCH)
}
