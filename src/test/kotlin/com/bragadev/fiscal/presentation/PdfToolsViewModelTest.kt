package com.bragadev.fiscal.presentation

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.data.pdf.PdfToolsRepositoryImpl
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.usecase.CreatePdfFromImagesUseCase
import com.bragadev.fiscal.domain.usecase.MergePdfsUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.PdfOutputResolver
import com.bragadev.fiscal.fakes.FakeSettingsRepository
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.pdftools.PdfToolsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import java.time.YearMonth
import kotlin.test.assertEquals

/** Painel de PDF: arquivos arrastados para a lista e reordenação por arrastar. */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfToolsViewModelTest {
    private val downloads = Path.of("C:", "Downloads")
    private lateinit var viewModel: PdfToolsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val files = FileRepositoryImpl()
        val resolver = PdfOutputResolver(files, EditablePeriodPolicy(YearMonth.of(2026, 6)))
        val tools = PdfToolsRepositoryImpl()
        viewModel = PdfToolsViewModel(
            CreatePdfFromImagesUseCase(files, tools, resolver),
            MergePdfsUseCase(files, tools, resolver),
            ObserveSettingsUseCase(FakeSettingsRepository(AppSettings(sourceFolder = downloads))),
            DocumentChangeNotifier(),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun names() = viewModel.uiState.value.dialog!!.items.map { it.path.fileName.toString() }

    @Test
    fun `arquivos arrastados entram na lista e os de outro tipo sao ignorados`() {
        viewModel.onOpenImagesToPdf()

        viewModel.onFilesAdded(listOf(downloads.resolve("a.jpg"), downloads.resolve("nota.pdf"), downloads.resolve("b.PNG")))

        assertEquals(listOf("a.jpg", "b.PNG"), names())
        assertEquals(Strings.ONLY_IMAGES_ACCEPTED, viewModel.uiState.value.dialog!!.error)
        assertEquals("a", viewModel.uiState.value.dialog!!.outputName)
        assertEquals(downloads, viewModel.uiState.value.dialog!!.outputFolder)
    }

    @Test
    fun `mesmo arquivo nao entra duas vezes`() {
        viewModel.onOpenMerge(downloads.resolve("a.pdf"))

        viewModel.onFilesAdded(listOf(downloads.resolve("a.pdf"), downloads.resolve("b.pdf")))

        assertEquals(listOf("a.pdf", "b.pdf"), names())
        assertEquals(Strings.ALREADY_IN_LIST, viewModel.uiState.value.dialog!!.error)
    }

    @Test
    fun `arrastar muda a ordem e o selecionado acompanha o item`() {
        viewModel.onOpenMerge(null)
        viewModel.onFilesAdded(listOf(downloads.resolve("1.pdf"), downloads.resolve("2.pdf"), downloads.resolve("3.pdf")))

        viewModel.onMove(0, 1)
        viewModel.onMove(1, 2)

        assertEquals(listOf("2.pdf", "3.pdf", "1.pdf"), names())
        assertEquals(2, viewModel.uiState.value.dialog!!.selectedIndex)
        viewModel.onMove(0, 5)
        assertEquals(listOf("2.pdf", "3.pdf", "1.pdf"), names())
    }
}
