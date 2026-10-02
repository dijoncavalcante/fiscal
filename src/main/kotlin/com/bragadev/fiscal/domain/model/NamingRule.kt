package com.bragadev.fiscal.domain.model

/** Define como o nome físico do PDF é gerado para uma categoria. */
enum class NamingRule {
    /** O arquivo recebe o rótulo da categoria: "8. Extrato Bancário.pdf". */
    CATEGORY_NAME,

    /** O arquivo recebe numeração sequencial: "1. Outros.pdf", "2. Outros.pdf"... */
    SEQUENTIAL,

    /**
     * Vários arquivos numerados e descritos pelo usuário:
     * "3. Despesa - xxx.pdf", "3.1 Despesa - yyy.pdf", "3.2 Despesa - xyz.pdf"...
     */
    DESCRIBED_SEQUENCE,
}
