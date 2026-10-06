package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.data.pdf.PdfToolsRepositoryImpl
import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.MonthReview
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.fakes.FakeCategoryRepository
import com.bragadev.fiscal.fakes.InMemoryFlagRepository
import com.bragadev.fiscal.fakes.createFakePdf
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** "Concluir mês": conferência da pasta e relatório em PDF. */
class MonthClosingTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val year: Path by lazy {
        temp.newFolder("pendriver").toPath().resolve("CONTAS MANUTENÇÃO").resolve("ANO DE SERVIÇO 2025-2026")
    }
    private val september: Path by lazy { year.resolve("5. Trimestre Set-Out-Nov").resolve("1. Setembro").also(Files::createDirectories) }
    private val downloads: Path by lazy { temp.newFolder("Downloads").toPath() }

    private val fileRepository = FileRepositoryImpl()
    private val flags = InMemoryFlagRepository()
    private val categories = FakeCategoryRepository()
    private val policy = EditablePeriodPolicy(YearMonth.of(2026, 6))
    private val review = ReviewMonthUseCase(
        fileRepository,
        GetCategoryTreeUseCase(categories),
        GetMonthChecklistUseCase(fileRepository, categories, flags),
        policy,
        now = { LocalDateTime.of(2026, 10, 6, 9, 30) },
    )
    private val generate = GenerateMonthReportUseCase(PdfToolsRepositoryImpl(), PdfOutputResolver(fileRepository, policy))

    private suspend fun septemberReview(): MonthReview {
        september.createFakePdf("1. Folha de Contas.pdf")
        september.createFakePdf("2. Donativo - Japiim.pdf")
        september.createFakePdf("2.1 Donativo Trinta e Um de Março.pdf")
        val internet = september.createFakePdf("3. Despesa - Internet.pdf")
        flags.set(internet, "Falta o comprovante de pagamento")
        september.createFakePdf("5. Extrato Bancário.pdf")
        september.createFakePdf("recibo solto 😀.pdf")
        return (review(september) as Outcome.Success).value
    }

    @Test
    fun `conferencia mostra o que falta e as pendencias`() = runTest {
        val result = septemberReview()

        assertEquals(YearMonth.of(2026, 9), result.month)
        assertEquals(AccountType.MANUTENCAO, result.account)
        assertEquals(listOf("4. Relatório Mensal"), result.missing.map { it.category.label })
        assertEquals(listOf("3. Despesas"), result.withIssues.map { it.category.label })
        assertEquals(3, result.doneCount, "Folha, Donativos e Extrato; Despesas tem pendência")
        assertEquals(5, result.requiredCount, "Outros é opcional e não conta")
        assertFalse(result.isComplete)
        val donativos = result.lines.single { it.category.id == "manutencao.donativos_congregacoes" }
        assertEquals(listOf("2. Donativo - Japiim.pdf", "2.1 Donativo Trinta e Um de Março.pdf"), donativos.files.sorted())
        assertEquals(listOf("recibo solto 😀.pdf"), result.unmatchedFiles)
    }

    @Test
    fun `mes completo`() = runTest {
        listOf("1. Folha de Contas", "2. Donativo - Japiim", "3. Despesa - Água", "4. Relatório Mensal", "5. Extrato Bancário")
            .forEach { september.createFakePdf("$it.pdf") }

        val result = (review(september) as Outcome.Success).value

        assertTrue(result.isComplete)
        assertEquals(5, result.doneCount)
    }

    @Test
    fun `relatorio pdf lista cada categoria com arquivos, faltando e pendencias`() = runTest {
        val result = septemberReview()

        val report = (generate(result, downloads, "Relatório - Setembro de 2026") as Outcome.Success).value

        assertEquals(downloads.resolve("Relatório - Setembro de 2026.pdf"), report)
        val text = Loader.loadPDF(report.toFile()).use { PDFTextStripper().getText(it) }
        listOf(
            "Relatório do mês — Setembro de 2026",
            "Conta da Manutenção",
            "Situação: INCOMPLETO — 1 categoria faltando e 1 pendência.",
            "Categorias obrigatórias com arquivo: 3 de 5.",
            "FALTANDO",
            "4. Relatório Mensal",
            "2.1 Donativo Trinta e Um de Março.pdf",
            "Pendência: Falta o comprovante de pagamento",
            "Arquivos sem número de categoria",
            "recibo solto ?.pdf",
            "Gerado em 06/10/2026 às 09:30",
            "Página 1 de",
        ).forEach { expected -> assertTrue(expected in text, "relatório deveria conter \"$expected\"") }
        assertEquals(6, september.toFile().list()!!.size, "a pasta do mês não é alterada")
    }

    @Test
    fun `relatorio nunca sobrescreve e nao grava em mes fechado`() = runTest {
        val result = septemberReview()
        generate(result, downloads, "Relatório")

        assertEquals(downloads.resolve("Relatório (2).pdf"), (generate(result, downloads, "Relatório") as Outcome.Success).value)

        val may = year.resolve("3. Trimestre Mar-Abr-Mai").resolve("3. Maio").also(Files::createDirectories)
        assertIs<FileOperationError.MonthLocked>((generate(result, may, "Relatório") as Outcome.Failure).error)
    }

    @Test
    fun `pasta que nao e de um mes nao pode ser concluida`() = runTest {
        assertEquals(Outcome.Failure(FileOperationError.MonthNotIdentified), review(downloads))
    }

    @Test
    fun `relatorio longo continua nas paginas seguintes`() = runTest {
        repeat(80) { september.createFakePdf("3.${it + 1} Despesa - Compra número ${it + 1} com uma descrição bem comprida.pdf") }
        val result = (review(september) as Outcome.Success).value

        val report = (generate(result, downloads, "Longo") as Outcome.Success).value

        val pages = Loader.loadPDF(report.toFile()).use { it.numberOfPages }
        assertTrue(pages > 1, "80 despesas não cabem em uma página")
        val text = Loader.loadPDF(report.toFile()).use { PDFTextStripper().getText(it) }
        assertTrue("3.80 Despesa - Compra número 80" in text)
        assertTrue("Página $pages de $pages" in text)
    }
}
