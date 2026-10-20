package com.bragadev.fiscal.data.pdf

import com.bragadev.fiscal.data.filesystem.FileErrorMapper
import com.bragadev.fiscal.data.logging.AppLog
import com.bragadev.fiscal.data.ocr.OcrEngine
import com.bragadev.fiscal.data.ocr.TesseractOcrEngine
import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.DocumentContentReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Lê o texto e as assinaturas de um PDF com PDFBox; páginas que são só foto passam pelo OCR.
 * O arquivo é lido para a memória e nunca é alterado nem fica bloqueado.
 */
class PdfContentReaderImpl(
    private val ocr: OcrEngine = TesseractOcrEngine(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DocumentContentReader {
    // Um PDF por vez: o OCR usa bastante processador e memória.
    private val mutex = Mutex()

    override suspend fun read(file: Path, isEnough: (DocumentContent) -> Boolean): Outcome<DocumentContent> =
        withContext(dispatcher) { mutex.withLock { readLocked(file, isEnough) } }

    private fun readLocked(file: Path, isEnough: (DocumentContent) -> Boolean): Outcome<DocumentContent> {
        val bytes = try {
            Files.readAllBytes(file)
        } catch (error: FileSystemException) {
            return Outcome.Failure(FileErrorMapper.map(error))
        } catch (_: IOException) {
            return Outcome.Failure(FileOperationError.ReadError)
        }
        val document = try {
            Loader.loadPDF(bytes)
        } catch (_: IOException) {
            // Corrompido, protegido por senha ou não é um PDF.
            return Outcome.Failure(FileOperationError.InvalidPdf)
        }
        return document.use {
            try {
                Outcome.Success(readContent(it, isEnough))
            } catch (error: Exception) {
                AppLog.warn("Não foi possível conferir ${file.fileName}", error)
                Outcome.Failure(FileOperationError.Unknown(error))
            } catch (error: LinkageError) {
                // DLL do OCR não carregou: a conferência fica para depois, sem pendência falsa.
                AppLog.error("OCR indisponível", error)
                Outcome.Failure(FileOperationError.Unknown(error))
            }
        }
    }

    private fun readContent(document: PDDocument, isEnough: (DocumentContent) -> Boolean): DocumentContent {
        val texts = (0 until document.numberOfPages).map { textOf(document, it) }.toMutableList()
        val signers = signersOf(document)
        val renderer = PDFRenderer(document)
        val ocrDone = BooleanArray(texts.size)
        fun ocrPage(index: Int) {
            texts[index] = texts[index] + "\n" + ocr.read(render(renderer, document.getPage(index), index))
            ocrDone[index] = true
        }
        // Página sem texto = foto ou digitalização (ex.: cupom fotografado).
        texts.indices.filter { texts[it].trim().length < MIN_TEXT_CHARS }.forEach(::ocrPage)
        // Páginas com texto e imagem (ex.: comprovante com a foto do cupom ao lado): só enquanto faltar algo.
        for (index in texts.indices) {
            if (isEnough(DocumentContent(texts.toList(), signers))) break
            if (!ocrDone[index] && hasImages(document.getPage(index))) ocrPage(index)
        }
        return DocumentContent(texts.toList(), signers, ocrPages = texts.indices.filter { ocrDone[it] }.toSet())
    }

    private fun textOf(document: PDDocument, pageIndex: Int): String = try {
        PDFTextStripper().apply {
            startPage = pageIndex + 1
            endPage = pageIndex + 1
        }.getText(document)
    } catch (_: IOException) {
        ""
    }

    private fun render(renderer: PDFRenderer, page: PDPage, pageIndex: Int): BufferedImage {
        val box = page.cropBox
        // Páginas enormes (foto no tamanho original) são reduzidas: o OCR não precisa de mais que isso.
        val scale =
            minOf(TesseractOcrEngine.OCR_DPI / POINTS_PER_INCH, MAX_RENDER_PIXELS / maxOf(box.width, box.height))
        return renderer.renderImage(pageIndex, scale, ImageType.GRAY)
    }

    private fun hasImages(page: PDPage): Boolean {
        val resources = page.resources ?: return false
        return resources.xObjectNames.any { name ->
            runCatching { resources.getXObject(name) is PDImageXObject }.getOrDefault(
                false
            )
        }
    }

    /** Quem assinou: o assunto de cada certificado da assinatura (ex.: "CN=ELVES LINCOLN MENEZES DE LIMA:000..."). */
    private fun signersOf(document: PDDocument): List<String> = document.signatureDictionaries.flatMap { signature ->
        listOfNotNull(signature.name) + certificateSubjects(signature)
    }

    private fun certificateSubjects(signature: PDSignature): List<String> = runCatching {
        val pkcs7 = trimDer(signature.contents)
        CertificateFactory.getInstance("X.509").generateCertificates(ByteArrayInputStream(pkcs7))
            .filterIsInstance<X509Certificate>()
            .map { it.subjectX500Principal.name }
    }.getOrDefault(emptyList())

    /**
     * O campo da assinatura é maior que ela e vem completado com zeros: corta no tamanho da estrutura DER.
     * Tamanho "indefinido" (0x80, comum nas assinaturas gov.br) já tem marca de fim: vai inteiro.
     */
    private fun trimDer(bytes: ByteArray): ByteArray {
        if (bytes.size < 2) return bytes
        val first = bytes[1].toInt() and 0xFF
        if (first == INDEFINITE_LENGTH) return bytes
        if (first < 0x80) return bytes.copyOf(minOf(bytes.size, 2 + first))
        val lengthBytes = first and 0x7F
        if (bytes.size < 2 + lengthBytes) return bytes
        var length = 0L
        for (i in 0 until lengthBytes) length = (length shl 8) or (bytes[2 + i].toLong() and 0xFF)
        val total = 2 + lengthBytes + length
        return if (total in 1..bytes.size.toLong()) bytes.copyOf(total.toInt()) else bytes
    }

    private companion object {
        const val MIN_TEXT_CHARS = 20
        const val POINTS_PER_INCH = 72f
        const val MAX_RENDER_PIXELS = 3500f
        const val INDEFINITE_LENGTH = 0x80
    }
}
