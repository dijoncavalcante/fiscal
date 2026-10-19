package com.bragadev.fiscal.presentation.preview

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.PageSize
import com.bragadev.fiscal.domain.usecase.PreviewPdfUseCase
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path

/**
 * Preview com rolagem contínua: todas as páginas ficam uma abaixo da outra e a tela informa quais estão
 * visíveis ([onVisiblePagesChanged]); só essas (e as vizinhas) são desenhadas.
 */
class PdfPreviewViewModel(
    private val previewPdf: PreviewPdfUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(PdfPreviewUiState())
    val uiState: StateFlow<PdfPreviewUiState> = state.asStateFlow()

    private var pageSizes: List<PageSize> = emptyList()
    private var viewport = Viewport()
    private var visible: IntRange = IntRange.EMPTY
    private var loadJob: Job? = null
    private var renderJob: Job? = null

    fun open(path: Path?) {
        if (path == state.value.path) return
        loadJob?.cancel()
        renderJob?.cancel()
        visible = IntRange.EMPTY
        if (path == null) return closeDocument()

        state.update { PdfPreviewUiState(path = path, fitMode = it.fitMode, zoom = it.zoom, isLoading = true) }
        loadJob = scope.launch {
            when (val result = previewPdf.open(path)) {
                is Outcome.Success -> {
                    pageSizes = result.value.pageSizes
                    state.update { it.copy(isLoading = false) }
                    // A tela informa as páginas visíveis assim que a lista aparece; até lá, desenha a primeira.
                    visible = 0..0
                    relayout()
                }
                is Outcome.Failure -> state.update { it.copy(isLoading = false, error = result.error.toUserMessage()) }
            }
        }
    }

    fun onViewportChanged(widthDp: Float, heightDp: Float, density: Float) {
        val updated = Viewport(widthDp, heightDp, density)
        if (updated == viewport) return
        viewport = updated
        relayout(debounce = true)
    }

    /** Páginas que estão aparecendo na tela agora (índices de 0); as vizinhas também são desenhadas. */
    fun onVisiblePagesChanged(range: IntRange) {
        if (range == visible) return
        visible = range
        renderVisible()
    }

    /** Página que ocupa mais espaço na tela (para "Página X de N" e o zoom mostrado). */
    fun onCurrentPageChanged(index: Int) {
        if (index in 0 until state.value.pageCount && index != state.value.pageIndex) {
            state.update { it.copy(pageIndex = index) }
        }
    }

    fun zoomIn() = setZoom(ZoomCalculator.zoomIn(state.value.currentZoom))

    fun zoomOut() = setZoom(ZoomCalculator.zoomOut(state.value.currentZoom))

    fun fitWidth() = setFitMode(FitMode.WIDTH)

    fun fitPage() = setFitMode(FitMode.PAGE)

    private fun setZoom(zoom: Float) {
        state.update { it.copy(zoom = zoom, fitMode = FitMode.NONE) }
        relayout()
    }

    private fun setFitMode(mode: FitMode) {
        state.update { it.copy(fitMode = mode) }
        relayout()
    }

    private fun closeDocument() {
        pageSizes = emptyList()
        state.update { PdfPreviewUiState(fitMode = it.fitMode, zoom = it.zoom) }
        scope.launch { previewPdf.close() }
    }

    /** Recalcula o tamanho de cada página (zoom/ajuste/janela) e redesenha as visíveis. */
    private fun relayout(debounce: Boolean = false) {
        if (!viewport.isKnown || pageSizes.isEmpty() || state.value.path == null) return
        state.update { current ->
            val pages = pageSizes.mapIndexed { index, size ->
                val zoom = zoomFor(current.fitMode, current.zoom, size)
                val old = current.pages.getOrNull(index)
                PreviewPage(
                    index = index,
                    widthDp = size.widthPoints * ZoomCalculator.POINTS_TO_DP * zoom,
                    heightDp = size.heightPoints * ZoomCalculator.POINTS_TO_DP * zoom,
                    zoom = zoom,
                    scale = ZoomCalculator.renderScale(zoom, viewport.density, size),
                    // A imagem antiga fica (esticada) até a nova ficar pronta: não pisca ao mudar o zoom.
                    image = old?.image,
                    renderedScale = old?.renderedScale,
                )
            }
            current.copy(pages = pages, error = null)
        }
        renderVisible(debounce)
    }

    /** Desenha, em ordem, as páginas visíveis e as vizinhas que ainda não estão prontas; libera as distantes. */
    private fun renderVisible(debounce: Boolean = false) {
        val path = state.value.path ?: return
        val pageCount = state.value.pageCount
        if (pageCount == 0 || visible.isEmpty()) return
        val wanted =
            (visible.first - NEIGHBORS).coerceAtLeast(0)..(visible.last + NEIGHBORS).coerceAtMost(pageCount - 1)
        val kept = (wanted.first - KEEP_DISTANCE).coerceAtLeast(0)..(wanted.last + KEEP_DISTANCE)
        state.update { current ->
            current.copy(pages = current.pages.map {
                if (it.index in kept) it else it.copy(
                    image = null,
                    renderedScale = null
                )
            })
        }
        // Visíveis primeiro, depois as vizinhas.
        val order = visible.filter { it in wanted } + wanted.filterNot { it in visible }
        renderJob?.cancel()
        renderJob = scope.launch {
            if (debounce) delay(RESIZE_DEBOUNCE_MS)
            for (index in order) {
                val page = state.value.pages.getOrNull(index) ?: continue
                if (!page.needsRender) continue
                when (val result = previewPdf.render(path, index, page.scale)) {
                    is Outcome.Success -> {
                        val bitmap = withContext(Dispatchers.Default) { result.value.image.toComposeImageBitmap() }
                        state.update { current ->
                            // Só aplica se nada mudou enquanto desenhava (zoom, documento).
                            if (current.path != path || current.pages.getOrNull(index)?.scale != page.scale) return@update current
                            current.copy(pages = current.pages.map {
                                if (it.index == index) it.copy(
                                    image = bitmap,
                                    renderedScale = page.scale
                                ) else it
                            })
                        }
                    }

                    is Outcome.Failure -> return@launch state.update { it.copy(error = result.error.toUserMessage()) }
                }
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

        /** Páginas desenhadas antes e depois das visíveis (a rolagem já encontra a próxima pronta). */
        const val NEIGHBORS = 1

        /** Além disso, as imagens são descartadas (um PDF de 50 páginas não fica todo na memória). */
        const val KEEP_DISTANCE = 2
    }
}
