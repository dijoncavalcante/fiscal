package com.bragadev.fiscal.domain.model

import java.math.BigDecimal
import java.time.Instant

/** O que uma despesa (ou um extrato bancário) precisa ter dentro do PDF. */
enum class ExpenseRequirement {
    /** Cupom fiscal, nota fiscal (NFC-e, DANFE, fatura...) ou recibo. */
    FISCAL_DOCUMENT,

    /** Comprovante da transação Pix ou TED. */
    PAYMENT_PROOF,

    /** Assinatura digital (gov.br) do responsável. */
    SIGNATURE,

    /** Extrato bancário: do banco, agência, conta e titular combinados para a conta. */
    BANK_ACCOUNT,

    /** Extrato bancário: período de movimentação do mês em edição. */
    STATEMENT_MONTH,

    /** Folha de Contas: título "FOLHA DE CONTAS (S-26)" e o nome da conta (congregação ou comissão). */
    SHEET_HEADER,

    /** Folha de Contas: mês e ano do mês em edição. */
    SHEET_MONTH,
}

/**
 * Conteúdo lido de um PDF para conferir a despesa.
 *
 * @param pageTexts texto de cada página; páginas que são só uma foto (cupom fotografado) vêm do OCR.
 * @param signers quem assinou digitalmente o PDF (nome do certificado de cada assinatura).
 * @param ocrPages páginas (a partir de 0) lidas pelo OCR: nelas um dígito pode ter saído errado.
 * @param layouts palavras com a posição na página: do próprio PDF (páginas com texto) e de cada leitura do OCR
 *   (uma página fotografada pode ter várias leituras, com resolução e modo diferentes). Servem para achar o
 *   total numa tabela, onde o valor fica embaixo do título da coluna ("TOTAL (R$)").
 */
data class DocumentContent(
    val pageTexts: List<String>,
    val signers: List<String> = emptyList(),
    val ocrPages: Set<Int> = emptySet(),
    val layouts: List<PageLayout> = emptyList(),
)

/**
 * Uma leitura das palavras de uma página, com a posição de cada uma.
 *
 * @param page página (a partir de 0).
 * @param fromOcr veio do OCR (foto): a confiança de cada palavra importa.
 * @param description como foi lida, para o log e o diagnóstico (ex.: "OCR 300 DPI, palavras soltas").
 */
data class PageLayout(
    val page: Int,
    val words: List<PageWord>,
    val fromOcr: Boolean,
    val description: String = "",
)

/**
 * Palavra numa página. Posição e tamanho em pixels da imagem do OCR ou em pontos do PDF (y cresce para baixo).
 *
 * @param confidence certeza do OCR, de 0 a 100 (texto do próprio PDF = 100).
 */
data class PageWord(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float = 100f,
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centerY: Float get() = y + height / 2
}

/**
 * O que a conferência exige de uma despesa: depende da conta, do nome do arquivo e das Configurações.
 *
 * @param signerName quem precisa ter assinado (nome completo); `null` = ninguém configurado em Configurações.
 * @param exemptBy termo do nome do arquivo que dispensa o cupom (ex.: "Resolução"); `null` = cupom obrigatório e
 *   valor do Pix igual ao total.
 */
data class ExpenseRequirements(val signerName: String?, val exemptBy: String? = null) {
    /** Muda quando muda o que é exigido: o resultado guardado de antes deixa de valer. */
    val key: String get() = "${signerName.orEmpty()}|${exemptBy.orEmpty()}"
}

/**
 * Resultado da conferência de uma despesa.
 *
 * @param unreadable o PDF não pôde ser aberto (corrompido ou protegido por senha).
 * @param amountProblem o valor pago no comprovante não bate com o total do cupom/nota, ou não deu para conferir.
 * @param amountEvidence quando o valor bateu: de onde veio o total (ex.: "Valor conferido: R$ 50,00 abaixo de
 *   "TOTAL (R$)", página 1 (foto)"), ou por que o cupom não foi exigido; para o usuário conferir num instante.
 * @param signerName quem precisava ter assinado (para a pendência dizer o nome); `null` = não configurado.
 */
data class ExpenseCheck(
    val missing: Set<ExpenseRequirement> = emptySet(),
    val unreadable: Boolean = false,
    val amountProblem: AmountProblem? = null,
    val amountEvidence: String? = null,
    val signerName: String? = null,
) {
    val isValid: Boolean get() = missing.isEmpty() && !unreadable && amountProblem == null
}

/**
 * Problema no valor do comprovante × total do cupom/nota fiscal.
 *
 * @param paid valor pago (soma dos comprovantes, se houver mais de um); `null` se não foi lido.
 * @param fiscal total do cupom/nota; `null` se não foi lido.
 */
data class AmountProblem(val kind: AmountProblemKind, val paid: BigDecimal? = null, val fiscal: BigDecimal? = null)

enum class AmountProblemKind {
    /** Os dois valores foram lidos e são diferentes. */
    MISMATCH,

    /** O "Valor:" do comprovante Pix/TED não foi lido. */
    PAYMENT_UNREADABLE,

    /** Nenhuma linha de total (total, valor a pagar...) foi lida no cupom/nota. */
    TOTAL_UNREADABLE,

    /** Os valores não batem, mas vieram de uma foto (OCR), que pode ter sido mal lida: não dá para afirmar. */
    UNCERTAIN_READING,
}

/** Conferência guardada no banco, válida enquanto o arquivo tiver o mesmo tamanho e data de modificação. */
data class StoredExpenseCheck(
    val sizeBytes: Long,
    val lastModified: Instant,
    val check: ExpenseCheck,
    /** Versão das regras que gerou o resultado: regras novas conferem de novo os arquivos já vistos. */
    val rulesVersion: Int = EXPENSE_RULES_VERSION,
    /** [ExpenseRequirements.key] usado: trocar o responsável ou os termos de isenção confere de novo. */
    val requirementsKey: String = "",
) {
    /**
     * Ainda vale: mesmo arquivo (data comparada em milissegundos, a precisão do banco), regras atuais e as
     * mesmas exigências.
     */
    fun matches(document: Document, requirementsKey: String, rulesVersion: Int = EXPENSE_RULES_VERSION): Boolean =
        sizeBytes == document.sizeBytes && lastModified.toEpochMilli() == document.lastModified.toEpochMilli() &&
                this.rulesVersion == rulesVersion && this.requirementsKey == requirementsKey
}

/**
 * Aumente quando a conferência passar a olhar algo novo (2 = valor do comprovante × cupom/nota; 3 = recibo vale,
 * valor só contra o total, pendência quando o valor não pôde ser conferido; 4 = total lido pela posição na
 * tabela, novas leituras do OCR e confiança das palavras; 5 = Conta da Congregação, responsável configurável,
 * isenção pelo nome do arquivo e selo gov.br completo).
 */
const val EXPENSE_RULES_VERSION = 5

/** Como [EXPENSE_RULES_VERSION], para a conferência dos extratos bancários. */
const val STATEMENT_RULES_VERSION = 1

/** Como [EXPENSE_RULES_VERSION], para a conferência da Folha de Contas (S-26). */
const val SHEET_RULES_VERSION = 1

/** Situação de uma despesa na tela do mês em edição. */
enum class ExpenseStatus {
    CHECKING,
    VERIFIED,
    HAS_ISSUES,

    /** Não foi possível conferir agora (ex.: arquivo aberto em outro programa); tenta de novo depois. */
    NOT_CHECKED,
}
