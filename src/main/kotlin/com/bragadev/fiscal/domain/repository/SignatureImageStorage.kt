package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.SignatureRole
import java.nio.file.Path

/**
 * Imagem de referência do selo de assinatura de quem assina (só para o usuário reconhecer em Configurações; a
 * conferência usa o nome). Fica na pasta de dados do usuário, nunca no projeto nem no pendrive.
 */
interface SignatureImageStorage {
    /** Bytes da imagem (PNG) guardada para quem assina; `null` se não há. */
    suspend fun load(role: SignatureRole): ByteArray?

    /** Copia a imagem escolhida (PNG, JPEG…) como PNG, substituindo a anterior. */
    suspend fun save(role: SignatureRole, source: Path): Outcome<Unit>

    suspend fun remove(role: SignatureRole): Outcome<Unit>
}
