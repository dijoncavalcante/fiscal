package com.bragadev.fiscal.domain.model

/**
 * Tipo de conta ao qual uma categoria pertence.
 *
 * [isCatchAll] marca a conta "Outros", que vale para qualquer pasta de mês.
 */
enum class AccountType(val displayName: String, val isCatchAll: Boolean = false) {
    CONGREGACAO(displayName = "Conta da Congregação"),
    MANUTENCAO(displayName = "Conta da Manutenção"),
    OUTROS(displayName = "Outros", isCatchAll = true),
}
