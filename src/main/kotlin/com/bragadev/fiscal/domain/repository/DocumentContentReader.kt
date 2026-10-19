package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.Outcome
import java.nio.file.Path

/** Lê o texto e as assinaturas digitais de um PDF, sem alterar o arquivo. */
interface DocumentContentReader {
    /**
     * Páginas sem texto (fotos, digitalizações) passam pelo OCR. Depois disso, as páginas com texto que também
     * têm imagens só passam pelo OCR enquanto [isEnough] disser que ainda falta algo — o OCR é lento.
     *
     * Falha com [com.bragadev.fiscal.domain.model.FileOperationError.InvalidPdf] se o PDF estiver corrompido ou
     * protegido por senha; outras falhas (arquivo em uso, OCR indisponível) são passageiras.
     */
    suspend fun read(file: Path, isEnough: (DocumentContent) -> Boolean = { false }): Outcome<DocumentContent>
}
