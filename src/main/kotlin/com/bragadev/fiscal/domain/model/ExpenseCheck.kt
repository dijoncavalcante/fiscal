package com.bragadev.fiscal.domain.model

import java.math.BigDecimal
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
 * @param ocrPages páginas (a partir de 0) lidas pelo OCR: nelas um dígito pode ter saído errado.
 */
data class DocumentContent(
    val pageTexts: List<String>,
    val signers: List<String> = emptyList(),
    val ocrPages: Set<Int> = emptySet(),
)

/**
 * Resultado da conferência de uma despesa.
 *
 * @param unreadable o PDF não pôde ser aberto (corrompido ou protegido por senha).
 * @param amountMismatch o valor pago no comprovante não bate com o cupom/nota.
 */
data class ExpenseCheck(
    val missing: Set<ExpenseRequirement> = emptySet(),
    val unreadable: Boolean = false,
    val amountMismatch: AmountMismatch? = null,
) {
    val isValid: Boolean get() = missing.isEmpty() && !unreadable && amountMismatch == null
}

/**
 * Valor do comprovante diferente do cupom/nota fiscal.
 *
 * @param paid valor pago (soma dos comprovantes, se houver mais de um).
 * @param fiscal total encontrado no cupom/nota; `null` se o OCR não achou a linha do total.
 */
data class AmountMismatch(val paid: BigDecimal, val fiscal: BigDecimal?)

/** Conferência guardada no banco, válida enquanto o arquivo tiver o mesmo tamanho e data de modificação. */
data class StoredExpenseCheck(
    val sizeBytes: Long,
    val lastModified: Instant,
    val check: ExpenseCheck,
    /** Versão das regras que gerou o resultado: regras novas conferem de novo os arquivos já vistos. */
    val rulesVersion: Int = EXPENSE_RULES_VERSION,
) {
    /** Ainda vale: mesmo arquivo (data comparada em milissegundos, a precisão do banco) e regras atuais. */
    fun matches(document: Document): Boolean =
        sizeBytes == document.sizeBytes && lastModified.toEpochMilli() == document.lastModified.toEpochMilli() &&
                rulesVersion == EXPENSE_RULES_VERSION
}

/** Aumente quando a conferência passar a olhar algo novo (2 = valor do comprovante × cupom/nota). */
const val EXPENSE_RULES_VERSION = 2

/** Situação de uma despesa na tela do mês em edição. */
enum class ExpenseStatus {
    CHECKING,
    VERIFIED,
    HAS_ISSUES,

    /** Não foi possível conferir agora (ex.: arquivo aberto em outro programa); tenta de novo depois. */
    NOT_CHECKED,
}
