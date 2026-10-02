package com.bragadev.fiscal.presentation.preview

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.PageSize
import com.bragadev.fiscal.domain.usecase.PreviewPdfUseCase
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

class PdfPreviewViewModel(
    private val previewPdf: PreviewPdfUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(PdfPreviewUiState())
    val uiState: StateFlow<PdfPreviewUiState> = state.asStateFlow()

    private var pageSizes: List<PageSize> = emptyList()
    private var viewport = Viewport()
    private var loadJob: Job? = null
    private var renderJob: Job? = null

    fun open(path: Path?) {
        if (path == state.value.path) return
        loadJob?.cancel()
        renderJob?.cancel()
        if (path == null) return closeDocument()

        state.update { PdfPreviewUiState(path = path, fitMode = it.fitMode, zoom = it.zoom, isLoading = true) }
        loadJob = scope.launch {
            when (val result = previewPdf.open(path)) {
                is Outcome.Success -> {
                    pageSizes = result.value.pageSizes
                    state.update { it.copy(pageCount = pageSizes.size, pageIndex = 0) }
                    render()
                }
                is Outcome.Failure -> state.update { it.copy(isLoading = false, error = result.error.toUserMessage()) }
            }
        }
    }

    fun onViewportChanged(widthDp: Float, heightDp: Float, density: Float) {
        val updated = Viewport(widthDp, heightDp, density)
        if (updated == viewport) return
        viewport = updated
        if (state.value.hasDocument) render(debounce = true)
    }

    fun nextPage() = goToPage(state.value.pageIndex + 1)

    fun previousPage() = goToPage(state.value.pageIndex - 1)

    fun zoomIn() = setZoom(ZoomCalculator.zoomIn(state.value.zoom))

    fun zoomOut() = setZoom(ZoomCalculator.zoomOut(state.value.zoom))

    fun fitWidth() = setFitMode(FitMode.WIDTH)

    fun fitPage() = setFitMode(FitMode.PAGE)

    private fun goToPage(index: Int) {
        if (index !in 0 until state.value.pageCount) return
        state.update { it.copy(pageIndex = index) }
        render()
    }

    private fun setZoom(zoom: Float) {
        state.update { it.copy(zoom = zoom, fitMode = FitMode.NONE) }
        render()
    }

    private fun setFitMode(mode: FitMode) {
        state.update { it.copy(fitMode = mode) }
        render()
    }

    private fun closeDocument() {
        pageSizes = emptyList()
        state.update { PdfPreviewUiState(fitMode = it.fitMode, zoom = it.zoom) }
        scope.launch { previewPdf.close() }
    }

    private fun render(debounce: Boolean = false) {
        val current = state.value
        val path = current.path ?: return
        val pageSize = pageSizes.getOrNull(current.pageIndex) ?: return
        if (!viewport.isKnown) return

        val zoom = zoomFor(current.fitMode, current.zoom, pageSize)
        state.update {
            it.copy(
                zoom = zoom,
                pageWidthDp = pageSize.widthPoints * ZoomCalculator.POINTS_TO_DP * zoom,
                pageHeightDp = pageSize.heightPoints * ZoomCalculator.POINTS_TO_DP * zoom,
                isLoading = true,
                error = null,
            )
        }
        renderJob?.cancel()
        renderJob = scope.launch {
            if (debounce) delay(RESIZE_DEBOUNCE_MS)
            val scale = ZoomCalculator.renderScale(zoom, viewport.density, pageSize)
            when (val result = previewPdf.render(path, current.pageIndex, scale)) {
                is Outcome.Success -> {
                    val bitmap = withContext(Dispatchers.Default) { result.value.image.toComposeImageBitmap() }
                    state.update { it.copy(page = bitmap, isLoading = false) }
                }
                is Outcome.Failure -> state.update { it.copy(isLoading = false, error = result.error.toUserMessage()) }
            }
        }
    }

    private fun zoomFor(mode: FitMode, currentZoom: Float, page: PageSize): Float = when (mode) {
        FitMode.NONE -> currentZoom
        FitMode.WIDTH -> ZoomCalculator.fitWidth(page, viewport.widthDp)
        FitMode.PAGE -> ZoomCalculator.fitPage(page, viewport.widthDp, viewport.heightDp)
    }

    private data class Viewport(val widthDp: Float = 0f, val heightDp: Float = 0f, val density: Float = 1f) {
        val isKnown: Boolean get() = widthDp > 0f && heightDp > 0f
    }

    private companion object {
        const val RESIZE_DEBOUNCE_MS = 150L
    }
}
