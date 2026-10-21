package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.Outcome
import java.nio.file.Path

/**
 * Imagem de referência do selo de assinatura de cada conta (só para o usuário reconhecer em Configurações; a
 * conferência usa o nome). Fica na pasta de dados do usuário, nunca no projeto nem no pendrive.
 */
interface SignatureImageStorage {
    /** Bytes da imagem (PNG) guardada para a conta; `null` se não há. */
    suspend fun load(account: AccountType): ByteArray?

    /** Copia a imagem escolhida (PNG, JPEG…) como PNG, substituindo a anterior da conta. */
    suspend fun save(account: AccountType, source: Path): Outcome<Unit>

    suspend fun remove(account: AccountType): Outcome<Unit>
}
