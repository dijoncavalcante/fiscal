package com.bragadev.fiscal.domain.model

import java.nio.file.Path

data class AppSettings(
    /** Pasta exibida à esquerda, de onde os PDFs são escolhidos. */
    val sourceFolder: Path? = null,
    /** Pasta do mês que está sendo editado, à direita. Destino dos documentos organizados. */
    val monthFolder: Path? = null,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.ASK,
    val confirmBeforeMove: Boolean = true,
    val confirmBeforeRename: Boolean = true,
    val documentSort: DocumentSort = DocumentSort.MODIFIED_NEWEST_FIRST,
)

/** Ordem da lista de documentos (lado esquerdo). */
enum class DocumentSort {
    /** Mais recentes primeiro, como "Data de modificação" no Explorer. */
    MODIFIED_NEWEST_FIRST,
    NAME,
}

/** Comportamento padrão quando o nome de destino já existe. */
enum class DuplicatePolicy {
    /** Pergunta ao usuário: substituir, criar cópia numerada ou cancelar. */
    ASK,

    /** Cria cópia numerada automaticamente, sem perguntar. Nunca substitui. */
    AUTO_NUMBERED_COPY,

    /** Bloqueia a operação. Nunca substitui nem cria cópia. */
    FORBID,
}

/** Escolha do usuário diante de um conflito de nome. */
enum class DuplicateResolution {
    REPLACE,
    NUMBERED_COPY,
    CANCEL,
}
