package com.bragadev.fiscal.domain.model

import java.time.Instant

/** O que uma despesa da Conta da Manutenção precisa ter dentro do PDF. */
enum class ExpenseRequirement {
    /** Cupom fiscal ou nota fiscal (NFC-e, DANFE, fatura...). */
    FISCAL_DOCUMENT,

    /** Comprovante da transação Pix ou TED. */
    PAYMENT_PROOF,

    /** Assinatura digital (gov.br) do responsável. */
    SIGNATURE,
}

/**
 * Conteúdo lido de um PDF para conferir a despesa.
 *
 * @param pageTexts texto de cada página; páginas que são só uma foto (cupom fotografado) vêm do OCR.
 * @param signers quem assinou digitalmente o PDF (nome do certificado de cada assinatura).
 */
data class DocumentContent(
    val pageTexts: List<String>,
    val signers: List<String> = emptyList(),
)

/**
 * Resultado da conferência de uma despesa.
 *
 * @param unreadable o PDF não pôde ser aberto (corrompido ou protegido por senha).
 */
data class ExpenseCheck(
    val missing: Set<ExpenseRequirement> = emptySet(),
    val unreadable: Boolean = false,
) {
    val isValid: Boolean get() = missing.isEmpty() && !unreadable
}

/** Conferência guardada no banco, válida enquanto o arquivo tiver o mesmo tamanho e data de modificação. */
data class StoredExpenseCheck(
    val sizeBytes: Long,
    val lastModified: Instant,
    val check: ExpenseCheck,
) {
    /** Compara em milissegundos: é a precisão gravada no banco. */
    fun matches(document: Document): Boolean =
        sizeBytes == document.sizeBytes && lastModified.toEpochMilli() == document.lastModified.toEpochMilli()
}

/** Situação de uma despesa na tela do mês em edição. */
enum class ExpenseStatus {
    CHECKING,
    VERIFIED,
    HAS_ISSUES,

    /** Não foi possível conferir agora (ex.: arquivo aberto em outro programa); tenta de novo depois. */
    NOT_CHECKED,
}
