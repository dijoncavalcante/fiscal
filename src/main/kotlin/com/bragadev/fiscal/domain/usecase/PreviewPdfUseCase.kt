package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.PdfInfo
import com.bragadev.fiscal.domain.model.RenderedPage
import com.bragadev.fiscal.domain.repository.PdfRepository
import java.nio.file.Path

class PreviewPdfUseCase(private val pdfRepository: PdfRepository) {
    suspend fun open(path: Path): Outcome<PdfInfo> = pdfRepository.open(path)

    suspend fun render(path: Path, pageIndex: Int, scale: Float): Outcome<RenderedPage> =
        pdfRepository.renderPage(path, pageIndex, scale)

    suspend fun close() = pdfRepository.close()
}
