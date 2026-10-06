package com.bragadev.fiscal.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.components.ReorderableColumn
import org.junit.Test
import kotlin.test.assertEquals

/** Arrasta itens com o mouse numa lista real, como o usuário faz no painel "Juntar PDFs". */
@OptIn(ExperimentalTestApi::class)
class ReorderableColumnTest {
    private val rowHeight = 40.dp

    private fun dragTest(start: List<String>, dragTag: String, rows: Float, expected: List<String>) =
        dragTest(start, dragTag, listOf(rows to 20), expected)

    /** [moves]: trechos do arraste, cada um em (linhas, quantidade de passos do mouse). */
    private fun dragTest(start: List<String>, dragTag: String, moves: List<Pair<Float, Int>>, expected: List<String>) = runComposeUiTest {
        var order by mutableStateOf(start)
        setContent {
            val items = remember { mutableStateOf(start) }
            order = items.value
            ReorderableColumn(
                items = items.value,
                key = { it },
                itemHeight = rowHeight,
                onMove = { from, to -> items.value = items.value.toMutableList().apply { add(to, removeAt(from)) } },
                modifier = Modifier.width(300.dp),
            ) { _, item, _ ->
                Text(item, Modifier.testTag(item).fillMaxSize())
            }
        }
        val rowPx = with(density) { rowHeight.toPx() }
        onNodeWithTag(dragTag).performMouseInput {
            moveTo(center)
            press()
            // Movimentos pequenos, como a mão do usuário.
            moves.forEach { (rows, steps) -> repeat(steps) { moveBy(Offset(0f, rows * rowPx / steps)) } }
            release()
        }
        waitForIdle()
        assertEquals(expected, order)
    }

    @Test
    fun `arrastar o primeiro para o fim`() = dragTest(listOf("a", "b", "c", "d"), "a", rows = 3f, expected = listOf("b", "c", "d", "a"))

    @Test
    fun `arrastar o ultimo para o inicio`() = dragTest(listOf("a", "b", "c", "d"), "d", rows = -3f, expected = listOf("d", "a", "b", "c"))

    @Test
    fun `arrastar uma posicao`() = dragTest(listOf("a", "b", "c"), "b", rows = 1f, expected = listOf("a", "c", "b"))

    @Test
    fun `arrastar pouco nao muda a ordem`() = dragTest(listOf("a", "b", "c"), "a", rows = 0.3f, expected = listOf("a", "b", "c"))

    @Test
    fun `descer e voltar no mesmo arraste mantem a ordem`() =
        dragTest(listOf("a", "b", "c", "d"), "a", listOf(2f to 10, -2f to 10), expected = listOf("a", "b", "c", "d"))

    @Test
    fun `arraste rapido com poucos movimentos grandes`() =
        dragTest(listOf("a", "b", "c", "d"), "a", listOf(3f to 2), expected = listOf("b", "c", "d", "a"))

    @Test
    fun `clique sem arrastar continua funcionando e nao muda a ordem`() = runComposeUiTest {
        var clicks = 0
        val items = mutableStateOf(listOf("a", "b"))
        setContent {
            ReorderableColumn(items.value, { it }, rowHeight, { from, to ->
                items.value = items.value.toMutableList().apply { add(to, removeAt(from)) }
            }, Modifier.width(300.dp)) { _, item, _ ->
                Text(item, Modifier.testTag(item).fillMaxSize().clickable { clicks++ })
            }
        }
        onNodeWithTag("b").performClick()
        waitForIdle()
        assertEquals(1, clicks)
        assertEquals(listOf("a", "b"), items.value)
    }
}
