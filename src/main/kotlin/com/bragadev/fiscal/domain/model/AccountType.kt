package com.bragadev.fiscal.domain.model

/**
 * Tipo de conta ao qual uma categoria pertence.
 *
 * [folderName] é o nome da pasta de primeiro nível dentro da pasta raiz.
 * Quando `null`, as categorias da conta ficam diretamente na pasta raiz (caso de "Outros").
 */
enum class AccountType(val displayName: String, val folderName: String?) {
    CONGREGACAO(displayName = "Conta da Congregação", folderName = "Conta da Congregação"),
    MANUTENCAO(displayName = "Conta da Manutenção", folderName = "Conta da Manutenção"),
    OUTROS(displayName = "Outros", folderName = null),
}
