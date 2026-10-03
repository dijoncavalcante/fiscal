package com.bragadev.fiscal.presentation.home

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.DocumentSort

data class HomeUiState(
    val sourceFolder: String = "",
    val folderStatus: FolderStatus = FolderStatus.LOADING,
    /** Todos os PDFs da pasta de origem. */
    val documents: List<Document> = emptyList(),
    /** O que aparece na lista: [documents] filtrados pela busca e ordenados. */
    val visibleDocuments: List<Document> = emptyList(),
    val sort: DocumentSort = DocumentSort.MODIFIED_NEWEST_FIRST,
    val query: String = "",
    val selectedDocument: Document? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

enum class FolderStatus { LOADING, READY, MISSING, NOT_SELECTED }
