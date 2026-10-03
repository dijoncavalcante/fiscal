package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.domain.model.DuplicateResolution
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.OrganizationPlan
import com.bragadev.fiscal.domain.model.OrganizeMode
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.UndoBlockReason
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
import kotlin.test.assertTrue

class OrganizeAndUndoTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val pendrive: Path by lazy { temp.newFolder("pendriver").toPath() }
    private val serviceYear: Path by lazy { pendrive.resolve("CONTAS CONGREGAÇÃO").resolve("ANO DE SERVIÇO 2025-2026") }
    private val june: Path by lazy { serviceYear.resolve("4. TRIMESTRE Jun-Jul-Ago").resolve("1. JUNHO").also(Files::createDirectories) }
    private val may: Path by lazy { serviceYear.resolve("3. TRIMESTRE Mar-Abr-Mai").resolve("3. MAIO").also(Files::createDirectories) }
    private val maintenanceJune: Path by lazy {
        pendrive.resolve("CONTAS MANUTENÇÃO").resolve("ANO DE SERVIÇO 2025-2026")
            .resolve("4. Trimestre Jun-Jul-Ago").resolve("1. Junho").also(Files::createDirectories)
    }
    private val downloads: Path by lazy { temp.newFolder("Downloads").toPath() }

    private val fileRepository = FileRepositoryImpl()
    private val history = InMemoryHistoryRepository()
    private val policy = EditablePeriodPolicy(YearMonth.of(2026, 6))
    private val settings by lazy { FakeSettingsRepository(AppSettings(sourceFolder = downloads, monthFolder = june)) }
    private val plan by lazy { PlanOrganizationUseCase(FakeCategoryRepository(), fileRepository, settings, policy) }
    private val flags = InMemoryFlagRepository()
    private val mover by lazy { RecordedFileMover(fileRepository, history, NoOpDocumentRepository(), flags) }
    private val organize by lazy {
        OrganizeDocumentUseCase(fileRepository, mover, TempBackupStorage(temp.newFolder("backup").toPath()), settings, policy)
    }
    private val undo by lazy { UndoOperationUseCase(fileRepository, history, NoOpDocumentRepository(), flags, policy) }

    @Test
    fun `organiza no mes em edicao e desfaz devolvendo o nome original`() = runTest {
        val source = downloads.createFakePdf("xxx.pdf")

        val proposal = planFor(source, "congregacao.extrato_bancario")
        assertEquals("8. Extrato Bancário.pdf", proposal.suggestedName)
        assertEquals(june, proposal.targetDirectory)

        val operation = organizeDone(proposal)
        assertTrue(Files.exists(june.resolve("8. Extrato Bancário.pdf")))
        assertFalse(Files.exists(source))

        assertIs<Outcome.Success<*>>(undo(operation.id))
        assertTrue(Files.exists(source))
        assertFalse(Files.exists(june.resolve("8. Extrato Bancário.pdf")))
        assertTrue(history.operations.single().undone)
    }

    @Test
    fun `subcategoria fica direto na pasta do mes`() = runTest {
        val source = downloads.createFakePdf("comprovante.pdf")

        organizeDone(planFor(source, "congregacao.comprovante_remessa"))

        assertTrue(Files.exists(june.resolve("5.1 Comprovante Remessa.pdf")))
    }

    @Test
    fun `mes anterior a junho de 2026 nao pode ser destino`() = runTest {
        settings.save(settings.settings.value.copy(monthFolder = may))
        val source = downloads.createFakePdf("xxx.pdf")

        val result = plan(source, "congregacao.extrato_bancario", OrganizeMode.RENAME_AND_MOVE)

        assertEquals(Outcome.Failure(FileOperationError.MonthLocked(YearMonth.of(2026, 5), YearMonth.of(2026, 6))), result)
        assertTrue(Files.exists(source))
    }

    @Test
    fun `arquivo de mes bloqueado nao pode ser renomeado nem movido`() = runTest {
        val oldFile = may.createFakePdf("antigo.pdf")

        val rename = plan(oldFile, "congregacao.extrato_bancario", OrganizeMode.RENAME_ONLY)
        val move = plan(oldFile, "congregacao.extrato_bancario", OrganizeMode.RENAME_AND_MOVE)

        assertIs<Outcome.Failure>(rename)
        assertIs<FileOperationError.MonthLocked>(rename.error)
        assertIs<FileOperationError.MonthLocked>((move as Outcome.Failure).error)
        assertTrue(Files.exists(oldFile))
    }

    @Test
    fun `execucao tambem recusa plano para mes bloqueado`() = runTest {
        val source = downloads.createFakePdf("xxx.pdf")
        val forged = planFor(source, "congregacao.extrato_bancario").copy(targetDirectory = may)

        val result = organize(forged)

        assertIs<FileOperationError.MonthLocked>((result as Outcome.Failure).error)
        assertTrue(Files.exists(source))
    }

    @Test
    fun `pasta que nao e de mes nao recebe documentos`() = runTest {
        settings.save(settings.settings.value.copy(monthFolder = serviceYear))
        val source = downloads.createFakePdf("xxx.pdf")

        val result = plan(source, "congregacao.extrato_bancario", OrganizeMode.RENAME_AND_MOVE)

        assertEquals(Outcome.Failure(FileOperationError.MonthNotIdentified), result)
    }

    @Test
    fun `sem pasta do mes selecionada nao e possivel mover`() = runTest {
        settings.save(settings.settings.value.copy(monthFolder = null))
        val source = downloads.createFakePdf("xxx.pdf")

        val result = plan(source, "congregacao.extrato_bancario", OrganizeMode.RENAME_AND_MOVE)

        assertEquals(Outcome.Failure(FileOperationError.MonthFolderNotSelected), result)
    }

    @Test
    fun `categoria da congregacao nao entra em mes da manutencao`() = runTest {
        settings.save(settings.settings.value.copy(monthFolder = maintenanceJune))
        val source = downloads.createFakePdf("xxx.pdf")

        val result = plan(source, "congregacao.extrato_betel", OrganizeMode.RENAME_AND_MOVE)

        assertEquals(Outcome.Failure(FileOperationError.CategoryNotInMonthAccount), result)
    }

    @Test
    fun `conflito com perguntar sempre nao altera nada sem escolha`() = runTest {
        june.createFakePdf("8. Extrato Bancário.pdf", content = "original")
        val source = downloads.createFakePdf("novo.pdf")
        val proposal = planFor(source, "congregacao.extrato_bancario")

        assertTrue(proposal.hasConflict)
        assertEquals("8. Extrato Bancário (2).pdf", proposal.numberedCopyName)
        assertEquals(OrganizeResult.NeedsConflictChoice, (organize(proposal) as Outcome.Success).value)
        assertTrue(Files.exists(source))
    }

    @Test
    fun `copia numerada preserva o arquivo existente`() = runTest {
        val existing = june.createFakePdf("8. Extrato Bancário.pdf", content = "original")
        val source = downloads.createFakePdf("novo.pdf")

        organizeDone(planFor(source, "congregacao.extrato_bancario"), DuplicateResolution.NUMBERED_COPY)

        assertTrue(Files.readString(existing).contains("original"))
        assertTrue(Files.exists(june.resolve("8. Extrato Bancário (2).pdf")))
    }

    @Test
    fun `substituir guarda backup e desfazer restaura os dois arquivos`() = runTest {
        val existing = june.createFakePdf("8. Extrato Bancário.pdf", content = "original")
        val source = downloads.createFakePdf("novo.pdf", content = "novo")

        val operation = organizeDone(planFor(source, "congregacao.extrato_bancario"), DuplicateResolution.REPLACE)
        assertTrue(Files.readString(existing).contains("novo"))

        assertIs<Outcome.Success<*>>(undo(operation.id))
        assertTrue(Files.readString(existing).contains("original"))
        assertTrue(Files.readString(source).contains("novo"))
    }

    @Test
    fun `nao permitir duplicados bloqueia a operacao`() = runTest {
        settings.save(settings.settings.value.copy(duplicatePolicy = DuplicatePolicy.FORBID))
        june.createFakePdf("8. Extrato Bancário.pdf")
        val source = downloads.createFakePdf("novo.pdf")

        val result = organize(planFor(source, "congregacao.extrato_bancario"), DuplicateResolution.REPLACE)

        assertEquals(Outcome.Failure(FileOperationError.DuplicatesNotAllowed), result)
        assertTrue(Files.exists(source))
    }

    @Test
    fun `outros recebe a proxima numeracao livre no mes`() = runTest {
        june.createFakePdf("1. Outros.pdf")
        june.createFakePdf("2. Outros.pdf")
        val source = downloads.createFakePdf("qualquer.pdf")

        val proposal = planFor(source, "outros")

        assertEquals("3. Outros.pdf", proposal.suggestedName)
        assertFalse(proposal.hasConflict)
    }

    @Test
    fun `renomear mantem o arquivo na pasta atual`() = runTest {
        val source = downloads.createFakePdf("xxx.pdf")

        organizeDone(planFor(source, "congregacao.extrato_bancario", OrganizeMode.RENAME_ONLY))

        assertTrue(Files.exists(downloads.resolve("8. Extrato Bancário.pdf")))
    }

    @Test
    fun `documento ja organizado nao gera proposta`() = runTest {
        val source = june.createFakePdf("8. Extrato Bancário.pdf")

        val result = plan(source, "congregacao.extrato_bancario", OrganizeMode.RENAME_AND_MOVE)

        assertEquals(Outcome.Failure(FileOperationError.AlreadyInPlace), result)
    }

    @Test
    fun `arquivo que nao e pdf e recusado`() = runTest {
        val text = downloads.resolve("nota.txt").also { Files.writeString(it, "texto") }

        val result = plan(text, "congregacao.extrato_bancario", OrganizeMode.RENAME_AND_MOVE)

        assertEquals(Outcome.Failure(FileOperationError.InvalidPdf), result)
    }

    @Test
    fun `desfazer e bloqueado se o local original foi ocupado`() = runTest {
        val source = downloads.createFakePdf("xxx.pdf")
        val operation = organizeDone(planFor(source, "congregacao.extrato_bancario"))
        downloads.createFakePdf("xxx.pdf", content = "outro arquivo")

        val result = undo(operation.id)

        assertEquals(Outcome.Failure(FileOperationError.UndoNotPossible(UndoBlockReason.ORIGINAL_LOCATION_OCCUPIED)), result)
        assertTrue(Files.exists(june.resolve("8. Extrato Bancário.pdf")))
    }

    @Test
    fun `desfazer duas vezes e bloqueado`() = runTest {
        val source = downloads.createFakePdf("xxx.pdf")
        val operation = organizeDone(planFor(source, "congregacao.extrato_bancario"))
        undo(operation.id)

        val result = undo(operation.id)

        assertEquals(Outcome.Failure(FileOperationError.UndoNotPossible(UndoBlockReason.ALREADY_UNDONE)), result)
    }

    @Test
    fun `despesas entram em sequencia 3 3_1 3_2 na pasta do mes`() = runTest {
        val first = downloads.createFakePdf("recibo-a.pdf")
        val second = downloads.createFakePdf("recibo-b.pdf")

        organizeDone(planFor(first, "congregacao.despesas", description = "Compra de cartazes"))
        val secondPlan = planFor(second, "congregacao.despesas", description = "Ônibus")
        organizeDone(secondPlan)

        assertTrue(Files.exists(june.resolve("3. Despesa - Compra de cartazes.pdf")))
        assertTrue(Files.exists(june.resolve("3.1 Despesa - Ônibus.pdf")))
        assertFalse(secondPlan.hasConflict)
    }

    @Test
    fun `despesa sem descricao nao gera proposta`() = runTest {
        val source = downloads.createFakePdf("recibo.pdf")

        val result = plan(source, "congregacao.despesas", OrganizeMode.RENAME_AND_MOVE, "  ")

        assertEquals(Outcome.Failure(FileOperationError.DescriptionRequired), result)
    }

    @Test
    fun `editar despesa ja no mes troca so a descricao e mantem o numero`() = runTest {
        june.createFakePdf("3. Despesa - a.pdf")
        val existing = june.createFakePdf("3.1 Despesa - errado.pdf")
        june.createFakePdf("3.2 Despesa - c.pdf")

        organizeDone(planFor(existing, "congregacao.despesas", description = "corrigido"))

        assertTrue(Files.exists(june.resolve("3.1 Despesa - corrigido.pdf")))
        assertFalse(Files.exists(existing))
    }

    private suspend fun planFor(
        source: Path,
        categoryId: String,
        mode: OrganizeMode = OrganizeMode.RENAME_AND_MOVE,
        description: String? = null,
    ): OrganizationPlan = (plan(source, categoryId, mode, description) as Outcome.Success).value

    private suspend fun organizeDone(proposal: OrganizationPlan, resolution: DuplicateResolution? = null) =
        ((organize(proposal, resolution) as Outcome.Success).value as OrganizeResult.Done).operation
}
