package com.bragadev.fiscal.domain.model

import java.awt.image.BufferedImage

/** Tamanho de uma página em pontos (1/72 de polegada), já considerando a rotação. */
data class PageSize(val widthPoints: Float, val heightPoints: Float)

data class PdfInfo(val pageSizes: List<PageSize>) {
    val pageCount: Int get() = pageSizes.size
}

data class RenderedPage(val pageIndex: Int, val image: BufferedImage)
