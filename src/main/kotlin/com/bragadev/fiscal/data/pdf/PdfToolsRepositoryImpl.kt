package com.bragadev.fiscal.data.pdf

import com.bragadev.fiscal.data.filesystem.FileErrorMapper
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.domain.model.MonthReview
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.PdfToolsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import java.awt.geom.AffineTransform
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Cria PDFs com o PDFBox. O resultado é gravado com CREATE_NEW: se o arquivo de destino
 * já existir, a gravação falha em vez de sobrescrever. Em caso de erro, nada é gravado.
 */
class PdfToolsRepositoryImpl(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PdfToolsRepository {

    override suspend fun imagesToPdf(pages: List<ImagePage>, target: Path): Outcome<Unit> = writing(target) {
        PDDocument().use { document ->
            pages.forEach { addImagePage(document, it) }
            save(document, target)
        }
    }

    override suspend fun mergePdfs(sources: List<Path>, target: Path): Outcome<Unit> = writing(target) {
        PDDocument().use { result ->
            val merger = PDFMergerUtility()
            sources.forEach { source ->
                // Cada original é lido para a memória: nunca fica bloqueado nem é alterado.
                Loader.loadPDF(Files.readAllBytes(source)).use { document -> merger.appendDocument(result, document) }
            }
            save(result, target)
        }
    }

    override suspend fun monthReport(review: MonthReview, target: Path): Outcome<Unit> = writing(target) {
        PDDocument().use { document ->
            MonthReportLayout(document).render(review)
            save(document, target)
        }
    }

    /**
     * Página A4 na orientação da imagem (já girada), com a imagem centralizada e ajustada às margens.
     *
     * Rotação final = a que o celular anotou no EXIF (o preview já mostra a foto em pé) + a do botão Girar.
     */
    private fun addImagePage(document: PDDocument, page: ImagePage) {
        // O original é lido para a memória: nunca fica bloqueado nem é alterado.
        val bytes = Files.readAllBytes(page.path)
        val image = PDImageXObject.createFromByteArray(document, bytes, page.path.fileName.toString())
        val rotation = (ExifOrientation.rotationDegrees(bytes) + page.rotationDegrees) % 360
        val quarterTurn = rotation % 180 != 0
        val shownWidth = if (quarterTurn) image.height.toFloat() else image.width.toFloat()
        val shownHeight = if (quarterTurn) image.width.toFloat() else image.height.toFloat()
        val pageBox = if (shownWidth > shownHeight) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4

        val availableWidth = pageBox.width - 2 * MARGIN
        val availableHeight = pageBox.height - 2 * MARGIN
        val scale = minOf(availableWidth / shownWidth, availableHeight / shownHeight)

        val pdfPage = PDPage(pageBox)
        document.addPage(pdfPage)
        PDPageContentStream(document, pdfPage).use { content ->
            val placement = AffineTransform().apply {
                translate(pageBox.width / 2.0, pageBox.height / 2.0)
                // No PDF o eixo Y sobe: girar no sentido horário é um ângulo negativo.
                rotate(-Math.toRadians(rotation.toDouble()))
                scale(image.width * scale.toDouble(), image.height * scale.toDouble())
                translate(-0.5, -0.5)
            }
            content.drawImage(image, Matrix(placement))
        }
    }

    /** Monta o PDF inteiro na memória e só então grava: um erro no meio nunca deixa arquivo incompleto. */
    private fun save(document: PDDocument, target: Path) {
        val bytes = ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    }

    private suspend fun writing(target: Path, block: () -> Unit): Outcome<Unit> = withContext(dispatcher) {
        try {
            block()
            Outcome.Success(Unit)
        } catch (error: FileSystemException) {
            Outcome.Failure(FileErrorMapper.map(error))
        } catch (_: IOException) {
            // Arquivo de entrada corrompido ou ilegível. Nada foi gravado, pois a gravação é o último passo.
            Outcome.Failure(FileOperationError.PdfCreationFailed)
        } catch (_: IllegalArgumentException) {
            // O PDFBox não reconhece o conteúdo como imagem (arquivo corrompido ou com extensão trocada).
            Outcome.Failure(FileOperationError.UnsupportedImage)
        }
    }

    private companion object {
        const val MARGIN = 24f
    }
}
