package com.bragadev.fiscal.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Atalhos da janela principal: Ctrl+Z desfaz a última operação e F5 atualiza.
 *
 * A janela entrega aqui só as teclas que nenhum elemento usou (ex.: Ctrl+Z dentro de um campo de texto
 * continua desfazendo a digitação). A tela aberta registra o que cada atalho faz com [RegisterShortcuts].
 */
class KeyboardShortcuts {
    internal var onUndo: (() -> Unit)? = null
    internal var onRefresh: (() -> Unit)? = null

    fun handle(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isShiftPressed) return false
        val action = when {
            event.key == Key.F5 && !event.isCtrlPressed -> onRefresh
            event.key == Key.Z && event.isCtrlPressed -> onUndo
            else -> null
        } ?: return false
        action()
        return true
    }
}

val LocalKeyboardShortcuts = staticCompositionLocalOf { KeyboardShortcuts() }

/** Liga os atalhos enquanto a tela estiver aberta. */
@Composable
fun RegisterShortcuts(onUndo: () -> Unit, onRefresh: () -> Unit) {
    val shortcuts = LocalKeyboardShortcuts.current
    val undo by rememberUpdatedState(onUndo)
    val refresh by rememberUpdatedState(onRefresh)
    DisposableEffect(shortcuts) {
        shortcuts.onUndo = { undo() }
        shortcuts.onRefresh = { refresh() }
        onDispose {
            shortcuts.onUndo = null
            shortcuts.onRefresh = null
        }
    }
}
