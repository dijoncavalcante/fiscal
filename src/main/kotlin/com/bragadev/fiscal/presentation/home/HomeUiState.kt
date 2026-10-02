package com.bragadev.fiscal.presentation.home

import com.bragadev.fiscal.domain.model.Document

data class HomeUiState(
    val rootPath: String = "",
    val rootStatus: RootStatus = RootStatus.LOADING,
    val documents: List<DocumentListItem> = emptyList(),
    val selectedDocument: Document? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

enum class RootStatus { LOADING, READY, MISSING, NOT_CONFIGURED }

/** Documento como exibido na lista: nome e a pasta relativa à raiz. */
data class DocumentListItem(
    val document: Document,
    val folderLabel: String,
)
