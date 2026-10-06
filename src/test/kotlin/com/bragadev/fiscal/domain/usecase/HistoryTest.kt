package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.FileOperationError
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
import com.bragadev.fiscal.presentation.history.HistoryViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tela de histórico: desfazer qualquer operação válida, não só a última. */
class HistoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val june: Path by lazy {
        temp.newFolder("pendriver").toPath().resolve("CONTAS MANUTENÇÃO").resolve("ANO DE SERVIÇO 2025-2026")
            .resolve("4. TRIMESTRE Jun-Jul-Ago").resolve("1. JUNHO").also(Files::createDirectories)
    }
    private val fileRepository = FileRepositoryImpl()
    private val history = InMemoryHistoryRepository()
    private val flags = InMemoryFlagRepository()
    private var cutoff = YearMonth.of(2026, 6)
    private val policy = EditablePeriodPolicy { cutoff }
    private val settings by lazy { FakeSettingsRepository(AppSettings(monthFolder = june)) }
    private val mover by lazy { RecordedFileMover(fileRepository, history, NoOpDocumentRepository(), flags) }
    private val organize by lazy {
        OrganizeDocumentUseCase(fileRepository, mover, TempBackupStorage(temp.newFolder("backup").toPath()), settings, policy)
    }
    private val planRename by lazy { PlanRenameUseCase(fileRepository, FakeCategoryRepository(), policy) }
    private val undo by lazy { UndoOperationUseCase(fileRepository, history, NoOpDocumentRepository(), flags, policy) }
    private val listHistory by lazy { ListHistoryUseCase(history, undo) }

    private suspend fun rename(file: Path, newName: String): FileOperation {
        val plan = (planRename(file, "manutencao.relatorio_mensal", newName) as Outcome.Success).value
        return ((organize(plan) as Outcome.Success).value as OrganizeResult.Done).operation
    }

    @Test
    fun `desfaz uma operacao antiga sem desfazer as mais novas`() = runTest {
        val first = rename(june.createFakePdf("relatorio.pdf"), "4. Relatório Mensal")
        rename(june.createFakePdf("extrato.pdf"), "5. Extrato Bancário")

        assertIs<Outcome.Success<*>>(undo(first.id))

        assertTrue(Files.exists(june.resolve("relatorio.pdf")))
        assertTrue(Files.exists(june.resolve("5. Extrato Bancário.pdf")), "a operação mais nova continua feita")
    }

    @Test
    fun `operacao cujo arquivo foi mexido depois nao pode ser desfeita, a mais nova pode`() = runTest {
        val first = rename(june.createFakePdf("relatorio.pdf"), "4. Relatório Mensal")
        val second = rename(june.resolve("4. Relatório Mensal.pdf"), "4. Relatório Mensal (S-30)")

        val entries = listHistory()
        assertEquals(listOf(second.id, first.id), entries.map { it.operation.id }, "mais recente primeiro")
        assertTrue(entries[0].canUndo)
        assertEquals(FileOperationError.UndoNotPossible(UndoBlockReason.CURRENT_FILE_MISSING), entries[1].undoBlock)
        assertIs<Outcome.Failure>(undo(first.id))

        // Desfeita a mais nova, a antiga volta a poder ser desfeita.
        undo(second.id)
        val after = listHistory()
        assertEquals(FileOperationError.UndoNotPossible(UndoBlockReason.ALREADY_UNDONE), after[0].undoBlock)
        assertNull(after[1].undoBlock)
    }

    @Test
    fun `mes que ficou fechado nao deixa desfazer`() = runTest {
        val operation = rename(june.createFakePdf("relatorio.pdf"), "4. Relatório Mensal")
        cutoff = YearMonth.of(2026, 7)

        assertIs<FileOperationError.MonthLocked>(listHistory().single().undoBlock)
        assertIs<Outcome.Failure>(undo(operation.id))
        assertTrue(Files.exists(june.resolve("4. Relatório Mensal.pdf")))
    }

    @Test
    fun `busca ignora maiusculas e acentos e aceita data`() = runTest {
        rename(june.createFakePdf("relatorio.pdf"), "4. Relatório Mensal")
        rename(june.createFakePdf("extrato.pdf"), "5. Extrato Bancário")
        val entries = listHistory()

        assertEquals(listOf("4. Relatório Mensal.pdf"), HistoryViewModel.filter(entries, "RELATORIO").map { it.operation.newName })
        assertEquals(listOf("5. Extrato Bancário.pdf"), HistoryViewModel.filter(entries, "bancario junho").map { it.operation.newName })
        assertEquals(2, HistoryViewModel.filter(entries, HistoryViewModel.formatDate(entries[0]).take(10)).size)
        assertEquals(2, HistoryViewModel.filter(entries, "  ").size)
        assertTrue(HistoryViewModel.filter(entries, "despesa").isEmpty())
    }
}
