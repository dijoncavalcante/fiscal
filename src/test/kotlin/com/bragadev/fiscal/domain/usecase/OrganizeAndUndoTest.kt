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
import com.bragadev.fiscal.fakes.FakeCategoryRepository
import com.bragadev.fiscal.fakes.FakeSettingsRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OrganizeAndUndoTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val root: Path by lazy { temp.newFolder("pendriver").toPath() }
    private val inbox: Path by lazy { root.resolve("entrada") }
    private val extratoFolder: Path by lazy { root.resolve("Conta da Congregação").resolve("8. Extrato Bancário") }

    private val fileRepository = FileRepositoryImpl()
    private val history = InMemoryHistoryRepository()
    private val settings by lazy { FakeSettingsRepository(AppSettings(rootPath = root)) }
    private val plan by lazy { PlanOrganizationUseCase(FakeCategoryRepository(), fileRepository, settings) }
    private val organize by lazy {
        OrganizeDocumentUseCase(
            fileRepository, history, NoOpDocumentRepository(), TempBackupStorage(temp.newFolder("backup").toPath()), settings,
        )
    }
    private val undo by lazy { UndoOperationUseCase(fileRepository, history, NoOpDocumentRepository()) }

    @Test
    fun `organiza e desfaz devolvendo o nome original`() = runTest {
        val source = inbox.createFakePdf("xxx.pdf")

        val proposal = planFor(source, "congregacao.extrato_bancario")
        assertEquals("Extrato Bancário.pdf", proposal.suggestedName)
        assertEquals(extratoFolder, proposal.targetDirectory)

        val operation = organizeDone(proposal)
        assertTrue(Files.exists(extratoFolder.resolve("Extrato Bancário.pdf")))
        assertFalse(Files.exists(source))

        assertIs<Outcome.Success<*>>(undo(operation.id))
        assertTrue(Files.exists(source))
        assertFalse(Files.exists(extratoFolder.resolve("Extrato Bancário.pdf")))
        assertTrue(history.operations.single().undone)
    }

    @Test
    fun `subcategoria cria a estrutura de pastas completa`() = runTest {
        val source = inbox.createFakePdf("comprovante.pdf")
        val proposal = planFor(source, "congregacao.comprovante_remessa")

        organizeDone(proposal)

        val expected = root.resolve("Conta da Congregação").resolve("5. Remessa Betel").resolve("5.1 Comprovante Remessa")
        assertTrue(Files.exists(expected.resolve("Comprovante Remessa.pdf")))
    }

    @Test
    fun `conflito com perguntar sempre nao altera nada sem escolha`() = runTest {
        extratoFolder.createFakePdf("Extrato Bancário.pdf", content = "original")
        val source = inbox.createFakePdf("novo.pdf")
        val proposal = planFor(source, "congregacao.extrato_bancario")

        assertTrue(proposal.hasConflict)
        assertEquals("Extrato Bancário (2).pdf", proposal.numberedCopyName)
        assertEquals(OrganizeResult.NeedsConflictChoice, (organize(proposal) as Outcome.Success).value)
        assertTrue(Files.exists(source))
    }

    @Test
    fun `copia numerada preserva o arquivo existente`() = runTest {
        val existing = extratoFolder.createFakePdf("Extrato Bancário.pdf", content = "original")
        val source = inbox.createFakePdf("novo.pdf")

        organizeDone(planFor(source, "congregacao.extrato_bancario"), DuplicateResolution.NUMBERED_COPY)

        assertTrue(Files.readString(existing).contains("original"))
        assertTrue(Files.exists(extratoFolder.resolve("Extrato Bancário (2).pdf")))
    }

    @Test
    fun `substituir guarda backup e desfazer restaura os dois arquivos`() = runTest {
        val existing = extratoFolder.createFakePdf("Extrato Bancário.pdf", content = "original")
        val source = inbox.createFakePdf("novo.pdf", content = "novo")

        val operation = organizeDone(planFor(source, "congregacao.extrato_bancario"), DuplicateResolution.REPLACE)
        assertTrue(Files.readString(existing).contains("novo"))

        assertIs<Outcome.Success<*>>(undo(operation.id))
        assertTrue(Files.readString(existing).contains("original"))
        assertTrue(Files.readString(source).contains("novo"))
    }

    @Test
    fun `nao permitir duplicados bloqueia a operacao`() = runTest {
        settings.save(AppSettings(rootPath = root, duplicatePolicy = DuplicatePolicy.FORBID))
        extratoFolder.createFakePdf("Extrato Bancário.pdf")
        val source = inbox.createFakePdf("novo.pdf")

        val result = organize(planFor(source, "congregacao.extrato_bancario"), DuplicateResolution.REPLACE)

        assertEquals(Outcome.Failure(FileOperationError.DuplicatesNotAllowed), result)
        assertTrue(Files.exists(source))
    }

    @Test
    fun `outros recebe a proxima numeracao livre`() = runTest {
        val outrosFolder = root.resolve("Outros")
        outrosFolder.createFakePdf("1. Outros.pdf")
        outrosFolder.createFakePdf("2. Outros.pdf")
        val source = inbox.createFakePdf("qualquer.pdf")

        val proposal = planFor(source, "outros")

        assertEquals("3. Outros.pdf", proposal.suggestedName)
        assertFalse(proposal.hasConflict)
    }

    @Test
    fun `renomear mantem o arquivo na pasta atual`() = runTest {
        val source = inbox.createFakePdf("xxx.pdf")

        organizeDone(planFor(source, "congregacao.extrato_bancario", OrganizeMode.RENAME_ONLY))

        assertTrue(Files.exists(inbox.resolve("Extrato Bancário.pdf")))
    }

    @Test
    fun `documento ja organizado nao gera proposta`() = runTest {
        val source = extratoFolder.createFakePdf("Extrato Bancário.pdf")

        val result = plan(source, "congregacao.extrato_bancario", OrganizeMode.RENAME_AND_MOVE)

        assertEquals(Outcome.Failure(FileOperationError.AlreadyInPlace), result)
    }

    @Test
    fun `arquivo que nao e pdf e recusado`() = runTest {
        Files.createDirectories(inbox)
        val text = inbox.resolve("nota.txt").also { Files.writeString(it, "texto") }

        val result = plan(text, "congregacao.extrato_bancario", OrganizeMode.RENAME_AND_MOVE)

        assertEquals(Outcome.Failure(FileOperationError.InvalidPdf), result)
    }

    @Test
    fun `desfazer e bloqueado se o local original foi ocupado`() = runTest {
        val source = inbox.createFakePdf("xxx.pdf")
        val operation = organizeDone(planFor(source, "congregacao.extrato_bancario"))
        inbox.createFakePdf("xxx.pdf", content = "outro arquivo")

        val result = undo(operation.id)

        assertEquals(Outcome.Failure(FileOperationError.UndoNotPossible(UndoBlockReason.ORIGINAL_LOCATION_OCCUPIED)), result)
        assertTrue(Files.exists(extratoFolder.resolve("Extrato Bancário.pdf")))
    }

    @Test
    fun `desfazer duas vezes e bloqueado`() = runTest {
        val source = inbox.createFakePdf("xxx.pdf")
        val operation = organizeDone(planFor(source, "congregacao.extrato_bancario"))
        undo(operation.id)

        val result = undo(operation.id)

        assertEquals(Outcome.Failure(FileOperationError.UndoNotPossible(UndoBlockReason.ALREADY_UNDONE)), result)
    }

    private suspend fun planFor(
        source: Path,
        categoryId: String,
        mode: OrganizeMode = OrganizeMode.RENAME_AND_MOVE,
    ): OrganizationPlan = (plan(source, categoryId, mode) as Outcome.Success).value

    private suspend fun organizeDone(proposal: OrganizationPlan, resolution: DuplicateResolution? = null) =
        ((organize(proposal, resolution) as Outcome.Success).value as OrganizeResult.Done).operation
}
