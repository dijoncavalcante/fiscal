package com.bragadev.fiscal.presentation.home

import com.bragadev.fiscal.domain.model.Document

data class HomeUiState(
    val sourceFolder: String = "",
    val folderStatus: FolderStatus = FolderStatus.LOADING,
    val documents: List<Document> = emptyList(),
    val selectedDocument: Document? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

enum class FolderStatus { LOADING, READY, MISSING, NOT_SELECTED }
