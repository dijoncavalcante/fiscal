package com.bragadev.fiscal.domain.model

/** Define como o nome físico do PDF é gerado para uma categoria. */
enum class NamingRule {
    /** O arquivo recebe o nome da categoria: "Extrato Bancário.pdf". */
    CATEGORY_NAME,

    /** O arquivo recebe numeração sequencial: "1. Outros.pdf", "2. Outros.pdf"... */
    SEQUENTIAL,
}
