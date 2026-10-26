package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseCheck
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import com.bragadev.fiscal.domain.rules.DonationRules.Party
import com.bragadev.fiscal.domain.rules.FuzzyText.findPhrase
import com.bragadev.fiscal.domain.rules.FuzzyText.words
import com.bragadev.fiscal.domain.rules.RemittanceProofRules.destination
import com.bragadev.fiscal.domain.rules.RemittanceProofRules.origin
import java.math.BigDecimal

/**
 * Conferência do "5.1 Comprovante Remessa" da Conta da Congregação, pedida pelo usuário em 26/10/2026. É o
 * comprovante do Pix da remessa para Betel (Nubank, quase sempre digitalizado), e precisa ter:
 * - a assinatura digital do responsável das despesas da Congregação (configurado em Configurações);
 * - o banco Nubank / Nu Pagamentos;
 * - a origem ([origin]) e o destino ([destination]) combinados;
 * - o valor da transferência igual ao "Total" da "5. Remessa Betel" e ao "TOTAL DE FUNDOS ENVIADOS:" do "4. TO-62"
 *   do mesmo mês (conferido junto com eles, [TransferRecordRules]).
 *
 * Os dados são procurados como nos donativos ([DonationRules.missingData]): na foto, um dígito de tolerância e o
 * CPF/CNPJ dispensado quando o nome e a conta foram lidos. Comprovante digitalizado: o que não foi lido ou não bate
 * vira "confira à mão", nunca "troque".
 */
object RemittanceProofRules {
    const val CATEGORY = "congregacao.comprovante_remessa"

    fun categoryFor(account: AccountType?): String? = CATEGORY.takeIf { account == AccountType.CONGREGACAO }

    fun isProofCategory(categoryId: String?): Boolean = categoryId == CATEGORY

    /** Conta da Congregação no Nubank, de onde sai a remessa (dados passados pelo usuário). */
    val origin = Party("Filipe da Silva Costa", "369.732", "Nu Pagamentos", "0001", "15077760-0")

    /** Conta corrente de Betel no Bradesco, que recebe a remessa. */
    val destination = Party(
        name = "Associação Torre de Vigia de Bíblias e Tratados",
        cpf = "33755687000124",
        institution = "Bradesco",
        branch = "3372",
        account = "40100-5",
        accountType = "Conta corrente",
        isCompany = true,
    )

    /** O comprovante é do Nubank: o nome do banco ou da instituição de pagamento. */
    private val bankMarkers = listOf("Nubank", "Nu Pagamentos")

    /**
     * Resultado e texto da pendência (`null` = comprovante certo); só o resultado é guardado no banco.
     *
     * @param signer responsável das despesas da Congregação; `null` = não configurado.
     */
    fun check(
        content: DocumentContent,
        signer: String?,
        remittance: TransferRecordRules.Remittance,
        transferRecord: TransferRecordRules.TransferRecord,
    ): BankStatementRules.Result {
        val text = content.pageTexts.joinToString("\n")
        val page = words(text)
        val fromOcr = content.ocrPages.isNotEmpty()

        val signed = signer != null && ExpenseDocumentRules.isSignedBy(content, signer)
        val isNubank = bankMarkers.any { page.findPhrase(words(it)).any() }
        val missingData = DonationRules.missingData(page, origin, fromOcr).map { "origem $it" } +
                DonationRules.missingData(page, destination, fromOcr).map { "destino $it" }
        val amount = DonationRules.findAmount(text)
        val totalNotes = amountNotes(amount, remittance, transferRecord, fromOcr)

        val missing = buildSet {
            if (!signed) add(ExpenseRequirement.SIGNATURE)
            if (!isNubank || missingData.isNotEmpty()) add(ExpenseRequirement.PROOF_DATA)
            if (totalNotes.isNotEmpty()) add(ExpenseRequirement.TRANSFER_TOTAL)
        }
        val notes = buildList {
            when {
                signer == null -> add("$UNSIGNED_PREFIX $EXPENSE_SIGNER $NOT_CONFIGURED_SUFFIX")
                !signed -> add("$UNSIGNED_PREFIX de $signer")
            }
            if (!isNubank) add(if (fromOcr) BANK_OCR_NOTE else BANK_NOTE)
            if (missingData.isNotEmpty()) add(dataNote(missingData, fromOcr))
            addAll(totalNotes)
        }
        val evidence = if (missing.isEmpty() && amount != null) {
            "R$ ${Money.format(amount)} de ${origin.name} (Nubank) para ${destination.name}, igual à remessa e ao " +
                    "TO-62; assinado por $signer"
        } else null
        return BankStatementRules.Result(
            ExpenseCheck(missing, amountEvidence = evidence),
            notes.takeIf { it.isNotEmpty() }?.joinToString(NOTE_SEPARATOR),
        )
    }

    // ---- Pendência ----

    private const val NOTE_SEPARATOR = "; "
    private const val UNSIGNED_PREFIX = "Falta assinatura digital (gov.br)"
    private const val EXPENSE_SIGNER = "do responsável das despesas da Congregação"
    private const val NOT_CONFIGURED_SUFFIX =
        "(configure em Configurações → Assinaturas das despesas e extratos)"
    private const val BANK_NOTE = "Comprovante não é do Nubank (Nu Pagamentos): troque pelo comprovante da remessa"
    private const val BANK_OCR_NOTE = "Banco do comprovante não lido na foto (Nubank / Nu Pagamentos): confira à mão"
    private const val DATA_PREFIX = "Comprovante sem os dados da remessa"
    private const val DATA_OCR_PREFIX = "Dados da remessa não confirmados na foto"
    private const val PAID = "Valor da transferência"
    private const val REMITTANCE_TOTAL = "Total da Remessa Betel"
    private const val SENT = "TOTAL DE FUNDOS ENVIADOS do TO-62"

    private fun list(items: List<String>): String =
        if (items.size == 1) items.single() else items.dropLast(1).joinToString(", ") + " e " + items.last()

    private fun dataNote(missing: List<String>, fromOcr: Boolean): String =
        if (fromOcr) "$DATA_OCR_PREFIX (não li ${list(missing)}): confira à mão"
        else "$DATA_PREFIX (não tem ${list(missing)}): troque pelo comprovante certo"

    private fun money(value: BigDecimal) = "R$ ${Money.format(value)}"

    /** O valor pago comparado com o Total da remessa e com o TOTAL DE FUNDOS ENVIADOS do TO-62. */
    private fun amountNotes(
        amount: BigDecimal?,
        remittance: TransferRecordRules.Remittance,
        transferRecord: TransferRecordRules.TransferRecord,
        fromOcr: Boolean,
    ): List<String> {
        val others = listOf(
            Triple(REMITTANCE_TOTAL, "\"5. Remessa Betel\"", remittance.files.isEmpty()) to remittance.total,
            Triple(SENT, "\"4. TO-62\"", transferRecord.files.isEmpty()) to transferRecord.sent,
        )
        if (amount == null) {
            val known = others.firstNotNullOfOrNull { (label, value) -> value?.let { "${label.first} (${money(it)})" } }
            return listOf("$PAID não foi lido: confira à mão" + (known?.let { " se é igual ao $it" } ?: ""))
        }
        val photo = fromOcr || remittance.fromOcr || transferRecord.fromOcr
        return others.mapNotNull { (label, value) ->
            val (name, category, isEmpty) = label
            when {
                isEmpty -> "$PAID (${money(amount)}) não pôde ser conferido com o $name: $category não tem nenhum " +
                        "documento neste mês"

                value == null -> "$name não foi lido: confira à mão se é igual ao valor da transferência " +
                        "(${money(amount)})"

                amount.compareTo(value) != 0 -> "$PAID (${money(amount)}) é diferente do $name (${money(value)}): " +
                        if (photo) "confira à mão (foto)" else "corrija o documento errado"

                else -> null
            }
        }
    }

    /** A pendência foi escrita por esta conferência? As do usuário nunca são mexidas. */
    fun isAutomaticNote(note: String): Boolean =
        note.split(NOTE_SEPARATOR).all { part -> automaticNotes.any { it.matches(part) } }

    private val automaticNotes = listOf(
        Regex("""${Regex.escape(UNSIGNED_PREFIX)} .+"""),
        Regex(Regex.escape(BANK_NOTE)),
        Regex(Regex.escape(BANK_OCR_NOTE)),
        Regex("""${Regex.escape(DATA_PREFIX)} \(não tem .+\): troque pelo comprovante certo"""),
        Regex("""${Regex.escape(DATA_OCR_PREFIX)} \(não li .+\): confira à mão"""),
        Regex("""${Regex.escape(PAID)} não foi lido: confira à mão.*"""),
        Regex("""${Regex.escape(PAID)} \(R\$ [\d.,]+\) não pôde ser conferido com o .+"""),
        Regex(
            """(${Regex.escape(REMITTANCE_TOTAL)}|${Regex.escape(SENT)}) não foi lido: """ +
                    """confira à mão se é igual ao valor da transferência .+""",
        ),
        Regex("""${Regex.escape(PAID)} \(R\$ [\d.,]+\) é diferente do .+"""),
        Regex(Regex.escape(ExpenseDocumentRules.UNREADABLE_NOTE)),
    )
}
