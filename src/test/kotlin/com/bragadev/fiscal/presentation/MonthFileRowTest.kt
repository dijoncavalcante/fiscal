package com.bragadev.fiscal.presentation

import androidx.compose.ui.test.*
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.organizer.MonthFileActions
import com.bragadev.fiscal.presentation.organizer.MonthFileRow
import org.junit.Test
import java.nio.file.Path
import kotlin.test.assertEquals

/** Arquivo do mês em edição com mouse simulado: menu do botão direito com renomear, copiar e excluir. */
@OptIn(ExperimentalTestApi::class)
class MonthFileRowTest {
    private val june = Path.of(
        "D:",
        "pendriver",
        "CONTAS CONGREGAÇÃO",
        "ANO DE SERVIÇO 2025-2026",
        "4. TRIMESTRE Jun-Jul-Ago",
        "1. JUNHO"
    )
    private val despesa = june.resolve("3. Despesa - Material de limpeza.pdf")

    private val calls = mutableListOf<String>()
    private val actions = MonthFileActions(
        onPreview = { calls += "preview ${it.fileName}" },
        onRename = { file, category -> calls += "renomear ${file.fileName} (${category?.id ?: "sem categoria"})" },
        onCopy = { calls += "copiar ${it.fileName}" },
        onDelete = { calls += "excluir ${it.fileName}" },
        onRemove = { calls += "retirar ${it.fileName}" },
        onFlag = { file, _ -> calls += "pendencia ${file.fileName}" },
        onClearFlag = {},
        onSendWhatsApp = { calls += "whatsapp ${it.fileName}" },
    )

    private fun rowTest(category: DocumentCategory?, editable: Boolean = true, block: ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                MonthFileRow(
                    file = despesa,
                    category = category,
                    issue = null,
                    editable = editable,
                    actions = actions
                )
            }
            waitForIdle()
            block()
        }

    private fun ComposeUiTest.rightClickFile() {
        onNodeWithText(despesa.fileName.toString()).performMouseInput { rightClick() }
        waitForIdle()
    }

    private fun ComposeUiTest.menuItem(text: String) =
        onNode(hasText(text) and hasClickAction(), useUnmergedTree = false)

    @Test
    fun `botao direito mostra no preview e abre o menu com renomear, copiar e excluir`() = rowTest(category = null) {
        rightClickFile()

        assertEquals(listOf("preview ${despesa.fileName}"), calls)
        menuItem(Strings.RENAME).assertIsDisplayed()
        menuItem(Strings.REMOVE_FROM_MONTH).assertIsDisplayed()
        menuItem(Strings.COPY_FILE).performClick()
        waitForIdle()
        assertEquals("copiar ${despesa.fileName}", calls.last())

        rightClickFile()
        menuItem(Strings.SEND_WHATSAPP).performClick()
        waitForIdle()
        assertEquals("whatsapp ${despesa.fileName}", calls.last())

        rightClickFile()
        menuItem(Strings.DELETE_FILE).performClick()
        waitForIdle()
        assertEquals("excluir ${despesa.fileName}", calls.last())

        rightClickFile()
        menuItem(Strings.RENAME).performClick()
        waitForIdle()
        assertEquals("renomear ${despesa.fileName} (sem categoria)", calls.last())
    }

    @Test
    fun `mes bloqueado so deixa copiar`() = rowTest(category = null, editable = false) {
        rightClickFile()

        menuItem(Strings.RENAME).assertIsNotEnabled()
        menuItem(Strings.DELETE_FILE).assertIsNotEnabled()
        menuItem(Strings.REMOVE_FROM_MONTH).assertIsNotEnabled()
        menuItem(Strings.COPY_FILE).performClick()
        waitForIdle()
        assertEquals("copiar ${despesa.fileName}", calls.last())
    }
}
