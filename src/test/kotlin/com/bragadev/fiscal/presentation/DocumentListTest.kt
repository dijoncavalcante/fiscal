package com.bragadev.fiscal.presentation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.rules.DocumentSorting
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.home.*
import org.junit.Test
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals

/** Lista de documentos com mouse e teclado simulados: menu do botão direito, F2, Ctrl+C e Delete. */
@OptIn(ExperimentalTestApi::class)
class DocumentListTest {
    private val downloads = Path.of("C:", "Downloads")
    private val extrato = Document(downloads.resolve("Extrato junho.pdf"), sizeBytes = 10, lastModified = Instant.parse("2026-06-10T10:00:00Z"))
    private val foto = Document(downloads.resolve("Recibo.jpg"), sizeBytes = 10, lastModified = Instant.parse("2026-06-09T10:00:00Z"))

    private val monthFile = Document(Path.of("D:", "pendriver", "1. JUNHO", "8. Extrato Bancário.pdf"), sizeBytes = 0, lastModified = Instant.EPOCH)

    private val calls =mutableListOf<String>()
    private val actions = DocumentActions(
        onRename = { calls += "renomear ${it.fileName}" },
        onCopy = { calls += "copiar ${it.fileName}" },
        onDelete = { calls += "excluir ${it.fileName}" },
        onSendWhatsApp = { calls += "whatsapp ${it.fileName}" },
    )

    private fun listTest(selected: Document? = null, block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) = runComposeUiTest {
        val documents = listOf(extrato, foto)
        val state = HomeUiState(
            sourceFolder = downloads.toString(),
            folderStatus = FolderStatus.READY,
            documents = documents,
            visibleDocuments = DocumentSorting.apply(documents, HomeUiState().sort, ""),
            selectedDocument = selected,
        )
        setContent {
            DocumentList(
                state = state,
                onSelect = { calls += "selecionar ${it.name}" },
                actions = actions,
                onChangeFolder = {},
                onRefresh = {},
                onSortChanged = {},
                onQueryChanged = {},
            )
        }
        waitForIdle()
        block()
    }

    @Test
    fun `botao direito seleciona o documento e abre o menu com renomear, copiar e excluir`() = listTest {
        onNodeWithText("Recibo.jpg").performMouseInput { rightClick() }
        waitForIdle()

        assertEquals(listOf("selecionar Recibo.jpg"), calls)
        onNodeWithText(Strings.RENAME).assertIsDisplayed()
        onNodeWithText(Strings.COPY_FILE).assertIsDisplayed()
        onNodeWithText(Strings.SEND_WHATSAPP).assertIsDisplayed()
        onNodeWithText(Strings.DELETE_FILE).performClick()
        waitForIdle()

        assertEquals("excluir Recibo.jpg", calls.last())
    }

    @Test
    fun `atalhos agem sobre o documento selecionado com a lista focada`() = listTest(selected = extrato) {
        onNodeWithText("Extrato junho.pdf").performClick()
        waitForIdle()
        val list = onNodeWithTag(DOCUMENT_LIST_TAG)
        list.performKeyInput { pressKey(Key.F2) }
        list.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.C) } }
        list.performKeyInput { pressKey(Key.Delete) }
        waitForIdle()

        assertEquals(
            listOf("selecionar Extrato junho.pdf", "renomear Extrato junho.pdf", "copiar Extrato junho.pdf", "excluir Extrato junho.pdf"),
            calls,
        )
    }

    @Test
    fun `arquivo do mes aberto no preview nao e excluido pelo Delete da lista`() = listTest(selected = monthFile) {
        onNodeWithText("Extrato junho.pdf").performClick()
        waitForIdle()
        onNodeWithTag(DOCUMENT_LIST_TAG).performKeyInput { pressKey(Key.Delete) }
        waitForIdle()

        // O clique foi registrado, mas a seleção continua sendo o arquivo do mês (fora desta lista).
        assertEquals(listOf("selecionar Extrato junho.pdf"), calls)
    }
}
