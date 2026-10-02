package com.bragadev.fiscal.presentation.common

import java.util.UUID

/** Mensagem curta exibida ao usuário. Quando [undoOperationId] existe, a mensagem oferece "Desfazer". */
data class UserMessage(
    val text: String,
    val isError: Boolean = false,
    val undoOperationId: UUID? = null,
    val id: Long = System.nanoTime(),
)
