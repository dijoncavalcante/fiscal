package com.bragadev.fiscal.presentation.preview

import com.bragadev.fiscal.domain.model.PageSize

/**
 * Cálculos de zoom do preview.
 *
 * Zoom 1.0 (100%) mostra a página no tamanho real: 1 ponto do PDF (1/72") = 96/72 dp.
 */
object ZoomCalculator {
    const val POINTS_TO_DP = 96f / 72f
    private const val MIN_ZOOM = 0.1f
    private const val MAX_ZOOM = 5f
    private const val VIEWPORT_MARGIN_DP = 24f
    private val zoomSteps = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 5f)

    fun zoomIn(current: Float): Float = zoomSteps.firstOrNull { it > current + EPSILON } ?: MAX_ZOOM

    fun zoomOut(current: Float): Float = zoomSteps.lastOrNull { it < current - EPSILON } ?: MIN_ZOOM

    fun fitWidth(page: PageSize, viewportWidthDp: Float): Float =
        clamp((viewportWidthDp - VIEWPORT_MARGIN_DP) / (page.widthPoints * POINTS_TO_DP))

    fun fitPage(page: PageSize, viewportWidthDp: Float, viewportHeightDp: Float): Float =
        minOf(fitWidth(page, viewportWidthDp), clamp((viewportHeightDp - VIEWPORT_MARGIN_DP) / (page.heightPoints * POINTS_TO_DP)))

    /** Escala de renderização em pixels, limitada para não criar imagens enormes em zoom alto. */
    fun renderScale(zoom: Float, density: Float, page: PageSize): Float {
        val desired = zoom * POINTS_TO_DP * density
        val largestSide = maxOf(page.widthPoints, page.heightPoints)
        return minOf(desired, MAX_RENDER_PIXELS / largestSide)
    }

    private fun clamp(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    private const val EPSILON = 0.001f
    private const val MAX_RENDER_PIXELS = 4000f
}
