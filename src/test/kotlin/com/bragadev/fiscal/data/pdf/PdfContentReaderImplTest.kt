package com.bragadev.fiscal.data.pdf

import com.bragadev.fiscal.data.ocr.OcrEngine
import com.bragadev.fiscal.data.ocr.OcrMode
import com.bragadev.fiscal.data.ocr.TesseractOcrEngine
import com.bragadev.fiscal.domain.model.*
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

    private val requirements = ExpenseRequirements(signerName = null)

    private fun enough(content: DocumentContent) = ExpenseDocumentRules.hasEnoughText(content, requirements)

    @Test
    fun `cupom fotografado e lido pelo OCR de verdade e o comprovante pelo texto do PDF`() = runTest {
        val pdf = expensePdf(
            textPage = "Comprovante de transacao Pix - Transferencia",
            photoLines = listOf("CUPOM FISCAL", "CNPJ 46.182.254/0001-79", "TOTAL R$ 125,00"),
        )

        val content = (PdfContentReaderImpl(TesseractOcrEngine()).read(pdf) as Outcome.Success).value

        assertTrue("CUPOM" in content.pageTexts[1].uppercase(), "OCR leu: ${content.pageTexts[1]}")
        // Comprovante só com texto (sem foto) é texto de verdade.
        assertEquals(setOf(1), content.ocrPages)
        // Sem assinatura digital: só ela falta.
        assertEquals(setOf(ExpenseRequirement.SIGNATURE), ExpenseDocumentRules.check(content, requirements).missing)
    }

    @Test
    fun `NFC-e fotografada com o total numa tabela e lida pela posicao com o OCR de verdade`() = runTest {
        // Como na NFC-e real: título "TOTAL (R$)" em cima, valor embaixo, com o "1" da coluna Itens no caminho.
        val pdf = expensePdf(
            textPage = "Comprovante de transacao Pix Valor: 125.00",
            photoLines = listOf(
                "DOCUMENTO AUXILIAR DA NOTA FISCAL",
                "ITENS SUBTOTAL  TOTAL (R$)",
                "1     125,00    125,00"
            ),
        )

        val content = (PdfContentReaderImpl(TesseractOcrEngine()).read(pdf, ::enough)
                as Outcome.Success).value
        val result = ExpenseDocumentRules.check(content, requirements)

        assertEquals(null, result.amountProblem, "OCR leu: ${content.pageTexts[1]}")
        assertTrue(result.amountEvidence!!.startsWith("Valor conferido: R$ 125,00 abaixo de"), result.amountEvidence)
    }

    @Test
    fun `pagina com texto so passa pelo OCR enquanto faltar algo`() = runTest {
        // Página 1: comprovante em texto com uma imagem ao lado; página 2: só a foto do cupom.
        val pdf = expensePdf(
            textPage = "Comprovante de transacao Pix Valor: 125.00",
            photoLines = listOf("CUPOM FISCAL"),
            imageOnTextPage = true
        )
        val ocr = CountingOcr("CUPOM FISCAL TOTAL R$ 125,00")

        val content =
            (PdfContentReaderImpl(ocr).read(pdf, ::enough) as Outcome.Success).value

        // Página com texto e uma foto grande (como as digitalizações com texto embutido): conta como foto,
        // porque esse texto também veio de um OCR.
        assertEquals(setOf(0, 1), content.ocrPages)
        // Só a página sem texto: com ela o PDF já tem cupom, comprovante e o valor bate.
        assertEquals(1, ocr.calls)
        assertEquals(0, ocr.wordCalls)
        assertTrue(enough(content))
    }

    @Test
    fun `total que o texto nao achou e procurado em novas leituras, parando na primeira que resolve`() = runTest {
        val pdf = expensePdf(textPage = "Comprovante de transacao Pix Valor: 125.00", photoLines = listOf("foto"))
        val table = listOf(
            PageWord("TOTAL", 600f, 500f, 100f, 30f, 95f), PageWord("(R$)", 705f, 500f, 60f, 30f, 95f),
            PageWord("1", 100f, 545f, 15f, 30f, 95f), PageWord("125,00", 620f, 545f, 120f, 30f, 93f),
        )
        val ocr = CountingOcr("Documento Auxiliar da Nota Fiscal", words = table)

        val content =
            (PdfContentReaderImpl(ocr).read(pdf, ::enough) as Outcome.Success).value

        assertEquals(1, ocr.wordCalls)
        assertEquals(listOf(OcrMode.AUTO), ocr.modes)
        assertEquals(null, ExpenseDocumentRules.check(content, requirements).amountProblem)
    }

    @Test
    fun `sem resolver, cada foto tem no maximo tres leituras extras`() = runTest {
        val pdf = expensePdf(textPage = "Comprovante de transacao Pix Valor: 125.00", photoLines = listOf("foto"))
        val ocr = CountingOcr("Documento Auxiliar da Nota Fiscal")

        val content =
            (PdfContentReaderImpl(ocr).read(pdf, ::enough) as Outcome.Success).value

        assertEquals(listOf(OcrMode.AUTO, OcrMode.SPARSE, OcrMode.SINGLE_COLUMN), ocr.modes)
        assertEquals(listOf(200, 300, 300), ocr.dpis)
        // Página de texto: palavras do próprio PDF, com posição; foto: uma leitura por tentativa.
        assertTrue(content.layouts.any { layout -> !layout.fromOcr && layout.words.any { it.text == "Comprovante" } })
        assertEquals(3, content.layouts.count { it.fromOcr })
    }

    @Test
    fun `sem o que procurar, as paginas com imagem tambem passam pelo OCR`() = runTest {
        val pdf = expensePdf(textPage = "Orcamento 007-2026", photoLines = listOf("foto"), imageOnTextPage = true)
        val ocr = CountingOcr("nada aqui")

        PdfContentReaderImpl(ocr).read(pdf, ::enough)

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

    private class CountingOcr(private val text: String, private val words: List<PageWord> = emptyList()) : OcrEngine {
        var calls = 0
        val modes = mutableListOf<OcrMode>()
        val dpis = mutableListOf<Int>()
        val wordCalls get() = modes.size

        override fun read(image: BufferedImage): String {
            calls++
            return text
        }

        override fun readWords(image: BufferedImage, dpi: Int, mode: OcrMode): List<PageWord> {
            modes += mode
            dpis += dpi
            return words
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
