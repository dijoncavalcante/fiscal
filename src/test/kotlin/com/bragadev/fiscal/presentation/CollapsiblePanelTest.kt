package com.bragadev.fiscal.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.*
import org.junit.Test

/** Recolher e mostrar os painéis Documentos (para a esquerda) e Mês em edição (para a direita), com mouse simulado. */
@OptIn(ExperimentalTestApi::class)
class CollapsiblePanelTest {
    private fun collapseTest(title: String, side: PanelSide) = runComposeUiTest {
        setContent {
            var collapsed by remember { mutableStateOf(false) }
            Row(Modifier.width(600.dp).height(400.dp)) {
                if (collapsed) {
                    CollapsedPanel(title, side, onExpand = { collapsed = false })
                } else {
                    Panel(title, Modifier.width(300.dp).fillMaxHeight(), PanelCollapse(side) { collapsed = true }) {
                        Text("conteúdo do painel")
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("conteúdo do painel").assertIsDisplayed()

        onNodeWithContentDescription(Strings.collapsePanel(title)).performClick()
        waitForIdle()
        onNodeWithText("conteúdo do painel").assertDoesNotExist()
        onNodeWithTag(COLLAPSED_PANEL_TAG).assertIsDisplayed()

        onNodeWithContentDescription(Strings.expandPanel(title)).performClick()
        waitForIdle()
        onNodeWithText("conteúdo do painel").assertIsDisplayed()
        onNodeWithTag(COLLAPSED_PANEL_TAG).assertDoesNotExist()
    }

    @Test
    fun `documentos recolhe para a esquerda e volta`() = collapseTest(Strings.DOCUMENTS, PanelSide.LEFT)

    @Test
    fun `mes em edicao recolhe para a direita e volta`() = collapseTest(Strings.MONTH_PANEL, PanelSide.RIGHT)

    @Test
    fun `clicar na faixa recolhida tambem mostra o painel`() = runComposeUiTest {
        var expanded = false
        setContent { CollapsedPanel(Strings.DOCUMENTS, PanelSide.LEFT, onExpand = { expanded = true }) }
        onNodeWithTag(COLLAPSED_PANEL_TAG).performClick()
        waitForIdle()
        kotlin.test.assertTrue(expanded)
    }
}
