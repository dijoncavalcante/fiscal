package com.bragadev.fiscal.presentation.preview

import androidx.compose.ui.graphics.ImageBitmap
import java.nio.file.Path

/**
 * Preview com todas as páginas uma abaixo da outra (rolagem contínua).
 *
 * @param pageIndex página que ocupa mais espaço na tela agora (atualizada pela rolagem).
 * @param zoom zoom escolhido pelo usuário (vale com [FitMode.NONE]); nos modos de ajuste cada página tem o seu.
 */
data class PdfPreviewUiState(
    val path: Path? = null,
    val pages: List<PreviewPage> = emptyList(),
    val pageIndex: Int = 0,
    val zoom: Float = 1f,
    val fitMode: FitMode = FitMode.PAGE,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val pageCount: Int get() = pages.size
    val hasDocument: Boolean get() = path != null && pageCount > 0
    val pageNumber: Int get() = pageIndex + 1
    val canGoPrevious: Boolean get() = pageIndex > 0
    val canGoNext: Boolean get() = pageIndex < pageCount - 1

    /** Zoom mostrado na barra: o da página atual. */
    val currentZoom: Float get() = pages.getOrNull(pageIndex)?.zoom ?: zoom

    /** Largura da página mais larga, para a rolagem horizontal com zoom alto. */
    val maxPageWidthDp: Float get() = pages.maxOfOrNull { it.widthDp } ?: 0f
}

/**
 * Uma página no preview. O tamanho já é conhecido antes de desenhar (a rolagem não "pula"); a imagem só é
 * gerada quando a página aparece na tela (ou está perto) e é descartada quando fica longe, para poupar memória.
 *
 * @param image imagem desenhada; pode ser de um zoom anterior ([renderedScale] diferente de [scale]) enquanto
 * a nova não fica pronta.
 */
data class PreviewPage(
    val index: Int,
    val widthDp: Float,
    val heightDp: Float,
    val zoom: Float,
    val scale: Float,
    val image: ImageBitmap? = null,
    val renderedScale: Float? = null,
) {
    val needsRender: Boolean get() = image == null || renderedScale != scale
}

enum class FitMode { NONE, WIDTH, PAGE }
