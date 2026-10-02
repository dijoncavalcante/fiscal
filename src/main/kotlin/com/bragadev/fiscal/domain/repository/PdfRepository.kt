package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.PdfInfo
import com.bragadev.fiscal.domain.model.RenderedPage
import java.nio.file.Path

interface PdfRepository {
    /** Abre o PDF somente para leitura. O arquivo original nunca é alterado. */
    suspend fun open(path: Path): Outcome<PdfInfo>

    suspend fun renderPage(path: Path, pageIndex: Int, scale: Float): Outcome<RenderedPage>

    /** Libera o documento aberto. */
    suspend fun close()
}
