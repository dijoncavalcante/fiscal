package com.bragadev.fiscal.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Conta bancária de onde o extrato de uma conta precisa vir (combinado com o usuário em 22/10/2026).
 *
 * @param bank nome do banco como o usuário reconhece, para a pendência ("next (237 - Banco Bradesco S.A.)").
 * @param bankMarkers termos que identificam o banco em qualquer página (basta um): nome, razão social, CNPJ.
 * @param accountType tipo da conta que precisa aparecer no extrato ("Conta Poupança"); `null` = não conferido.
 */
data class BankAccountProfile(
    val bank: String,
    val bankMarkers: List<String>,
    val branch: String,
    val account: String,
    val holder: String,
    val accountType: String? = null,
) {
    /** "next (237 - Banco Bradesco S.A.), Agência 3943, Conta 286256-5, DANILO CAVALCANTE BRAGA". */
    val description: String get() = "$bank, Agência $branch, Conta $account, $holder"
}

/** Quem assina (as despesas de uma conta ou os extratos bancários das duas contas). */
enum class SignatureRole(val account: AccountType?, val imageName: String) {
    EXPENSES_CONGREGACAO(AccountType.CONGREGACAO, "congregacao"),
    EXPENSES_MANUTENCAO(AccountType.MANUTENCAO, "manutencao"),

    /** "8. Extrato Bancário" da Congregação e "5. Extrato Bancário" da Manutenção. */
    BANK_STATEMENTS(null, "extratos"),
    ;

    companion object {
        fun expensesOf(account: AccountType): SignatureRole? = entries.firstOrNull { it.account == account }
    }
}

/**
 * O que a conferência exige do extrato bancário de uma conta.
 *
 * @param signerName quem precisa ter assinado; `null` = ninguém configurado em Configurações.
 * @param month mês em edição: o período do extrato precisa ser dele; `null` = mês não identificado (não confere).
 */
data class StatementRequirements(
    val signerName: String?,
    val month: YearMonth?,
    val account: BankAccountProfile,
) {
    /** Muda quando muda o que é exigido: o resultado guardado de antes deixa de valer. */
    val key: String get() = "extrato|${signerName.orEmpty()}|${month ?: ""}|${account.description}"
}

/** Período de movimentação de um extrato ("01/08/2026 a 31/08/2026"). */
data class StatementPeriod(val start: LocalDate, val end: LocalDate) {
    /**
     * Mês a que o extrato se refere: o que tem mais dias dentro do período (o Nubank às vezes vai de 01/03 a
     * 01/04 ou de 09/01 a 31/01). Empate fica com o primeiro.
     */
    val mainMonth: YearMonth
        get() {
            var best = YearMonth.from(start)
            var bestDays = -1L
            var month = YearMonth.from(start)
            while (!month.isAfter(YearMonth.from(end))) {
                val from = maxOf(start, month.atDay(1))
                val to = minOf(end, month.atEndOfMonth())
                val days = to.toEpochDay() - from.toEpochDay() + 1
                if (days > bestDays) {
                    best = month
                    bestDays = days
                }
                month = month.plusMonths(1)
            }
            return best
        }
}
