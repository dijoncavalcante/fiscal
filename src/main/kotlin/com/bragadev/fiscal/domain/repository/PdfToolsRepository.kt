package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.domain.model.Outcome
import java.nio.file.Path

/** Criação de PDFs novos. Os arquivos de entrada só são lidos; [target] nunca é sobrescrito. */
interface PdfToolsRepository {
    /** Um PDF com uma página A4 por imagem, na ordem recebida. */
    suspend fun imagesToPdf(pages: List<ImagePage>, target: Path): Outcome<Unit>

    /** Um PDF com todas as páginas dos PDFs de entrada, na ordem recebida. */
    suspend fun mergePdfs(sources: List<Path>, target: Path): Outcome<Unit>
}
