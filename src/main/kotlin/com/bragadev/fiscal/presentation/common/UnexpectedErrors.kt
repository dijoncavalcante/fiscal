package com.bragadev.fiscal.presentation.common

import com.bragadev.fiscal.data.logging.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ponto único para erros inesperados (de qualquer tela ou da própria janela): registra no log e
 * abre a janela "Algo deu errado" com o botão de copiar diagnóstico, em vez de fechar o app.
 */
object UnexpectedErrors {
    private val state = MutableStateFlow<Throwable?>(null)
    val current: StateFlow<Throwable?> = state.asStateFlow()

    fun report(error: Throwable) {
        AppLog.error("Erro inesperado", error)
        state.value = error
    }

    fun dismiss() {
        state.value = null
    }
}
