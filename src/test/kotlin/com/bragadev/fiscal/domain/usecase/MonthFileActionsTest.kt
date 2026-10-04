package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.OrganizeMode
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.fakes.FakeCategoryRepository
import com.bragadev.fiscal.fakes.FakeSettingsRepository
import com.bragadev.fiscal.fakes.InMemoryFlagRepository
import com.bragadev.fiscal.fakes.InMemoryHistoryRepository
import com.bragadev.fiscal.fakes.NoOpDocumentRepository
import com.bragadev.fiscal.fakes.TempBackupStorage
import com.bragadev.fiscal.fakes.createFakePdf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renomear, retirar do mês e pendências em arquivos que já estão na pasta do mês. */
class MonthFileActionsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val june: Path by lazy {
        temp.newFolder("pendriver").toPath().resolve("CONTAS CONGREGAÇÃO").resolve("ANO DE SERVIÇO 2025-2026")
            .resolve("4. TRIMESTRE Jun-Jul-Ago").resolve("1. JUNHO").also(Files::createDirectories)
    }
    private val may: Path by lazy { june.parent.parent.resolve("3. TRIMESTRE Mar-Abr-Mai").resolve("3. MAIO").also(Files::createDirectories) }
    private val downloads: Path by lazy { temp.newFolder("Downloads").toPath() }

    private val fileRepository = FileRepositoryImpl()
    private val history = InMemoryHistoryRepository()
    private val flags = InMemoryFlagRepository()
    private val policy = EditablePeriodPolicy(YearMonth.of(2026, 6))
    private val categories = FakeCategoryRepository()
    private val settings by lazy { FakeSettingsRepository(AppSettings(sourceFolder = downloads, monthFolder = june)) }
    private val mover by lazy { RecordedFileMover(fileRepository, history, NoOpDocumentRepository(), flags) }
    private val organize by lazy {
        OrganizeDocumentUseCase(fileRepository, mover, TempBackupStorage(temp.newFolder("backup").toPath()), settings, policy)
    }
    private val undo by lazy { UndoOperationUseCase(fileRepository, history, NoOpDocumentRepository(), flags, policy) }
    private val planDescribed by lazy { PlanOrganizationUseCase(categories, fileRepository, settings, policy) }
    private val planRename by lazy { PlanRenameUseCase(fileRepository, categories, policy) }
    private val removeFromMonth by lazy { RemoveFromMonthUseCase(fileRepository, settings, mover, policy) }
    private val checklist by lazy { GetMonthChecklistUseCase(fileRepository, categories, flags) }

    @Test
    fun `corrigir descricao de despesa mantem o numero e pode ser desfeito`() = runTest {
        june.createFakePdf("3. Despesa - a.pdf")
        val wrong = june.createFakePdf("3.1 Despesa - descricao errada.pdf")

        val plan = (planDescribed(wrong, "congregacao.despesas", OrganizeMode.RENAME_ONLY, "Ônibus congresso") as Outcome.Success).value
        val operation = ((organize(plan) as Outcome.Success).value as OrganizeResult.Done).operation

        assertTrue(Files.exists(june.resolve("3.1 Despesa - Ônibus congresso.pdf")))
        assertIs<Outcome.Success<*>>(undo(operation.id))
        assertTrue(Files.exists(wrong))
    }

    @Test
    fun `renomear livremente arquivo do mes`() = runTest {
        val file = june.createFakePdf("7. Relatório Mensal (S-30).pdf")

        val plan = (planRename(file, "congregacao.relatorio_mensal", "7. Relatório Mensal") as Outcome.Success).value
        organize(plan)

        assertTrue(Files.exists(june.resolve("7. Relatório Mensal.pdf")))
        assertFalse(Files.exists(file))
    }

    @Test
    fun `renomear acusa nome ja usado e nome vazio`() = runTest {
        val file = june.createFakePdf("8. Extrato errado.pdf")
        june.createFakePdf("8. Extrato Bancário.pdf")

        val taken = (planRename(file, "congregacao.extrato_bancario", "8. extrato bancário.pdf") as Outcome.Success).value
        assertTrue(taken.hasConflict)
        assertEquals(Outcome.Failure(FileOperationError.NameRequired), planRename(file, "congregacao.extrato_bancario", "  .pdf"))
        assertEquals(Outcome.Failure(FileOperationError.InvalidFileName), planRename(file, "congregacao.extrato_bancario", "a:b"))
    }

    @Test
    fun `nao renomeia arquivo de mes bloqueado`() = runTest {
        val old = may.createFakePdf("8. Extrato Bancário.pdf")

        val result = planRename(old, "congregacao.extrato_bancario", "outro")

        assertIs<FileOperationError.MonthLocked>((result as Outcome.Failure).error)
    }

    @Test
    fun `retirar do mes devolve para a pasta de origem e pode ser desfeito`() = runTest {
        val wrong = june.createFakePdf("8. Extrato Bancário.pdf")

        val operation = (removeFromMonth(wrong) as Outcome.Success).value

        assertFalse(Files.exists(wrong))
        assertTrue(Files.exists(downloads.resolve("8. Extrato Bancário.pdf")))
        assertIs<Outcome.Success<*>>(undo(operation.id))
        assertTrue(Files.exists(wrong))
    }

    @Test
    fun `retirar do mes nunca sobrescreve arquivo na pasta de origem`() = runTest {
        downloads.createFakePdf("8. Extrato Bancário.pdf", content = "original")
        val wrong = june.createFakePdf("8. Extrato Bancário.pdf", content = "do mes")

        assertEquals(downloads.resolve("8. Extrato Bancário (2).pdf"), (removeFromMonth.plan(wrong) as Outcome.Success).value)
        removeFromMonth(wrong)

        assertTrue(Files.readString(downloads.resolve("8. Extrato Bancário.pdf")).contains("original"))
        assertTrue(Files.readString(downloads.resolve("8. Extrato Bancário (2).pdf")).contains("do mes"))
    }

    @Test
    fun `retirar do mes exige pasta de origem diferente da pasta do mes`() = runTest {
        settings.save(settings.settings.value.copy(sourceFolder = june))
        val file = june.createFakePdf("8. Extrato Bancário.pdf")

        assertEquals(Outcome.Failure(FileOperationError.ReturnFolderIsMonthFolder), removeFromMonth.plan(file))
    }

    @Test
    fun `pendencia aparece no checklist e acompanha o arquivo renomeado`() = runTest {
        val file = june.createFakePdf("8. Extrato errado.pdf")
        FileFlagUseCase(flags).mark(file, "Arquivo errado")

        val before = checklist(june, AccountType.CONGREGACAO)
        assertTrue(before.hasPendingIssue("congregacao.extrato_bancario"))
        assertEquals("Arquivo errado", before.flagFor("8. Extrato errado.pdf"))

        organize((planRename(file, "congregacao.extrato_bancario", "8. Extrato Bancário") as Outcome.Success).value)

        assertEquals("Arquivo errado", checklist(june, AccountType.CONGREGACAO).flagFor("8. Extrato Bancário.pdf"))
        FileFlagUseCase(flags).clear(june.resolve("8. Extrato Bancário.pdf"))
        assertNull(checklist(june, AccountType.CONGREGACAO).flagFor("8. Extrato Bancário.pdf"))
    }

    @Test
    fun `trocar o numero de uma despesa na ordem errada`() = runTest {
        june.createFakePdf("3. Despesa - Conta de energia.pdf")
        june.createFakePdf("3.1 Despesa - Conta de agua.pdf")
        val wrong = june.createFakePdf("3. Despesa - Objetivos especificos.pdf")

        val plan = (
            planDescribed(wrong, "congregacao.despesas", OrganizeMode.RENAME_ONLY, "Objetivos especificos", sequenceIndex = 2)
                as Outcome.Success
            ).value
        assertTrue(plan.sameNumberFiles.isEmpty())
        organize(plan)

        assertTrue(Files.exists(june.resolve("3.2 Despesa - Objetivos especificos.pdf")))
        assertFalse(Files.exists(wrong))
    }

    @Test
    fun `numero repetido gera aviso mas nao bloqueia`() = runTest {
        june.createFakePdf("3. Despesa - a.pdf")
        val other = june.createFakePdf("3.1 Despesa - b.pdf")

        val plan = (planDescribed(other, "congregacao.despesas", OrganizeMode.RENAME_ONLY, "b", sequenceIndex = 0) as Outcome.Success).value

        assertEquals(listOf("3. Despesa - a.pdf"), plan.sameNumberFiles)
        assertFalse(plan.hasConflict)
    }

    @Test
    fun `nome completo da despesa pode ser editado livremente`() = runTest {
        val file = june.createFakePdf("3. Despesa - Objetivos especificos.pdf")

        organize((planRename(file, "congregacao.despesas", "3.2 Despesa - Objetivos específicos") as Outcome.Success).value)

        assertTrue(Files.exists(june.resolve("3.2 Despesa - Objetivos específicos.pdf")))
    }
}
