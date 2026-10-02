package com.bragadev.fiscal.presentation.preview

import androidx.compose.ui.graphics.ImageBitmap
import java.nio.file.Path

data class PdfPreviewUiState(
    val path: Path? = null,
    val pageCount: Int = 0,
    val pageIndex: Int = 0,
    val zoom: Float = 1f,
    val fitMode: FitMode = FitMode.PAGE,
    val page: ImageBitmap? = null,
    val pageWidthDp: Float = 0f,
    val pageHeightDp: Float = 0f,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val hasDocument: Boolean get() = path != null && pageCount > 0
    val pageNumber: Int get() = pageIndex + 1
    val canGoPrevious: Boolean get() = pageIndex > 0
    val canGoNext: Boolean get() = pageIndex < pageCount - 1
}

enum class FitMode { NONE, WIDTH, PAGE }
