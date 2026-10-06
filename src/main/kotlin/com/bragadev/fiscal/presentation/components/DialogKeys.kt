package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Teclado nas janelas de diálogo: Enter confirma e Esc cancela.
 *
 * O diálogo recebe o foco ao abrir, para as teclas funcionarem sem clicar antes. Os eventos chegam aqui
 * depois do elemento focado: se o usuário levou o foco (Tab) até um botão, Enter aciona aquele botão.
 *
 * @param onConfirm ação do botão principal; `null` quando ele está desabilitado (Enter não faz nada).
 */
@Composable
fun Modifier.dialogKeys(onConfirm: (() -> Unit)?, onDismiss: () -> Unit): Modifier {
    val focusRequester = remember { FocusRequester() }
    val confirm by rememberUpdatedState(onConfirm)
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(focusRequester) { runCatching { focusRequester.requestFocus() } }
    return this
        .onKeyEvent { event -> handleDialogKey(event, confirm, dismiss) }
        .focusRequester(focusRequester)
        .focusable()
}

internal fun handleDialogKey(event: KeyEvent, onConfirm: (() -> Unit)?, onDismiss: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isShiftPressed) return false
    return when (event.key) {
        Key.Enter, Key.NumPadEnter -> {
            onConfirm?.invoke()
            onConfirm != null
        }
        Key.Escape -> {
            onDismiss()
            true
        }
        else -> false
    }
}
