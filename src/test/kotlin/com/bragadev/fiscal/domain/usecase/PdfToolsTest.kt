package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.data.pdf.PdfToolsRepositoryImpl
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.YearMonth
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PdfToolsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val folder: Path by lazy { temp.newFolder("Downloads").toPath() }
    private val fileRepository = FileRepositoryImpl()
    private val resolver = PdfOutputResolver(fileRepository, EditablePeriodPolicy(YearMonth.of(2026, 6)))
    private val tools = PdfToolsRepositoryImpl()
    private val imagesToPdf = CreatePdfFromImagesUseCase(fileRepository, tools, resolver)
    private val merge = MergePdfsUseCase(fileRepository, tools, resolver)

    private fun image(name: String, width: Int, height: Int, format: String): Path =
        folder.resolve(name).also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, it.toFile()) }

    private fun pdf(name: String, pages: Int): Path = folder.resolve(name).also { path ->
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.save(path.toFile())
        }
    }

    private fun pageSizes(path: Path) = Loader.loadPDF(path.toFile()).use { doc -> doc.pages.map { it.mediaBox.width to it.mediaBox.height } }

    @Test
    fun `imagens viram um pdf com uma pagina por imagem na orientacao certa`() = runTest {
        val landscape = image("foto-deitada.jpg", 400, 200, "jpg")
        val portrait = image("recibo.png", 200, 400, "png")

        val created = (imagesToPdf(listOf(ImagePage(landscape), ImagePage(portrait), ImagePage(landscape, 90)), folder, "Fotos") as Outcome.Success).value

        assertEquals(folder.resolve("Fotos.pdf"), created)
        val sizes = pageSizes(created)
        assertEquals(3, sizes.size)
        assertTrue(sizes[0].first > sizes[0].second, "imagem larga → página deitada")
        assertTrue(sizes[1].first < sizes[1].second, "imagem alta → página em pé")
        assertTrue(sizes[2].first < sizes[2].second, "imagem larga girada 90° → página em pé")
    }

    @Test
    fun `juntar pdfs mantem a ordem e nao altera os originais`() = runTest {
        val first = pdf("a.pdf", 1)
        val second = pdf("b.pdf", 2)
        val originalBytes = Files.readAllBytes(first)

        val created = (merge(listOf(second, first), folder, "Unido") as Outcome.Success).value

        assertEquals(3, pageSizes(created).size)
        assertContentEquals(originalBytes, Files.readAllBytes(first))
        assertTrue(Files.exists(second))
    }

    @Test
    fun `nunca sobrescreve um pdf existente`() = runTest {
        val existing = pdf("Unido.pdf", 1)
        val before = Files.readAllBytes(existing)

        val created = (merge(listOf(pdf("a.pdf", 1), pdf("b.pdf", 1)), folder, "Unido") as Outcome.Success).value

        assertEquals(folder.resolve("Unido (2).pdf"), created)
        assertContentEquals(before, Files.readAllBytes(existing))
    }

    @Test
    fun `validacoes antes de criar`() = runTest {
        assertEquals(Outcome.Failure(FileOperationError.NoInputFiles), merge(listOf(pdf("so-um.pdf", 1)), folder, "x"))
        assertEquals(Outcome.Failure(FileOperationError.NoInputFiles), imagesToPdf(emptyList(), folder, "x"))
        val gif = folder.resolve("anim.gif").also { Files.writeString(it, "GIF89a") }
        assertEquals(Outcome.Failure(FileOperationError.UnsupportedImage), imagesToPdf(listOf(ImagePage(gif)), folder, "x"))
        assertEquals(Outcome.Failure(FileOperationError.NameRequired), imagesToPdf(listOf(ImagePage(image("a.jpg", 10, 10, "jpg"))), folder, " "))
    }

    @Test
    fun `nao salva em mes fechado`() = runTest {
        val may = temp.root.toPath().resolve("CONTAS CONGREGAÇÃO").resolve("ANO DE SERVIÇO 2025-2026")
            .resolve("3. TRIMESTRE Mar-Abr-Mai").resolve("3. MAIO").also(Files::createDirectories)

        val result = imagesToPdf(listOf(ImagePage(image("a.jpg", 10, 10, "jpg"))), may, "x")

        assertIs<FileOperationError.MonthLocked>((result as Outcome.Failure).error)
    }

    @Test
    fun `imagem corrompida nao deixa arquivo pela metade`() = runTest {
        val broken = folder.resolve("quebrada.jpg").also { Files.writeString(it, "isto não é uma imagem") }

        val result = imagesToPdf(listOf(ImagePage(broken)), folder, "Saida")

        assertEquals(Outcome.Failure(FileOperationError.UnsupportedImage), result)
        assertFalse(Files.exists(folder.resolve("Saida.pdf")))
    }
}
