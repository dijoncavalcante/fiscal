package com.bragadev.fiscal.domain.rules

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data da transferência num comprovante Pix/TED, como os bancos das contas escrevem (lido nas despesas reais de
 * 2025-2026):
 * - next/Bradesco: "Data da operação: 18/09/2026 19:42:21" (OCR: "Data da operacio", "operagao");
 * - Nubank: "10 AGO 2026 - 10:30:32" ([DonationRules.findDateTime]);
 * - outros: "Data do pagamento Sábado, 01/11/2025", "Data da transferência/transação: …".
 *
 * Outras datas da página **não** valem: a do selo gov.br ("Data: 12/08/2026 07:38:13-0300" é quando assinaram), a
 * expiração do QR Code do Pix, o vencimento da fatura. Sem nenhuma dessas, vale a data dentro do identificador do
 * Pix (E2E: "E" + 8 dígitos do banco + aaaaMMddHHmm, em UTC), que o OCR pode ler errado.
 */
internal object TransferDates {
    /** Data do comprovante e de onde veio, para a pendência. */
    data class Found(val date: LocalDate, val fromPixId: Boolean)

    /** "18/07/2026"; o OCR às vezes perde as barras ("18072026"). */
    private const val DATE = """(\d{2})\s?/?\s?(\d{2})\s?/?\s?(\d{4})"""

    /**
     * "DATA DA OPERACAO:", "DATA DO PAGAMENTO SABADO,", "DATA DA TRANSFERENCIA" — até 25 letras até a data. O OCR
     * troca letras do rótulo ("Date da operaeha", "Data di opersedat").
     */
    private val labeled =
        Regex("""DAT[AE] D[AOI] (?:OPER|PAGAMENTO|TRANSFER|TRANSAC)[^0-9]{0,25}$DATE(?!\d)""")

    private val pixId = Regex("""\bE\d{8}(20\d{2})(\d{2})(\d{2})(\d{2})(\d{2})""")
    private val pixIdGlued = Regex("""E\d{8}(20\d{2})(\d{2})(\d{2})(\d{2})(\d{2})""")

    /** Fuso dos comprovantes (o identificador do Pix vem em UTC). */
    private const val UTC_OFFSET_HOURS = 3L

    /** Data da transferência na página do comprovante; `null` se não foi lida. */
    fun find(pageText: String): Found? {
        val plain = Months.plain(pageText)
        labeled.find(plain)?.let { match ->
            val g = match.groupValues
            date(g[1], g[2], g[3])?.let { return Found(it, fromPixId = false) }
        }
        DonationRules.findDateTime(pageText).first?.let { return Found(it, fromPixId = false) }
        // O OCR às vezes separa o identificador ("E18236120202511141305s192af1 aObf") ou cola no rótulo.
        val id = pixId.find(plain) ?: pixIdGlued.find(plain.replace(" ", "")) ?: return null
        val g = id.groupValues.drop(1).map(String::toInt)
        return try {
            Found(LocalDateTime.of(g[0], g[1], g[2], g[3], g[4]).minusHours(UTC_OFFSET_HOURS).toLocalDate(), true)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun date(day: String, month: String, year: String): LocalDate? =
        try {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        } catch (_: DateTimeException) {
            null
        }

    val format: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
}
