package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.repository.DocumentContentReader
import com.bragadev.fiscal.domain.repository.ExpenseCheckRepository
import com.bragadev.fiscal.fakes.FakeCategoryRepository
import com.bragadev.fiscal.fakes.InMemoryFlagRepository
import com.bragadev.fiscal.fakes.createFakePdf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Conferência das despesas da Conta da Manutenção: pendências automáticas, check verde e cache. */
class CheckExpensesUseCaseTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val september: Path by lazy {
        temp.newFolder("pendriver").toPath().resolve("CONTAS MANUTENÇÃO").resolve("ANO DE SERVIÇO 2025-2026")
            .resolve("5. Trimestre Set-Out-Nov").resolve("1. Setembro").also(Files::createDirectories)
    }

    private val complete = DocumentContent(
        listOf(
            "COMPROVANTE DE VENDA CNPJ TOTAL R$ 125,00",
            "Comprovante de transação Pix Valor: 125.00 Id da transação: E60746948202609191355M3943eq0hGo"
        ),
        signers = listOf("CN=ELVES LINCOLN MENEZES DE LIMA"),
    )
    private val unsigned = complete.copy(signers = emptyList())

    private val fileRepository = FileRepositoryImpl()
    private val flags = InMemoryFlagRepository()
    private val checks = InMemoryExpenseChecks()
    private val reader = FakeReader()
    private val checkExpenses = CheckExpensesUseCase(fileRepository, reader, checks, flags)
    private val checklist = GetMonthChecklistUseCase(fileRepository, FakeCategoryRepository(), flags)

    private val evidences = mutableMapOf<String, String?>()

    private suspend fun run(
        folder: Path = september,
        account: AccountType = AccountType.MANUTENCAO
    ): Map<String, ExpenseStatus> {
        val results = mutableMapOf<String, ExpenseStatus>()
        checkExpenses(folder, account, checklist(folder, account)) { name, status, evidence ->
            results[name] = status
            evidences[name] = evidence
        }
        return results
    }

    @Test
    fun `despesa completa ganha o check e incompleta ganha pendencia dizendo o que falta`() = runTest {
        val ok = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        val missing = september.createFakePdf("3.1 Despesa - Espuma para microfone.pdf")
        september.createFakePdf("1. Folha de Contas.pdf")
        reader.contents[ok.fileName.toString()] = complete
        reader.contents[missing.fileName.toString()] = unsigned

        val results = run()

        assertEquals(ExpenseStatus.VERIFIED, results["3. Despesa - Dreno encapado.pdf"])
        assertEquals(ExpenseStatus.HAS_ISSUES, results["3.1 Despesa - Espuma para microfone.pdf"])
        assertNull(flags.flags[ok])
        assertEquals("Falta assinatura de Elves Lincoln", flags.flags[missing])
        // Só as despesas são conferidas.
        assertEquals(2, results.size)
    }

    @Test
    fun `conta da Congregacao nao e conferida`() = runTest {
        val congregation = temp.newFolder("CONTAS CONGREGAÇÃO", "1. SETEMBRO").toPath()
        congregation.createFakePdf("3. Despesa - Luz.pdf")

        val results = run(congregation, AccountType.CONGREGACAO)

        assertTrue(results.isEmpty())
        assertEquals(0, reader.reads)
    }

    @Test
    fun `pendencia escrita pelo usuario nunca e trocada nem removida`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = unsigned
        flags.set(file, "Valor diferente do cupom")

        run()
        assertEquals("Valor diferente do cupom", flags.flags[file])

        // Mesmo depois de corrigido o arquivo.
        replace(file, complete)
        run()
        assertEquals("Valor diferente do cupom", flags.flags[file])
    }

    @Test
    fun `arquivo corrigido perde a pendencia automatica`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = unsigned
        run()
        assertEquals("Falta assinatura de Elves Lincoln", flags.flags[file])

        replace(file, complete)
        val results = run()

        assertNull(flags.flags[file])
        assertEquals(ExpenseStatus.VERIFIED, results[file.fileName.toString()])
    }

    @Test
    fun `arquivo sem mudanca nao e lido de novo e pendencia removida pelo usuario nao volta`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = unsigned
        run()
        assertEquals(1, reader.reads)

        flags.clear(file)
        val results = run()

        assertEquals(1, reader.reads)
        assertNull(flags.flags[file])
        assertEquals(ExpenseStatus.HAS_ISSUES, results[file.fileName.toString()])
    }

    @Test
    fun `despesa verificada diz de onde veio o total, tambem quando vem do resultado guardado`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = complete
        val expected = "R$ 125,00 em \"TOTAL\", página 1 (texto do PDF)"

        run()
        assertEquals(expected, evidences[file.fileName.toString()])

        evidences.clear()
        run()
        assertEquals(1, reader.reads)
        assertEquals(expected, evidences[file.fileName.toString()])
    }

    @Test
    fun `valor do Pix diferente do cupom vira pendencia que diz como corrigir`() = runTest {
        val file = september.createFakePdf("3. Despesa - Material de limpeza.pdf")
        reader.contents[file.fileName.toString()] = complete.copy(
            pageTexts = listOf("NOTA FISCAL Total R$ 395,98", "Comprovante de transação Pix Valor: 405.98"),
        )

        assertEquals(ExpenseStatus.HAS_ISSUES, run()[file.fileName.toString()])
        assertEquals(
            "Valor do comprovante (R$ 405,98) diferente do cupom/nota (R$ 395,98): confira e troque o documento errado",
            flags.flags[file],
        )
    }

    @Test
    fun `resultado guardado por regras antigas e conferido de novo`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = complete
        val old = StoredExpenseCheck(
            Files.size(file), Files.getLastModifiedTime(file).toInstant(), ExpenseCheck(), rulesVersion = 1,
        )
        checks.save(file, old)

        run()

        assertEquals(1, reader.reads)
    }

    @Test
    fun `pendencia automatica da versao anterior e trocada pela nova`() = runTest {
        // Até a versão 2 o recibo não valia: a pendência antiga dizia "cupom ou nota fiscal".
        val file = september.createFakePdf("3. Despesa - Conserto do portão.pdf")
        flags.set(file, "Falta cupom ou nota fiscal")
        checks.save(
            file,
            StoredExpenseCheck(
                Files.size(file), Files.getLastModifiedTime(file).toInstant(),
                ExpenseCheck(setOf(ExpenseRequirement.FISCAL_DOCUMENT)), rulesVersion = 2,
            ),
        )
        reader.contents[file.fileName.toString()] = complete.copy(
            pageTexts = listOf("RECIBO Recebi a importância de R$ 125,00 Total R$ 125,00", complete.pageTexts[1]),
        )

        assertEquals(ExpenseStatus.VERIFIED, run()[file.fileName.toString()])
        assertNull(flags.flags[file])
    }

    @Test
    fun `falha passageira nao marca pendencia e tenta de novo depois`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.failure = FileOperationError.FileLocked

        assertEquals(ExpenseStatus.NOT_CHECKED, run()[file.fileName.toString()])
        assertNull(flags.flags[file])

        reader.failure = null
        reader.contents[file.fileName.toString()] = complete
        assertEquals(ExpenseStatus.VERIFIED, run()[file.fileName.toString()])
    }

    @Test
    fun `PDF que nao abre vira pendencia`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.failure = FileOperationError.InvalidPdf

        assertEquals(ExpenseStatus.HAS_ISSUES, run()[file.fileName.toString()])
        assertEquals("Não foi possível ler o PDF", flags.flags[file])
    }

    /** Troca o conteúdo do arquivo (outro tamanho e outra data), como quando o usuário substitui o PDF. */
    private fun replace(file: Path, content: DocumentContent) {
        Files.writeString(file, Files.readString(file) + "\n% corrigido")
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(60)))
        reader.contents[file.fileName.toString()] = content
    }

    private class FakeReader : DocumentContentReader {
        val contents = mutableMapOf<String, DocumentContent>()
        var failure: FileOperationError? = null
        var reads = 0

        override suspend fun read(file: Path, isEnough: (DocumentContent) -> Boolean): Outcome<DocumentContent> {
            reads++
            failure?.let { return Outcome.Failure(it) }
            return Outcome.Success(contents[file.fileName.toString()] ?: DocumentContent(emptyList()))
        }
    }

    private class InMemoryExpenseChecks : ExpenseCheckRepository {
        private val saved = mutableMapOf<Path, StoredExpenseCheck>()

        override suspend fun checksIn(folder: Path): Map<String, StoredExpenseCheck> =
            saved.filterKeys { it.parent == folder }.mapKeys { it.key.fileName.toString().lowercase() }

        override suspend fun save(file: Path, check: StoredExpenseCheck) {
            saved[file] = check
        }
    }
}
