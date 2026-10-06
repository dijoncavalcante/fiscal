package com.bragadev.fiscal.presentation

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bragadev.fiscal.presentation.common.KeyboardShortcuts
import com.bragadev.fiscal.presentation.common.LocalKeyboardShortcuts
import com.bragadev.fiscal.presentation.common.RegisterShortcuts
import com.bragadev.fiscal.presentation.components.dialogKeys
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Test
import kotlin.test.assertEquals

/** Teclas de verdade: Enter/Esc nos diálogos e Ctrl+Z/F5 na janela principal. */
@OptIn(ExperimentalTestApi::class)
class KeyboardTest {

    // ---- Diálogos: Enter confirma, Esc cancela ----

    private class DialogCounts {
        var confirmed by mutableStateOf(0)
        var dismissed by mutableStateOf(0)
    }

    private fun dialogTest(confirmEnabled: Boolean = true, block: androidx.compose.ui.test.ComposeUiTest.(DialogCounts) -> Unit) =
        runComposeUiTest {
            val counts = DialogCounts()
            setContent {
                var text by remember { mutableStateOf("") }
                Dialog(onDismissRequest = { counts.dismissed++ }) {
                    Surface(
                        Modifier.testTag("dialogo").dialogKeys(
                            onConfirm = if (confirmEnabled) ({ counts.confirmed++ }) else null,
                            onDismiss = { counts.dismissed++ },
                        ),
                    ) {
                        Column {
                            OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.testTag("campo"))
                            TextButton(onClick = { counts.dismissed++ }, modifier = Modifier.testTag("cancelar")) { Text("Cancelar") }
                        }
                    }
                }
            }
            waitForIdle()
            block(counts)
        }

    @Test
    fun `enter confirma assim que o dialogo abre`() = dialogTest { counts ->
        onNodeWithTag("dialogo").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(1, counts.confirmed)
        assertEquals(0, counts.dismissed)
    }

    @Test
    fun `enter confirma mesmo digitando no campo`() = dialogTest { counts ->
        onNodeWithTag("campo").performClick()
        onNodeWithTag("campo").performTextInput("Compra de cartazes")
        onNodeWithTag("campo").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(1, counts.confirmed)
    }

    @Test
    fun `esc cancela`() = dialogTest { counts ->
        onNodeWithTag("campo").performClick()
        onNodeWithTag("campo").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(1, counts.dismissed)
        assertEquals(0, counts.confirmed)
    }

    @Test
    fun `enter nao faz nada com o botao principal desabilitado`() = dialogTest(confirmEnabled = false) { counts ->
        onNodeWithTag("dialogo").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(0, counts.confirmed)
        assertEquals(0, counts.dismissed)
    }

    // ---- Janela principal: Ctrl+Z e F5 ----

    private class ShortcutCounts {
        var undos = 0
        var refreshes = 0
    }

    private fun shortcutTest(block: androidx.compose.ui.test.ComposeUiTest.(ShortcutCounts) -> Unit) = runComposeUiTest {
        val counts = ShortcutCounts()
        val shortcuts = KeyboardShortcuts()
        setContent {
            CompositionLocalProvider(LocalKeyboardShortcuts provides shortcuts) {
                RegisterShortcuts(onUndo = { counts.undos++ }, onRefresh = { counts.refreshes++ })
                // Como a janela: recebe só as teclas que o elemento focado não usou.
                Column(Modifier.onKeyEvent(shortcuts::handle)) {
                    Box(Modifier.size(50.dp).testTag("tela").focusable())
                    var text by remember { mutableStateOf("") }
                    OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.testTag("busca"))
                }
            }
        }
        waitForIdle()
        block(counts)
    }

    private fun KeyInjectionScope.ctrl(key: Key) = withKeyDown(Key.CtrlLeft) { pressKey(key) }

    @Test
    fun `ctrl z desfaz e f5 atualiza`() = shortcutTest { counts ->
        onNodeWithTag("tela").requestFocus()
        onNodeWithTag("tela").performKeyInput { ctrl(Key.Z) }
        onNodeWithTag("tela").performKeyInput { pressKey(Key.F5) }
        waitForIdle()
        assertEquals(1, counts.undos)
        assertEquals(1, counts.refreshes)
    }

    @Test
    fun `ctrl z dentro de um campo de texto desfaz a digitacao, nao a organizacao`() = shortcutTest { counts ->
        onNodeWithTag("busca").performClick()
        onNodeWithTag("busca").performTextInput("abc")
        onNodeWithTag("busca").performKeyInput { ctrl(Key.Z) }
        waitForIdle()
        assertEquals(0, counts.undos)
        onNodeWithTag("busca").assertTextEquals("")
    }

    @Test
    fun `outras combinacoes nao disparam atalhos`() = shortcutTest { counts ->
        onNodeWithTag("tela").requestFocus()
        onNodeWithTag("tela").performKeyInput { withKeyDown(Key.ShiftLeft) { ctrl(Key.Z) } }
        onNodeWithTag("tela").performKeyInput { pressKey(Key.Z) }
        waitForIdle()
        assertEquals(0, counts.undos)
        assertEquals(0, counts.refreshes)
    }
}
