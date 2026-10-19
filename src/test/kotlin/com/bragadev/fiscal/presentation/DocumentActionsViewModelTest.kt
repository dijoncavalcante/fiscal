package com.bragadev.fiscal.presentation

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.usecase.DeleteDocumentUseCase
import com.bragadev.fiscal.domain.usecase.RecordedFileMover
import com.bragadev.fiscal.domain.usecase.RenameDocumentUseCase
import com.bragadev.fiscal.fakes.*
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.common.DocumentsChanged
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.WhatsAppTarget
import com.bragadev.fiscal.presentation.documentactions.DocumentActionDialog
import com.bragadev.fiscal.presentation.documentactions.DocumentActionsUiState
import com.bragadev.fiscal.presentation.documentactions.DocumentActionsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.YearMonth
import kotlin.test.*

/** Ações do botão direito: o que o usuário vê (diálogo, mensagem com Desfazer) e o aviso para a lista. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentActionsViewModelTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val notifier = DocumentChangeNotifier()
    private val copied = mutableListOf<List<Path>>()
    private var clipboardWorks = true
    private var whatsAppTarget = WhatsAppTarget.WEB
    private var whatsAppOpened = 0
    private lateinit var downloads: Path
    private lateinit var viewModel: DocumentActionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        downloads = temp.newFolder("Downloads").toPath()
        val files = FileRepositoryImpl()
        val policy = EditablePeriodPolicy(YearMonth.of(2026, 6))
        val mover = RecordedFileMover(files, InMemoryHistoryRepository(), NoOpDocumentRepository(), InMemoryFlagRepository())
        val backups = TempBackupStorage(temp.newFolder("backup").toPath(), temp.root.toPath().resolve("lixeira"))
        viewModel = DocumentActionsViewModel(
            RenameDocumentUseCase(files, mover, policy),
            DeleteDocumentUseCase(files, backups, mover, policy),
            notifier,
            copyFiles = { copied += it; clipboardWorks },
            openWhatsApp = { whatsAppOpened++; whatsAppTarget },
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun awaitState(condition: (DocumentActionsUiState) -> Boolean): DocumentActionsUiState =
        runBlocking { withTimeout(5_000) { viewModel.uiState.first(condition) } }

    @Test
    fun `copiar coloca o arquivo na area de transferencia e avisa como colar`() {
        val file = downloads.resolve("Extrato junho.pdf")

        viewModel.onCopyRequested(file)
        assertEquals(listOf(listOf(file)), copied)
        assertEquals(Strings.FILE_COPIED, viewModel.uiState.value.message?.text)

        clipboardWorks = false
        viewModel.onCopyRequested(file)
        assertTrue(viewModel.uiState.value.message!!.isError)
    }

    @Test
    fun `enviar pelo whatsapp copia o arquivo, abre o WhatsApp e explica como colar`() {
        val file = downloads.resolve("8. Extrato Bancário.pdf")

        viewModel.onSendWhatsAppRequested(file)
        assertEquals(listOf(listOf(file)), copied)
        assertEquals(Strings.WHATSAPP_WEB_OPENED, awaitState { it.message != null }.message?.text)
        assertEquals(1, whatsAppOpened)

        viewModel.onMessageShown()
        whatsAppTarget = WhatsAppTarget.APP
        viewModel.onSendWhatsAppRequested(file)
        assertEquals(Strings.WHATSAPP_APP_OPENED, awaitState { it.message != null }.message?.text)

        viewModel.onMessageShown()
        whatsAppTarget = WhatsAppTarget.NONE
        viewModel.onSendWhatsAppRequested(file)
        assertEquals(Strings.WHATSAPP_NOT_OPENED, awaitState { it.message != null }.message?.text)
    }

    @Test
    fun `sem conseguir copiar o WhatsApp nem abre`() {
        clipboardWorks = false
        viewModel.onSendWhatsAppRequested(downloads.resolve("scan.pdf"))

        assertTrue(viewModel.uiState.value.message!!.isError)
        assertEquals(0, whatsAppOpened)
    }

    @Test
    fun `excluir pede confirmacao, oferece desfazer e passa a selecao para o vizinho`() = runBlocking {
        val file = downloads.createFakePdf("scan001.pdf")
        val neighbor = downloads.createFakePdf("scan002.pdf")
        val changed = async(start = CoroutineStart.UNDISPATCHED) { notifier.changes.first() }

        viewModel.onDeleteRequested(file, selectAfter = neighbor)
        assertIs<DocumentActionDialog.Delete>(awaitState { it.dialog != null }.dialog)
        assertTrue(Files.exists(file), "nada muda antes de confirmar")

        viewModel.onDeleteConfirmed()
        val done = awaitState { it.message != null }

        assertEquals(Strings.FILE_DELETED, done.message?.text)
        assertNotNull(done.message?.undoOperationId)
        assertEquals(null, done.dialog)
        assertFalse(Files.exists(file))
        assertEquals(DocumentsChanged(selectPath = neighbor, removedPath = file), withTimeout(5_000) { changed.await() })
    }

    @Test
    fun `renomear comeca com o nome sem extensao e so libera quando muda`() = runBlocking {
        val photo = Files.writeString(downloads.resolve("WhatsApp Image 2026-06-03.jpg"), "foto")

        viewModel.onRenameRequested(photo)
        val dialog = viewModel.uiState.value.dialog as DocumentActionDialog.Rename
        assertEquals("WhatsApp Image 2026-06-03", dialog.name)
        assertFalse(dialog.canConfirm)

        viewModel.onRenameNameChanged("Recibo da luz")
        viewModel.onRenameConfirmed()
        awaitState { it.message != null }

        assertTrue(Files.exists(downloads.resolve("Recibo da luz.jpg")))
        assertEquals(Strings.FILE_RENAMED, viewModel.uiState.value.message?.text)
    }

    @Test
    fun `renomear para nome ocupado mostra o aviso no proprio dialogo`() = runBlocking {
        val file = downloads.createFakePdf("scan001.pdf")
        downloads.createFakePdf("Extrato.pdf")

        viewModel.onRenameRequested(file)
        viewModel.onRenameNameChanged("extrato")
        viewModel.onRenameConfirmed()
        val state = awaitState { (it.dialog as? DocumentActionDialog.Rename)?.inputError != null }

        assertEquals(Strings.NAME_TAKEN, (state.dialog as DocumentActionDialog.Rename).inputError)
        assertTrue(Files.exists(file))
    }
}
