package com.bragadev.fiscal.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.data.pdf.PdfRepositoryImpl
import com.bragadev.fiscal.domain.usecase.PreviewPdfUseCase
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.preview.PREVIEW_PAGES_TAG
import com.bragadev.fiscal.presentation.preview.PdfPreviewScreen
import com.bragadev.fiscal.presentation.preview.PdfPreviewViewModel
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.test.assertTrue

/** Preview com rolagem contínua: a roda do mouse percorre o PDF inteiro, para baixo e para cima, sem botões. */
@OptIn(ExperimentalTestApi::class)
class PdfPreviewScrollTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun pdfWithPages(pages: Int): Path = temp.root.toPath().resolve("8. Extrato Bancário.pdf").also { file ->
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.save(file.toFile())
        }
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.scrollWheel(steps: Int, delta: Float) {
        repeat(steps) {
            onNodeWithTag(PREVIEW_PAGES_TAG).performMouseInput { moveTo(center); scroll(delta, ScrollWheel.Vertical) }
            waitForIdle()
        }
    }

    @Test
    fun `rolar com o mouse passa por todas as paginas e volta ao topo`() = runComposeUiTest {
        val viewModel = PdfPreviewViewModel(PreviewPdfUseCase(PdfRepositoryImpl()))
        val pdf = pdfWithPages(12)
        setContent { Box(Modifier.width(500.dp).height(600.dp)) { PdfPreviewScreen(viewModel, pdf) } }

        waitForText(Strings.pageOf(1, 12))

        scrollWheel(steps = 150, delta = 10f)
        waitForText(Strings.pageOf(12, 12))

        // A última página foi desenhada (a imagem só existe para páginas perto da tela).
        waitUntil(timeoutMillis = 10_000) { viewModel.uiState.value.pages.last().image != null }
        // As páginas do começo, longe da tela, foram liberadas da memória.
        assertTrue(viewModel.uiState.value.pages.first().image == null)

        scrollWheel(steps = 150, delta = -10f)
        waitForText(Strings.pageOf(1, 12))
        waitUntil(timeoutMillis = 10_000) { viewModel.uiState.value.pages.first().image != null }
        viewModel.clear()
    }
}
