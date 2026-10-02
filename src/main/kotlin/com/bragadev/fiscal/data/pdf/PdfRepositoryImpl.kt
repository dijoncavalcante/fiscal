package com.bragadev.fiscal.data.pdf

import com.bragadev.fiscal.data.filesystem.FileErrorMapper
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.PageSize
import com.bragadev.fiscal.domain.model.PdfInfo
import com.bragadev.fiscal.domain.model.RenderedPage
import com.bragadev.fiscal.domain.model.flatMap
import com.bragadev.fiscal.domain.model.map
import com.bragadev.fiscal.domain.repository.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Leitura de PDFs com Apache PDFBox.
 *
 * O arquivo é lido inteiro para a memória e o PDFBox trabalha sobre os bytes:
 * assim o arquivo original nunca fica bloqueado nem é alterado, e pode ser
 * movido ou renomeado enquanto o preview está aberto.
 */
class PdfRepositoryImpl(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PdfRepository {
    private val mutex = Mutex()
    private var openPath: Path? = null
    private var openDocument: PDDocument? = null

    override suspend fun open(path: Path): Outcome<PdfInfo> = locked {
        // Sempre relê o arquivo: outro arquivo pode ter ocupado o mesmo caminho.
        closeCurrent()
        documentFor(path).map { document -> PdfInfo(document.pages.map(::pageSizeOf)) }
    }

    override suspend fun renderPage(path: Path, pageIndex: Int, scale: Float): Outcome<RenderedPage> = locked {
        documentFor(path).flatMap { document -> render(document, pageIndex, scale) }
    }

    override suspend fun close() = locked { closeCurrent() }

    private fun render(document: PDDocument, pageIndex: Int, scale: Float): Outcome<RenderedPage> {
        if (pageIndex !in 0 until document.numberOfPages) return Outcome.Failure(FileOperationError.ReadError)
        return try {
            val image = PDFRenderer(document).renderImage(pageIndex, scale, ImageType.RGB)
            Outcome.Success(RenderedPage(pageIndex, image))
        } catch (_: IOException) {
            Outcome.Failure(FileOperationError.InvalidPdf)
        }
    }

    private fun documentFor(path: Path): Outcome<PDDocument> {
        val current = openDocument
        if (current != null && openPath == path) return Outcome.Success(current)
        closeCurrent()
        return try {
            val document = Loader.loadPDF(Files.readAllBytes(path))
            openDocument = document
            openPath = path
            Outcome.Success(document)
        } catch (error: FileSystemException) {
            Outcome.Failure(FileErrorMapper.map(error))
        } catch (_: IOException) {
            // Arquivo corrompido, protegido por senha ou que não é um PDF válido.
            Outcome.Failure(FileOperationError.InvalidPdf)
        }
    }

    private fun closeCurrent() {
        runCatching { openDocument?.close() }
        openDocument = null
        openPath = null
    }

    private fun pageSizeOf(page: PDPage): PageSize {
        val box = page.cropBox
        val rotated = page.rotation % 180 != 0
        return if (rotated) PageSize(box.height, box.width) else PageSize(box.width, box.height)
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(dispatcher) { mutex.withLock { block() } }
}
