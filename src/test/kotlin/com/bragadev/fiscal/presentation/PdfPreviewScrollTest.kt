package com.bragadev.fiscal.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.data.pdf.PdfRepositoryImpl
import com.bragadev.fiscal.domain.usecase.PreviewPdfUseCase
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.preview.PREVIEW_HORIZONTAL_SCROLLBAR_TAG
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

    private fun ComposeUiTest.pagesLeft(): Float =
        onNodeWithTag(PREVIEW_PAGES_TAG).fetchSemanticsNode().positionInRoot.x

    @Test
    fun `com zoom maior que o painel da para ir de um lado para o outro`() = runComposeUiTest {
        val viewModel = PdfPreviewViewModel(PreviewPdfUseCase(PdfRepositoryImpl()))
        val pdf = pdfWithPages(2)
        setContent { Box(Modifier.width(500.dp).height(600.dp)) { PdfPreviewScreen(viewModel, pdf) } }
        waitForText(Strings.pageOf(1, 2))

        repeat(6) { onNodeWithText(Strings.ZOOM_IN).performClick(); waitForIdle() }
        waitUntil(timeoutMillis = 10_000) { viewModel.uiState.value.maxPageWidthDp > 700f }
        waitForIdle()
        val start = pagesLeft()

        // Arrastando a barra de rolagem horizontal para a direita, a página anda para a esquerda.
        onNodeWithTag(PREVIEW_HORIZONTAL_SCROLLBAR_TAG).performMouseInput {
            moveTo(Offset(10f, centerY)); press(); moveBy(Offset(200f, 0f)); release()
        }
        waitForIdle()
        val afterBar = pagesLeft()
        assertTrue(afterBar < start - 50f, "barra horizontal não rolou: $start -> $afterBar")

        // Arrastando a própria página para a direita, ela volta.
        onNodeWithTag(PREVIEW_PAGES_TAG).performMouseInput {
            moveTo(Offset(100f, 100f)); press(); moveBy(Offset(150f, 0f)); release()
        }
        waitForIdle()
        assertTrue(pagesLeft() > afterBar + 50f, "arrastar a página não rolou")

        // Shift + roda do mouse também rola para os lados.
        val beforeWheel = pagesLeft()
        onNodeWithTag(PREVIEW_PAGES_TAG).performMouseInput { moveTo(center); scroll(10f, ScrollWheel.Horizontal) }
        waitForIdle()
        assertTrue(pagesLeft() < beforeWheel, "roda horizontal não rolou")
        viewModel.clear()
    }
}
