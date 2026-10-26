package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseCheck
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import com.bragadev.fiscal.domain.rules.DonationRules.AMOUNT
import com.bragadev.fiscal.domain.rules.DonationRules.destination
import com.bragadev.fiscal.domain.rules.FuzzyText.findPhrase
import com.bragadev.fiscal.domain.rules.FuzzyText.words
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter

/**
 * Conferência dos "2. Donativos das Congregações" da Conta da Manutenção, pedida pelo usuário em 26/10/2026.
 * A categoria precisa ter **dois** comprovantes de transferência (Nubank, quase sempre digitalizados), um de cada
 * congregação, e cada um precisa ter:
 * - a data da transferência no mês em edição ("10 SET 2026 - 10:36:07");
 * - o valor do donativo ([AMOUNT]);
 * - o destino combinado ([destination]): nome, CPF, banco, agência, conta e tipo de conta;
 * - a origem da congregação ([Congregation]): nome, CPF, instituição, agência e conta.
 *
 * A ordem dos arquivos segue a data e hora da transferência: a primeira é o "2.", a segunda o "2.1".
 *
 * Os dados são procurados sozinhos no texto (o OCR troca letras: "Cavaleante", "1893A180-7", "Agéncia O0oL"),
 * com um dígito de tolerância na foto. No comprovante digitalizado o que não foi lido vira "confira à mão",
 * nunca "troque". O CPF (só os 6 dígitos do meio aparecem) é o dado que o OCR mais erra: na foto, ele não gera
 * pendência quando o nome e a conta da mesma pessoa foram lidos.
 */
object DonationRules {
    const val CATEGORY = "manutencao.donativos_congregacoes"

    /** Valor do donativo mensal de cada congregação (passado pelo usuário). */
    val AMOUNT: BigDecimal = BigDecimal("800.00")

    /** Categoria dos donativos da conta, ou `null` se a conta não tem donativos conferidos. */
    fun categoryFor(account: AccountType?): String? = CATEGORY.takeIf { account == AccountType.MANUTENCAO }

    fun isDonationCategory(categoryId: String?): Boolean = categoryId == CATEGORY

    /** Dados de uma ponta da transferência, como aparecem no comprovante. */
    data class Party(
        val name: String,
        /** Só os dígitos que aparecem no comprovante ("***.638.182-**"). */
        val cpf: String,
        val institution: String,
        val branch: String,
        val account: String,
        val accountType: String? = null,
    )

    /** Conta Poupança do next/Bradesco da Manutenção, que recebe os dois donativos. */
    val destination = Party(
        name = "Danilo Cavalcante Braga",
        cpf = "638.182",
        institution = "Bradesco",
        branch = "3943",
        account = "286256-5",
        accountType = "Conta poupança",
    )

    /** De onde vem o donativo de cada congregação (dados passados pelo usuário). */
    enum class Congregation(val displayName: String, val origin: Party, private val fileNameMarkers: List<String>) {
        JAPIIM(
            "Japiim",
            Party("Walter Santana Mota do Rosário", "250.412", "Nu Pagamentos", "0001", "18938180-7"),
            listOf("JAPIIM"),
        ),
        TRINTA_E_UM_DE_MARCO(
            "Trinta e Um de Março",
            Party("Filipe da Silva Costa", "369.732", "Nu Pagamentos", "0001", "15077760-0"),
            listOf("TRINTA E UM", "31 DE MARCO"),
        ),
        ;

        /** O nome do arquivo cita esta congregação ("2.1 Donativo - Japiim", "2 Donativo Cong 31 de Março")? */
        fun isNamedIn(fileName: String): Boolean {
            val name = Months.plain(fileName).replace(Regex("""[^A-Z0-9]+"""), " ")
            return fileNameMarkers.any { Regex("""\b$it\b""").containsMatchIn(name) }
        }
    }

    // ---- Um comprovante ----

    /**
     * O que foi lido de um comprovante.
     *
     * @param congregation de quem é (pela origem: nome ou conta); `null` se não foi reconhecida.
     * @param missingData dados do destino (e da origem, quando a congregação foi reconhecida) não achados.
     */
    data class Receipt(
        val fileName: String,
        val congregation: Congregation?,
        val date: LocalDate?,
        val time: LocalTime?,
        val amount: BigDecimal?,
        val missingData: List<String>,
        val fromOcr: Boolean,
    ) {
        val isComplete: Boolean
            get() = congregation != null && date != null && time != null && amount != null && missingData.isEmpty()
    }

    fun read(fileName: String, content: DocumentContent): Receipt {
        val text = content.pageTexts.joinToString("\n")
        val page = words(text)
        val fromOcr = content.ocrPages.isNotEmpty()
        val congregation = Congregation.entries
            .map { it to originScore(page, it.origin, fromOcr) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first
        val missing = missingData(page, destination, fromOcr).map { "destino $it" } +
                congregation?.let { c -> missingData(page, c.origin, fromOcr).map { "origem $it" } }.orEmpty()
        val (date, time) = findDateTime(text)
        return Receipt(fileName, congregation, date, time, findAmount(text), missing, fromOcr)
    }

    /** Já dá para dar a resposta sem novas leituras do OCR? Só se tudo foi lido. */
    fun hasEnoughText(content: DocumentContent): Boolean = read("", content).isComplete

    /** Quantos dados identificam a origem: nome e conta (a instituição e a agência são iguais nas duas). */
    private fun originScore(page: List<String>, origin: Party, fromOcr: Boolean): Int =
        listOf(hasName(page, origin), hasAccount(page, origin.account, fromOcr)).count { it }

    private fun missingData(page: List<String>, party: Party, fromOcr: Boolean): List<String> {
        val hasName = hasName(page, party)
        val hasAccount = hasAccount(page, party.account, fromOcr)
        return buildList {
            if (!hasName) add("nome ${party.name}")
            val cpfRead = hasDigits(page, words(party.cpf), fromOcr)
            if (!cpfRead && !(fromOcr && hasName && hasAccount)) add("CPF ***.${party.cpf}-**")
            if (page.findPhrase(words(party.institution)).none()) add(party.institution)
            if (!hasBranch(page, party.branch, fromOcr)) add("Agência ${party.branch}")
            if (!hasAccount) add("Conta ${party.account}")
            party.accountType?.let { type -> if (page.findPhrase(words(type)).none()) add(type) }
        }
    }

    private fun hasName(page: List<String>, party: Party): Boolean = page.findPhrase(words(party.name)).any()

    /** "286256-5": o número antes do dígito; o OCR às vezes separa ("15077 760-0"), por isso junta vizinhos. */
    private fun hasAccount(page: List<String>, account: String, fromOcr: Boolean): Boolean {
        val number = words(account).first()
        val joined = page.zipWithNext { a, b -> a + b }
        return (page + joined).any { sameNumber(it, number, fromOcr) }
    }

    /** "Agência 3943": o número logo depois da palavra (o "0001" sozinho também está no CNPJ do Nubank). */
    private fun hasBranch(page: List<String>, branch: String, fromOcr: Boolean): Boolean =
        page.zipWithNext().any { (label, value) ->
            FuzzyText.similar(label, "AGENCIA", inPhrase = false) && sameNumber(value, branch, fromOcr)
        }

    /** CPF mascarado: os dois grupos do meio ("638", "182") em sequência. */
    private fun hasDigits(page: List<String>, groups: List<String>, fromOcr: Boolean): Boolean =
        (0..page.size - groups.size).any { start ->
            groups.indices.all { sameNumber(page[start + it], groups[it], fromOcr) }
        }

    /** Número igual; na foto, com as letras que o OCR põe no lugar de dígitos ("1B2", "O0oL") e um dígito errado. */
    private fun sameNumber(found: String, expected: String, fromOcr: Boolean): Boolean {
        if (found == expected) return true
        if (!fromOcr || found.length != expected.length) return false
        val digits = found.map { ocrDigits[it] ?: it }
        return digits.indices.count { digits[it] != expected[it] } <= 1
    }

    private val ocrDigits =
        mapOf('O' to '0', 'I' to '1', 'L' to '1', 'T' to '1', 'B' to '8', 'S' to '5', 'G' to '6', 'Z' to '2')

    // ---- Data, hora e valor ----

    private val monthAbbreviations =
        listOf("JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ")

    /**
     * "14 NOV 2025 - 10:05:36". O OCR lê o dia como "O9", "Tl", "TI" e às vezes estraga a hora ("10725:39"):
     * aí só a data vale.
     */
    private val dateTime = Regex(
        """(?<![A-Z0-9])([0-9ILT]{1,2}) (${monthAbbreviations.joinToString("|")}) (20\d{2})""" +
                """(?:[^0-9A-Z]{0,6}(\d{1,2})[:;](\d{2})(?:[:;](\d{2}))?)?""",
    )

    internal fun findDateTime(text: String): Pair<LocalDate?, LocalTime?> {
        val match = dateTime.find(Months.plain(text)) ?: return null to null
        val g = match.groupValues
        val day = g[1].map { ocrDigits[it] ?: it }.joinToString("").toIntOrNull() ?: return null to null
        val date = try {
            LocalDate.of(g[3].toInt(), monthAbbreviations.indexOf(g[2]) + 1, day)
        } catch (_: DateTimeException) {
            return null to null
        }
        val time = if (g[4].isEmpty()) null else try {
            LocalTime.of(g[4].toInt(), g[5].toInt(), g[6].toIntOrNull() ?: 0)
        } catch (_: DateTimeException) {
            null
        }
        return date to time
    }

    /** "Valor R$ 800,00" (OCR: "Valor RS 800,00", "Valor R&S 700,00", "Valor . R$ 800,00"). */
    private val amountLine = Regex("""VALOR[^0-9]{0,8}${Money.PATTERN}""")

    internal fun findAmount(text: String): BigDecimal? =
        amountLine.find(Months.plain(text))?.let { Money.amount(it.groupValues[1], it.groupValues[2]) }

    // ---- A categoria inteira ----

    /**
     * Confere os comprovantes da categoria juntos (quantidade, uma de cada congregação, ordem pela data e hora).
     *
     * @param files nome do arquivo e o conteúdo lido; conteúdo `null` = PDF que não abre.
     * @param month mês em edição; `null` = não confere o mês.
     * @return resultado e pendência de cada arquivo, pelo nome.
     */
    fun check(files: List<Pair<String, DocumentContent?>>, month: YearMonth?): Map<String, BankStatementRules.Result> {
        val receipts = files.mapNotNull { (name, content) -> content?.let { read(name, it) } }
        val setNotes = setNotes(receipts)
        return files.associate { (name, content) ->
            val receipt = receipts.firstOrNull { it.fileName == name }
            name to if (receipt == null) {
                BankStatementRules.Result(ExpenseCheck(unreadable = true), ExpenseDocumentRules.UNREADABLE_NOTE)
            } else result(receipt, month, setNotes[name].orEmpty())
        }
    }

    private fun result(receipt: Receipt, month: YearMonth?, setNotes: List<String>): BankStatementRules.Result {
        val ocr = receipt.fromOcr
        val wrongMonth = month != null && receipt.date?.let(YearMonth::from) != month
        val wrongAmount = receipt.amount?.compareTo(AMOUNT) != 0
        val namedAs = Congregation.entries.filter { it.isNamedIn(receipt.fileName) }
        val wrongName = receipt.congregation != null && namedAs.size == 1 && namedAs.single() != receipt.congregation

        val missing = buildSet {
            if (receipt.congregation == null || receipt.missingData.isNotEmpty()) add(ExpenseRequirement.DONATION_DATA)
            if (wrongMonth) add(ExpenseRequirement.DONATION_MONTH)
            if (wrongAmount) add(ExpenseRequirement.DONATION_AMOUNT)
            if (wrongName || setNotes.isNotEmpty()) add(ExpenseRequirement.DONATION_SET)
        }
        val notes = buildList {
            if (receipt.congregation == null) add(originNote(ocr))
            if (receipt.missingData.isNotEmpty()) add(dataNote(receipt.missingData, ocr))
            if (wrongMonth) add(monthNote(receipt.date, month, ocr))
            if (wrongAmount) add(amountNote(receipt.amount, ocr))
            if (wrongName) add(fileNameNote(namedAs.single(), receipt.congregation!!))
            addAll(setNotes)
        }
        val evidence = if (missing.isEmpty()) evidence(receipt) else null
        return BankStatementRules.Result(
            ExpenseCheck(missing, amountEvidence = evidence),
            notes.takeIf { it.isNotEmpty() }?.joinToString(NOTE_SEPARATOR),
        )
    }

    /** Pendências da categoria inteira, por arquivo: falta comprovante, congregação repetida, ordem errada. */
    private fun setNotes(receipts: List<Receipt>): Map<String, List<String>> {
        val notes = receipts.associate { it.fileName to mutableListOf<String>() }
        val known = receipts.mapNotNull { it.congregation }
        when {
            receipts.size > 2 -> receipts.forEach { notes.getValue(it.fileName) += tooManyNote(receipts.size) }
            receipts.size == 1 -> {
                val other = known.singleOrNull()?.let { c -> Congregation.entries.single { it != c } }
                notes.getValue(receipts.single().fileName) += missingReceiptNote(other)
            }

            receipts.size == 2 && known.size == 2 && known.toSet().size == 1 -> {
                val other = Congregation.entries.single { it != known.first() }
                receipts.forEach { notes.getValue(it.fileName) += sameCongregationNote(known.first(), other) }
            }

            receipts.size == 2 -> orderNotes(receipts).forEach { (name, note) -> notes.getValue(name) += note }
        }
        return notes
    }

    /** A primeira transferência é o "2.", a segunda o "2.1"; sem data e hora das duas, a ordem não é conferida. */
    private fun orderNotes(receipts: List<Receipt>): List<Pair<String, String>> {
        val moments = receipts.map { r -> r.date?.let { d -> r.time?.let { LocalDateTime.of(d, it) } } }
        val sameDay = receipts.map { it.date }.distinct().size == 1
        val order = when {
            receipts.any { it.date == null } -> return emptyList()
            !sameDay -> receipts.sortedBy { it.date }
            moments.any { it == null } || moments[0] == moments[1] -> return emptyList()
            else -> receipts.sortedBy { r -> LocalDateTime.of(r.date, r.time) }
        }
        return order.mapIndexedNotNull { position, receipt ->
            val expected = if (position == 0) "2." else "2.1"
            if (subNumber(receipt.fileName) == position) null
            else receipt.fileName to orderNote(receipt, position, expected)
        }
    }

    /** "2. Donativo …" / "2 Donativo …" = 0; "2.1 …" / "2. 1 …" = 1; outro número = `null`. */
    internal fun subNumber(fileName: String): Int? {
        val match = Regex("""^\s*2\s*(?:\.\s*(\d+))?""").find(fileName) ?: return null
        return match.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
    }

    // ---- Pendência ----

    private const val NOTE_SEPARATOR = "; "
    private const val ORIGIN_PREFIX = "Origem da transferência não é"
    private const val ORIGIN_OCR_PREFIX = "Origem da transferência não lida na foto"
    private const val DATA_PREFIX = "Comprovante sem os dados do donativo"
    private const val DATA_OCR_PREFIX = "Dados do donativo não confirmados na foto"
    private const val DATE_UNREADABLE = "Data da transferência não foi lida"
    private const val AMOUNT_UNREADABLE = "Valor da transferência não foi lido"
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    private val congregationList = Congregation.entries.joinToString(" e ") { it.displayName }

    private fun list(items: List<String>): String =
        if (items.size == 1) items.single() else items.dropLast(1).joinToString(", ") + " e " + items.last()

    private fun originNote(ocr: Boolean): String =
        if (ocr) "$ORIGIN_OCR_PREFIX: confira à mão se é de $congregationList"
        else "$ORIGIN_PREFIX " + Congregation.entries.joinToString(" nem ") {
            "${it.displayName} (${it.origin.name}, Conta ${it.origin.account})"
        } + ": troque pelo comprovante do donativo"

    private fun dataNote(missing: List<String>, ocr: Boolean): String =
        if (ocr) "$DATA_OCR_PREFIX (não li ${list(missing)}): confira à mão"
        else "$DATA_PREFIX (não tem ${list(missing)}): troque pelo comprovante certo"

    private fun monthNote(date: LocalDate?, month: YearMonth?, ocr: Boolean): String {
        if (date == null) return "$DATE_UNREADABLE: confira à mão se é de ${Months.name(month)}"
        val action = if (ocr) "confira à mão (foto)" else "troque pelo comprovante do mês"
        return "Transferência de ${date.format(dateFormat)}, mas o mês em edição é ${Months.name(month)}: $action"
    }

    private fun amountNote(amount: BigDecimal?, ocr: Boolean): String {
        val expected = "R$ ${Money.format(AMOUNT)}"
        if (amount == null) return "$AMOUNT_UNREADABLE: confira à mão se é $expected"
        val action = if (ocr) "confira à mão (foto)" else "confira o valor"
        return "Transferência de R$ ${Money.format(amount)}, mas o donativo é $expected: $action"
    }

    private fun fileNameNote(named: Congregation, actual: Congregation): String =
        "O nome do arquivo diz ${named.displayName}, mas o comprovante é de ${actual.displayName} " +
                "(${actual.origin.name}): renomeie o arquivo"

    private fun tooManyNote(count: Int): String =
        "$count comprovantes na categoria: devem ser só dois ($congregationList)"

    private fun missingReceiptNote(other: Congregation?): String =
        if (other != null) "Falta o comprovante do donativo de ${other.displayName}"
        else "Falta o segundo comprovante de transferência ($congregationList)"

    private fun sameCongregationNote(found: Congregation, other: Congregation): String =
        "Os dois comprovantes são de ${found.displayName}: falta o de ${other.displayName}"

    private fun orderNote(receipt: Receipt, position: Int, expected: String): String {
        val moment = listOfNotNull(receipt.date?.format(dateFormat), receipt.time?.format(timeFormat)).joinToString(" ")
        val which = if (position == 0) "primeira" else "segunda"
        return "Transferência de $moment é a $which do mês: o arquivo deve começar com \"$expected\""
    }

    /** "Japiim: 10/09/2026 10:36, R$ 800,00, de Walter Santana Mota do Rosário para Danilo Cavalcante Braga". */
    private fun evidence(receipt: Receipt): String {
        val congregation = receipt.congregation!!
        val moment = listOfNotNull(receipt.date?.format(dateFormat), receipt.time?.format(timeFormat)).joinToString(" ")
        return "${congregation.displayName}: $moment, R$ ${Money.format(receipt.amount!!)}, " +
                "de ${congregation.origin.name} para ${destination.name}"
    }

    /** A pendência foi escrita por esta conferência? As do usuário nunca são mexidas. */
    fun isAutomaticNote(note: String): Boolean =
        note.split(NOTE_SEPARATOR).all { part -> automaticNotes.any { it.matches(part) } }

    private val automaticNotes = listOf(
        Regex("""${Regex.escape(ORIGIN_PREFIX)} .+: troque pelo comprovante do donativo"""),
        Regex("""${Regex.escape(ORIGIN_OCR_PREFIX)}: confira à mão se é de .+"""),
        Regex("""${Regex.escape(DATA_PREFIX)} \(não tem .+\): troque pelo comprovante certo"""),
        Regex("""${Regex.escape(DATA_OCR_PREFIX)} \(não li .+\): confira à mão"""),
        Regex("""${Regex.escape(DATE_UNREADABLE)}: confira à mão se é de .+"""),
        Regex("""Transferência de \d{2}/\d{2}/\d{4}, mas o mês em edição é .+: (confira à mão \(foto\)|troque pelo comprovante do mês)"""),
        Regex("""${Regex.escape(AMOUNT_UNREADABLE)}: confira à mão se é R\$ .+"""),
        Regex("""Transferência de R\$ .+, mas o donativo é R\$ .+: (confira à mão \(foto\)|confira o valor)"""),
        Regex("""O nome do arquivo diz .+, mas o comprovante é de .+: renomeie o arquivo"""),
        Regex("""\d+ comprovantes na categoria: devem ser só dois \(.+\)"""),
        Regex("""Falta o comprovante do donativo de .+"""),
        Regex("""Falta o segundo comprovante de transferência \(.+\)"""),
        Regex("""Os dois comprovantes são de .+: falta o de .+"""),
        Regex("""Transferência de [\d/: ]+ é a (primeira|segunda) do mês: o arquivo deve começar com ".+""""),
        Regex(Regex.escape(ExpenseDocumentRules.UNREADABLE_NOTE)),
    )
}
