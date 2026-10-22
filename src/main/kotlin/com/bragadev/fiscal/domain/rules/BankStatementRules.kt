package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.rules.BankStatementRules.accountFor
import com.bragadev.fiscal.domain.rules.FuzzyText.findPhrase
import com.bragadev.fiscal.domain.rules.FuzzyText.words
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Conferência do extrato bancário ("8. Extrato Bancário" da Congregação, "5. Extrato Bancário" da Manutenção),
 * pedida pelo usuário em 22/10/2026. Cada PDF precisa:
 * - ser da conta combinada ([accountFor]): banco, agência, conta e titular (e o tipo, quando há);
 * - ter o período de movimentação no mês em edição ([StatementPeriod.mainMonth]);
 * - estar assinado por quem assina os extratos (Configurações), pelo certificado ou pelo selo gov.br completo
 *   (mesma regra das despesas; a data da assinatura não importa).
 *
 * Os extratos digitalizados (CamScanner) chegam pelo OCR: nesses, o que não foi lido vira "confira à mão",
 * nunca "troque o documento".
 */
object BankStatementRules {
    private val categories = mapOf(
        AccountType.CONGREGACAO to "congregacao.extrato_bancario",
        AccountType.MANUTENCAO to "manutencao.extrato_bancario",
    )

    /** Categoria "Extrato Bancário" da conta, ou `null` se a conta não tem extrato conferido. */
    fun categoryFor(account: AccountType?): String? = categories[account]

    fun isStatementCategory(categoryId: String?): Boolean = categoryId in categories.values

    /** Conta de onde vem o extrato de cada conta (dados passados pelo usuário). */
    fun accountFor(account: AccountType?): BankAccountProfile? = when (account) {
        AccountType.MANUTENCAO -> BankAccountProfile(
            bank = "next (237 - Banco Bradesco S.A.)",
            bankMarkers = listOf("next"),
            branch = "3943",
            account = "286256-5",
            holder = "DANILO CAVALCANTE BRAGA",
            accountType = "Conta Poupança",
        )

        AccountType.CONGREGACAO -> BankAccountProfile(
            bank = "Nubank (Nu Pagamentos S.A.)",
            bankMarkers = listOf(
                "Nu Pagamentos", "Nu Financeira", "Nubank", "18.236.120/0001-58", "30.680.829/0001-43",
            ),
            branch = "0001",
            account = "15077760-0",
            holder = "Filipe da Silva Costa",
        )

        else -> null
    }

    /**
     * Resultado da conferência e o texto da pendência (`null` = extrato certo). Só o resultado é guardado no
     * banco; a pendência é escrita na hora.
     */
    data class Result(val check: ExpenseCheck, val note: String?)

    fun check(content: DocumentContent, requirements: StatementRequirements): Result {
        val pages = content.pageTexts.map(::words)
        val firstPage = pages.firstOrNull().orEmpty()
        val fromOcr = 0 in content.ocrPages
        val profile = requirements.account
        val notFound = missingAccountData(pages, firstPage, profile, fromOcr)

        val period = findPeriod(content.pageTexts)
        val periodFromOcr = period?.let { it.second in content.ocrPages } ?: content.ocrPages.isNotEmpty()
        val month = requirements.month
        val wrongMonth = month != null && period?.first?.mainMonth != month

        val signer = requirements.signerName?.takeIf { it.isNotBlank() }
        val signed = signer != null && ExpenseDocumentRules.isSignedBy(content, signer)

        val missing = buildSet {
            if (notFound.isNotEmpty()) add(ExpenseRequirement.BANK_ACCOUNT)
            if (wrongMonth) add(ExpenseRequirement.STATEMENT_MONTH)
            if (!signed) add(ExpenseRequirement.SIGNATURE)
        }
        val notes = buildList {
            if (notFound.isNotEmpty()) add(accountNote(profile, notFound, fromOcr))
            if (wrongMonth) add(periodNote(period?.first, month, periodFromOcr))
            if (!signed) add(signatureNote(signer))
        }
        val evidence = if (missing.isEmpty()) evidence(profile, period?.first, signer) else null
        return Result(
            ExpenseCheck(missing, amountEvidence = evidence, signerName = signer),
            notes.takeIf { it.isNotEmpty() }?.joinToString(NOTE_SEPARATOR),
        )
    }

    /** Já dá para dar a resposta sem fazer OCR das páginas com imagem? Só se o extrato já está certo. */
    fun hasEnoughText(content: DocumentContent, requirements: StatementRequirements): Boolean =
        check(content, requirements).check.isValid

    // ---- Conta ----

    /**
     * O que não foi achado no extrato, para a pendência dizer ("Conta 286256-5", "titular …"). Agência, conta,
     * titular e tipo ficam no cabeçalho da primeira página (o next embaralha a ordem: "Agência: | Conta: |
     * Tipo: 3943 286256-5 Conta Poupança", por isso cada dado é procurado sozinho); o banco pode estar em qualquer
     * página (o Nubank só põe o CNPJ no fim). Na foto, o número da conta aceita um dígito trocado pelo OCR.
     */
    private fun missingAccountData(
        pages: List<List<String>>,
        firstPage: List<String>,
        profile: BankAccountProfile,
        fromOcr: Boolean,
    ): List<String> = buildList {
        if (profile.bankMarkers.none { marker -> pages.any { it.findPhrase(words(marker)).any() } }) {
            add("banco ${profile.bank}")
        }
        if (profile.branch !in firstPage) add("Agência ${profile.branch}")
        val accountNumber = words(profile.account).first()
        if (firstPage.none { it == accountNumber || (fromOcr && oneDigitOff(it, accountNumber)) }) {
            add("Conta ${profile.account}")
        }
        if (firstPage.findPhrase(words(profile.holder)).none()) add("titular ${profile.holder}")
        profile.accountType?.let { type ->
            if (firstPage.findPhrase(words(type)).none()) add(type)
        }
    }

    private fun oneDigitOff(found: String, expected: String): Boolean =
        found.length == expected.length && found.indices.count { found[it] != expected[it] } == 1

    // ---- Período ----

    private const val DAY_MONTH_YEAR = """(\d{2})/(\d{2})/(\d{4})"""
    private const val WRITTEN_DATE = """(\d{1,2}) DE ([A-Z]{3,10}) DE (\d{4})"""

    /** next: "Movimentação entre: 01/08/2026 e 31/08/2026" (o OCR troca o "e" por "¢" e põe "= " ou "—_" no meio). */
    private val periodBetween = Regex("""ENTRE[^0-9]{0,6}$DAY_MONTH_YEAR \S{1,2} $DAY_MONTH_YEAR""")

    /** Nubank: "02 DE AGOSTO DE 2026 a 31 DE AGOSTO DE 2026" (no texto do PDF o "a" vem antes das datas). */
    private val periodWritten = Regex("""$WRITTEN_DATE (?:A )?$WRITTEN_DATE""")

    private val monthNames = listOf(
        "JANEIRO", "FEVEREIRO", "MARCO", "ABRIL", "MAIO", "JUNHO",
        "JULHO", "AGOSTO", "SETEMBRO", "OUTUBRO", "NOVEMBRO", "DEZEMBRO",
    )

    /** Primeiro período achado e a página (a partir de 0) onde estava; `null` se não foi lido. */
    internal fun findPeriod(pageTexts: List<String>): Pair<StatementPeriod, Int>? =
        pageTexts.withIndex().firstNotNullOfOrNull { (index, text) ->
            val page = plain(text)
            val period = periodBetween.find(page)?.let { match ->
                val g = match.groupValues
                period(date(g[1], g[2], g[3]), date(g[4], g[5], g[6]))
            } ?: periodWritten.findAll(page).firstNotNullOfOrNull { match ->
                val g = match.groupValues
                period(writtenDate(g[1], g[2], g[3]), writtenDate(g[4], g[5], g[6]))
            }
            period?.let { it to index }
        }

    private fun period(start: LocalDate?, end: LocalDate?): StatementPeriod? =
        if (start != null && end != null && !end.isBefore(start)) StatementPeriod(start, end) else null

    private fun date(day: String, month: String, year: String): LocalDate? =
        try {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        } catch (_: DateTimeException) {
            null
        }

    /**
     * "01 DE MARGO DE 2026" (OCR de "MARÇO") também vale. Vários meses se parecem (JULHO/JUNHO,
     * SETEMBRO/NOVEMBRO/DEZEMBRO): vale o nome exato ou o único mais parecido; empate = não lido.
     */
    private fun writtenDate(day: String, monthName: String, year: String): LocalDate? {
        val exact = monthNames.indexOf(monthName)
        val month = if (exact >= 0) exact else {
            val close = monthNames.indices.filter { FuzzyText.similar(monthName, monthNames[it], inPhrase = false) }
                .groupBy { FuzzyText.distance(monthName, monthNames[it], limit = 2) }
            close.minByOrNull { it.key }?.value?.singleOrNull() ?: return null
        }
        return date(day, (month + 1).toString(), year)
    }

    /**
     * Sem acentos, em maiúsculas, espaços simples. O OCR lê o zero como a letra O em datas ("O1 DE MARGO"):
     * colado a dígitos, vira zero.
     */
    private fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "").uppercase()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""(?<![A-Z])O(?=\d)|(?<=\d)O(?![A-Z])"""), "0")

    // ---- Pendência ----

    private const val NOTE_SEPARATOR = "; "
    private const val ACCOUNT_PREFIX = "Extrato de outra conta"
    private const val ACCOUNT_OCR_PREFIX = "Conta do extrato não confirmada na foto"
    private const val PERIOD_UNREADABLE_PREFIX = "Período do extrato não foi lido"
    private const val SIGNER_NOT_CONFIGURED =
        "Falta assinatura do responsável pelos extratos (configure quem assina em Configurações → Assinaturas " +
                "das despesas)"
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private fun accountNote(profile: BankAccountProfile, notFound: List<String>, fromOcr: Boolean): String {
        val list = if (notFound.size == 1) notFound.single() else
            notFound.dropLast(1).joinToString(", ") + " e " + notFound.last()
        return if (fromOcr) "$ACCOUNT_OCR_PREFIX (não li $list): confira à mão se é a conta ${profile.description}"
        else "$ACCOUNT_PREFIX (não tem $list): o extrato precisa ser da conta ${profile.description}"
    }

    private fun periodNote(period: StatementPeriod?, month: YearMonth?, fromOcr: Boolean): String {
        val expected = Months.name(month)
        if (period == null) return "$PERIOD_UNREADABLE_PREFIX: confira à mão se é de $expected"
        val action = if (fromOcr) "confira à mão (foto)" else "troque pelo extrato do mês"
        return "Extrato de ${period.start.format(dateFormat)} a ${period.end.format(dateFormat)}, mas o mês em edição é " +
                "$expected: $action"
    }

    private fun signatureNote(signer: String?): String =
        if (signer == null) SIGNER_NOT_CONFIGURED else "Falta assinatura de $signer"

    /** "Extrato conferido: next …, Agência 3943, Conta 286256-5, …; de 01/08/2026 a 31/08/2026; assinado por …". */
    private fun evidence(profile: BankAccountProfile, period: StatementPeriod?, signer: String?): String =
        listOfNotNull(
            profile.description,
            period?.let { "de ${it.start.format(dateFormat)} a ${it.end.format(dateFormat)}" },
            signer?.let { "assinado por $it" },
        ).joinToString("; ")

    /**
     * A pendência foi escrita por esta conferência? Só essas são trocadas ou removidas sozinhas; as do usuário
     * nunca são mexidas.
     */
    fun isAutomaticNote(note: String): Boolean =
        note.split(NOTE_SEPARATOR).all { part -> automaticNotes.any { it.matches(part) } }

    private val automaticNotes = listOf(
        Regex("""${Regex.escape(ACCOUNT_PREFIX)} \(não tem .+\): o extrato precisa ser da conta .+"""),
        Regex("""${Regex.escape(ACCOUNT_OCR_PREFIX)} \(não li .+\): confira à mão se é a conta .+"""),
        Regex("""${Regex.escape(PERIOD_UNREADABLE_PREFIX)}: confira à mão se é de .+"""),
        Regex(
            """Extrato de \d{2}/\d{2}/\d{4} a \d{2}/\d{2}/\d{4}, mas o mês em edição é .+: """ +
                    """(confira à mão \(foto\)|troque pelo extrato do mês)""",
        ),
        Regex(Regex.escape(SIGNER_NOT_CONFIGURED)),
        Regex("""Falta assinatura de [^;]+"""),
        Regex(Regex.escape(ExpenseDocumentRules.UNREADABLE_NOTE)),
    )
}

/** Nome do mês para a pendência ("agosto de 2026"). */
internal object Months {
    private val names = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )

    fun name(month: YearMonth?): String = month?.let { "${names[it.monthValue - 1]} de ${it.year}" } ?: "?"
}
