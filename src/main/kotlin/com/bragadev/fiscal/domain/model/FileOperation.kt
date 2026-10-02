package com.bragadev.fiscal.domain.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * Registro de uma operação realizada sobre um arquivo, usado pelo histórico e pelo desfazer.
 *
 * [originalPath] e [newPath] são caminhos completos do arquivo (pasta + nome).
 * [backupPath] só é preenchido quando a operação substituiu um arquivo existente:
 * o arquivo substituído é guardado ali para que a operação possa ser desfeita.
 */
data class FileOperation(
    val id: UUID,
    val type: OperationType,
    val originalPath: String,
    val originalName: String,
    val newPath: String,
    val newName: String,
    val timestamp: LocalDateTime,
    val backupPath: String? = null,
    val undone: Boolean = false,
)

enum class OperationType {
    RENAME,
    MOVE,
}
