package com.bragadev.fiscal.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.rules.DocumentSorting
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.CopyButton
import com.bragadev.fiscal.presentation.home.*
import com.bragadev.fiscal.presentation.organizer.MONTH_SCROLLBAR_TAG
import com.bragadev.fiscal.presentation.organizer.MonthFileActions
import com.bragadev.fiscal.presentation.organizer.OrganizerScreen
import com.bragadev.fiscal.presentation.organizer.OrganizerUiState
import org.junit.Test
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals

/** Botão de copiar (caminho/nome) e barras de rolagem de Documentos e Mês em edição, com mouse simulado. */
@OptIn(ExperimentalTestApi::class)
class CopyAndScrollTest {
    private val downloads = Path.of("C:", "Downloads")

    @Test
    fun `copiar coloca o texto na area de transferencia e mostra Copiado`() = runComposeUiTest {
        val copied = mutableListOf<String>()
        val path = """D:\Modelo\jw\pendriver\CONTAS CONGREGAÇÃO"""
        setContent { CopyButton(path, Strings.COPY_PATH, copy = { copied += it }) }

        onNodeWithContentDescription(Strings.COPY_PATH).performClick()
        waitForIdle()

        assertEquals(listOf(path), copied)
        onNodeWithContentDescription(Strings.COPIED).assertIsDisplayed()
    }

    @Test
    fun `arrastar a barra de rolagem leva ao fim da lista de documentos`() = runComposeUiTest {
        val documents =
            (1..60).map { Document(downloads.resolve("Comprovante %02d.pdf".format(it)), 10, Instant.EPOCH) }
        val state = HomeUiState(
            sourceFolder = downloads.toString(),
            folderStatus = FolderStatus.READY,
            documents = documents,
            visibleDocuments = DocumentSorting.apply(documents, com.bragadev.fiscal.domain.model.DocumentSort.NAME, ""),
        )
        setContent {
            Box(Modifier.width(400.dp).height(500.dp)) {
                DocumentList(state, {}, DocumentActions({}, {}, {}), {}, {}, {}, {})
            }
        }
        waitForIdle()
        onNodeWithText("Comprovante 60.pdf").assertDoesNotExistOrHidden()

        dragScrollbarToBottom(DOCUMENT_SCROLLBAR_TAG)

        onNodeWithText("Comprovante 60.pdf").assertIsDisplayed()
    }

    @Test
    fun `arrastar a barra de rolagem leva ao fim do mes em edicao`() = runComposeUiTest {
        val noop = MonthFileActions({}, { _, _ -> }, {}, {}, {}, { _, _ -> }, {})
        setContent {
            Box(Modifier.width(400.dp).height(500.dp)) {
                OrganizerScreen(
                    state = OrganizerUiState(),
                    selectedDocument = null,
                    onDrop = { _, _ -> },
                    onCategoryClickedWithoutDocument = {},
                    fileActions = noop,
                    navigator = { Column { (1..60).forEach { Text("Linha $it") } } },
                )
            }
        }
        waitForIdle()
        onNodeWithText("Linha 60").assertDoesNotExistOrHidden()

        dragScrollbarToBottom(MONTH_SCROLLBAR_TAG)

        onNodeWithText("Linha 60").assertIsDisplayed()
    }

    private fun ComposeUiTest.dragScrollbarToBottom(tag: String) {
        onNodeWithTag(tag).performMouseInput {
            moveTo(Offset(width / 2f, 6f))
            press()
            moveTo(Offset(width / 2f, height / 2f))
            moveTo(Offset(width / 2f, height + 200f))
            release()
        }
        waitForIdle()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistOrHidden() {
        runCatching { assertIsNotDisplayed() }.recoverCatching { assertDoesNotExist() }.getOrThrow()
    }
}
