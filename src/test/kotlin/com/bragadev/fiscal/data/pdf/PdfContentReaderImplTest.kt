package com.bragadev.fiscal.data.pdf

import com.bragadev.fiscal.data.ocr.OcrEngine
import com.bragadev.fiscal.data.ocr.TesseractOcrEngine
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.ExpenseDocumentRules
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Leitura de despesas: texto do PDF, OCR das páginas que são foto e arquivo que não abre. */
class PdfContentReaderImplTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `cupom fotografado e lido pelo OCR de verdade e o comprovante pelo texto do PDF`() = runTest {
        val pdf = expensePdf(
            textPage = "Comprovante de transacao Pix - Transferencia",
            photoLines = listOf("CUPOM FISCAL", "CNPJ 46.182.254/0001-79", "TOTAL R$ 125,00"),
        )

        val content = (PdfContentReaderImpl(TesseractOcrEngine()).read(pdf) as Outcome.Success).value

        assertTrue("CUPOM" in content.pageTexts[1].uppercase(), "OCR leu: ${content.pageTexts[1]}")
        // Sem assinatura digital: só ela falta.
        assertEquals(setOf(ExpenseRequirement.SIGNATURE), ExpenseDocumentRules.check(content).missing)
    }

    @Test
    fun `pagina com texto so passa pelo OCR enquanto faltar algo`() = runTest {
        // Página 1: comprovante em texto com uma imagem ao lado; página 2: só a foto do cupom.
        val pdf = expensePdf(
            textPage = "Comprovante de transacao Pix",
            photoLines = listOf("CUPOM FISCAL"),
            imageOnTextPage = true
        )
        val ocr = CountingOcr("CUPOM FISCAL")

        val content =
            (PdfContentReaderImpl(ocr).read(pdf, ExpenseDocumentRules::hasEnoughText) as Outcome.Success).value

        // Só a página sem texto: com ela o PDF já tem cupom e comprovante.
        assertEquals(1, ocr.calls)
        assertTrue(ExpenseDocumentRules.hasEnoughText(content))
    }

    @Test
    fun `sem o que procurar, as paginas com imagem tambem passam pelo OCR`() = runTest {
        val pdf = expensePdf(textPage = "Orcamento 007-2026", photoLines = listOf("foto"), imageOnTextPage = true)
        val ocr = CountingOcr("nada aqui")

        PdfContentReaderImpl(ocr).read(pdf, ExpenseDocumentRules::hasEnoughText)

        assertEquals(2, ocr.calls)
    }

    @Test
    fun `arquivo que nao e PDF falha como PDF invalido`() = runTest {
        val file =
            temp.newFile("3. Despesa - quebrado.pdf").toPath().also { Files.writeString(it, "%PDF-1.4 quebrado") }

        val result = PdfContentReaderImpl(CountingOcr("")).read(file)

        assertIs<Outcome.Failure>(result)
        assertEquals(FileOperationError.InvalidPdf, result.error)
    }

    @Test
    fun `falha do OCR nao vira resultado`() = runTest {
        val pdf = expensePdf(textPage = "Comprovante Pix", photoLines = listOf("CUPOM FISCAL"))
        val broken = OcrEngine { throw IllegalStateException("DLL do Tesseract não carregou") }

        assertIs<Outcome.Failure>(PdfContentReaderImpl(broken).read(pdf))
    }

    private class CountingOcr(private val text: String) : OcrEngine {
        var calls = 0

        override fun read(image: BufferedImage): String {
            calls++
            return text
        }
    }

    /** PDF de despesa: uma página com texto (o comprovante) e uma página que é só a foto do cupom. */
    private fun expensePdf(textPage: String, photoLines: List<String>, imageOnTextPage: Boolean = false): Path {
        val file = temp.root.toPath().resolve("3. Despesa - teste.pdf")
        PDDocument().use { document ->
            val photo = LosslessFactory.createFromImage(document, photoOf(photoLines))
            PDPage(PDRectangle.A4).also(document::addPage).let { page ->
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 14f)
                    stream.newLineAtOffset(50f, 750f)
                    stream.showText(textPage)
                    stream.endText()
                    if (imageOnTextPage) stream.drawImage(photo, 50f, 400f, 200f, 100f)
                }
            }
            PDPage(PDRectangle.A4).also(document::addPage).let { page ->
                PDPageContentStream(document, page).use { stream ->
                    stream.drawImage(photo, 0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height)
                }
            }
            document.save(file.toFile())
        }
        return file
    }

    /** "Foto" do cupom: texto preto em fundo branco, sem nada que o PDFBox consiga extrair como texto. */
    private fun photoOf(lines: List<String>): BufferedImage {
        val image = BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            color = Color.WHITE
            fillRect(0, 0, image.width, image.height)
            color = Color.BLACK
            font = Font(Font.MONOSPACED, Font.BOLD, 56)
            lines.forEachIndexed { i, line -> drawString(line, 80, 200 + i * 90) }
            dispose()
        }
        return image
    }
}
