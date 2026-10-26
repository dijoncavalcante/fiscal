package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "2. Donativos das Congregações" (Conta da Manutenção), com os textos como o OCR lê os comprovantes reais do
 * Nubank de 2025-2026 (todos digitalizados). De março a agosto/2026 os 12 comprovantes reais passam.
 */
class DonationRulesTest {
    private val august = YearMonth.of(2026, 8)

    /** "2. Donativo - Japiim.pdf" de agosto/2026, como o OCR leu. */
    private val japiim = DocumentContent(
        listOf(
            """
            NU,
            Comprovante de transferéncia
            10 AGO 2026 - 10:18:09
            Valor RS 800,00
            Tipo de transferéncia Pix
            Destino
            Nome Danilo Cavalcante Braga
            CPF 1,638,182 -+
            Instituigao BCO BRADESCO S.A.
            Agéncia 3943
            Conta 286256-5
            Tipo de conta Conta poupan¢a
            Origem
            Nome Walter Santana Mota do Rosario
            Instituigao NU PAGAMENTOS - IP
            Agéncia 0001
            Conta 18938180-7
            CPF ++.250.412-+
            Nu Pagamentos S.A. - Instituic¢ao de Pagamento
            CNPJ 18.236.120/0001-58
            """.trimIndent(),
        ),
        ocrPages = setOf(0),
    )

    /** "2.1 Donativo - Trinta e um de março.pdf" de agosto/2026 ("BRADESCO $.A.", CPF "360.732"). */
    private val trintaEUm = DocumentContent(
        listOf(
            """
            Comprovante de
            transferéncia
            10 AGO 2026 - 10:30:32
            Valor RS 800,00
            Tipo de transferéncia Pix
            Destino
            Nome Danilo Cavalcante Braga
            CPF + 698.182- +
            Instituigao BCO BRADESCO ${'$'}.A.
            Agéncia 3943
            Conta 286256-5
            Tipo de conta Conta poupanca
            Origem
            Nome Filipe da Silva Costa
            Instituigao NU PAGAMENTOS - IP
            Agéncia 0001
            Conta 15077760-0
            CPF ++ 360.732-+
            Nu Pagamentos ${'$'}.A. - Instituigao de
            CNPJ 18.236.120/0001-58
            """.trimIndent(),
        ),
        ocrPages = setOf(0),
    )

    private fun check(vararg files: Pair<String, DocumentContent?>, month: YearMonth? = august) =
        DonationRules.check(files.toList(), month)

    @Test
    fun `dois comprovantes do mes, um de cada congregacao, na ordem da transferencia`() {
        val results =
            check("2. Donativo - Japiim.pdf" to japiim, "2.1 Donativo - Trinta e um de março.pdf" to trintaEUm)
        results.values.forEach { assertTrue(it.check.isValid, it.note) }
        assertEquals(
            "Japiim: 10/08/2026 10:18, R$ 800,00, de Walter Santana Mota do Rosário para Danilo Cavalcante Braga",
            results.getValue("2. Donativo - Japiim.pdf").check.amountEvidence,
        )
    }

    @Test
    fun `numeracao fora da ordem da transferencia diz qual numero cada arquivo deve ter`() {
        val results =
            check("2. Donativo - Trinta e um de março.pdf" to trintaEUm, "2.1 Donativo - Japiim.pdf" to japiim)
        assertEquals(
            "Transferência de 10/08/2026 10:30 é a segunda do mês: o arquivo deve começar com \"2.1\"",
            results.getValue("2. Donativo - Trinta e um de março.pdf").note,
        )
        assertEquals(
            "Transferência de 10/08/2026 10:18 é a primeira do mês: o arquivo deve começar com \"2.\"",
            results.getValue("2.1 Donativo - Japiim.pdf").note,
        )
        assertEquals(setOf(ExpenseRequirement.DONATION_SET), results.values.first().check.missing)
        results.values.forEach { assertTrue(DonationRules.isAutomaticNote(it.note!!)) }
    }

    @Test
    fun `falta o comprovante da outra congregacao`() {
        val alone = check("2. Donativo - Japiim.pdf" to japiim).values.single()
        assertEquals("Falta o comprovante do donativo de Trinta e Um de Março", alone.note)

        val twice = check("2. Donativo - Japiim.pdf" to japiim, "2.1 Donativo - Japiim (2).pdf" to japiim)
        assertEquals(
            "Os dois comprovantes são de Japiim: falta o de Trinta e Um de Março",
            twice.getValue("2. Donativo - Japiim.pdf").note,
        )
        assertTrue(DonationRules.isAutomaticNote(alone.note!!))
    }

    @Test
    fun `nome do arquivo com a congregacao errada pede para renomear`() {
        val results =
            check("2. Donativo - Trinta e um de março.pdf" to japiim, "2.1 Donativo - Japiim.pdf" to trintaEUm)
        assertEquals(
            "O nome do arquivo diz Trinta e Um de Março, mas o comprovante é de Japiim " +
                    "(Walter Santana Mota do Rosário): renomeie o arquivo",
            results.getValue("2. Donativo - Trinta e um de março.pdf").note,
        )
    }

    @Test
    fun `transferencia de outro mes ou de outro valor`() {
        val september = check(
            "2. Donativo - Japiim.pdf" to japiim, "2.1 Donativo - Trinta.pdf" to trintaEUm,
            month = YearMonth.of(2026, 9)
        )
        assertEquals(
            "Transferência de 10/08/2026, mas o mês em edição é setembro de 2026: confira à mão (foto)",
            september.getValue("2. Donativo - Japiim.pdf").note,
        )

        // PDF com texto (sem OCR): aí dá para afirmar.
        val text = japiim.copy(
            pageTexts = listOf(japiim.pageTexts.single().replace("RS 800,00", "R$ 700,00")),
            ocrPages = emptySet(),
        )
        val result = check("2. Donativo - Japiim.pdf" to text, "2.1 Donativo - Trinta.pdf" to trintaEUm)
            .getValue("2. Donativo - Japiim.pdf")
        assertEquals(setOf(ExpenseRequirement.DONATION_AMOUNT), result.check.missing)
        assertEquals("Transferência de R$ 700,00, mas o donativo é R$ 800,00: confira o valor", result.note)
        assertTrue(DonationRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `destino errado no PDF com texto pede o comprovante certo`() {
        val other = japiim.copy(
            pageTexts = listOf(japiim.pageTexts.single().replace("286256-5", "112233-4")),
            ocrPages = emptySet(),
        )
        val result = check("2. Donativo - Japiim.pdf" to other, "2.1 Donativo - Trinta.pdf" to trintaEUm)
            .getValue("2. Donativo - Japiim.pdf")
        assertEquals(setOf(ExpenseRequirement.DONATION_DATA), result.check.missing)
        assertEquals(
            "Comprovante sem os dados do donativo (não tem destino Conta 286256-5): troque pelo comprovante certo",
            result.note,
        )
    }

    @Test
    fun `transferencia de outra pessoa nao e donativo`() {
        val stranger = DocumentContent(
            listOf(
                japiim.pageTexts.single()
                    .replace("Walter Santana Mota do Rosario", "Maria Souza")
                    .replace("18938180-7", "55555555-5")
            ),
        )
        val result = check("2. Donativo - Japiim.pdf" to stranger).getValue("2. Donativo - Japiim.pdf")
        assertEquals(
            "Origem da transferência não é Japiim (Walter Santana Mota do Rosário, Conta 18938180-7) nem " +
                    "Trinta e Um de Março (Filipe da Silva Costa, Conta 15077760-0): troque pelo comprovante do donativo; " +
                    "Falta o segundo comprovante de transferência (Japiim e Trinta e Um de Março)",
            result.note,
        )
        assertTrue(DonationRules.isAutomaticNote(result.note!!))
    }

    /** "2.1 Donativo Cong Japiim.pdf" de setembro/2026: foto borrada, o OCR leu "Valor Ag 600,00". */
    @Test
    fun `foto borrada nunca pede a troca`() {
        val blurred = DocumentContent(
            listOf(
                """
                Comprovante de
                transferéncia
                TI SET 2026:-- 10725:39
                Valor Ag 600,00
                esting
                Nome Danilo Cavalcante Braga
                CPF 1 G05,192 -++
                Instituig¢ao ACO BRADESCO S.4,
                Agéncia 3943
                Gonta 286256-5
                Tipo de conta Conia poupanga
                Origen
                Nome Walter Santana Mote do Rosane
                Instituiggo NU PAGAMENTOS IP
                Agéncia O0oL
                Conta 1893A180-7
                CPF 1,250,412 -+
                """.trimIndent(),
            ),
            ocrPages = setOf(0),
        )
        val sept = YearMonth.of(2026, 9)
        val trinta = DocumentContent(
            listOf(trintaEUm.pageTexts.single().replace("10 AGO 2026 - 10:30:32", "10 SET 2026 - 10:36:07")),
            ocrPages = setOf(0),
        )
        val results =
            check("2 Donativo Cong 31 de Março.pdf" to trinta, "2.1 Donativo Cong Japiim.pdf" to blurred, month = sept)
        assertTrue(results.getValue("2 Donativo Cong 31 de Março.pdf").check.isValid)
        assertEquals(
            "Dados do donativo não confirmados na foto (não li origem nome Walter Santana Mota do Rosário): " +
                    "confira à mão; Transferência de R$ 600,00, mas o donativo é R$ 800,00: confira à mão (foto)",
            results.getValue("2.1 Donativo Cong Japiim.pdf").note,
        )
    }

    @Test
    fun `PDF que nao abre vira pendencia`() {
        val result = check("2. Donativo - Japiim.pdf" to japiim, "2.1 Donativo - Trinta.pdf" to null)
        assertTrue(result.getValue("2.1 Donativo - Trinta.pdf").check.unreadable)
        assertEquals(
            "Falta o comprovante do donativo de Trinta e Um de Março",
            result.getValue("2. Donativo - Japiim.pdf").note
        )
    }

    @Test
    fun `data e hora como o OCR le`() {
        assertEquals(
            LocalDate.of(2026, 5, 11) to LocalTime.of(14, 23, 10),
            DonationRules.findDateTime("Tl MAI 2026 - 14:23:10")
        )
        assertEquals(
            LocalDate.of(2026, 3, 9) to LocalTime.of(11, 24, 54),
            DonationRules.findDateTime("O9 MAR 2026 - 11:24:54")
        )
        assertEquals(
            LocalDate.of(2026, 4, 10) to LocalTime.of(11, 4, 52),
            DonationRules.findDateTime("10 ABR 2026 - 11;04:52")
        )
        assertEquals(LocalDate.of(2026, 9, 11) to null, DonationRules.findDateTime("TI SET 2026:-- 10725:39"))
        assertEquals(null to null, DonationRules.findDateTime("CNPJ 18.236.120/0001-58"))
        assertEquals(BigDecimal("800.00"), DonationRules.findAmount("; Valor R$ 800,00"))
        assertEquals(BigDecimal("700.00"), DonationRules.findAmount("Valor R&S 700,00"))
        assertNull(DonationRules.findAmount("Tipo de transferência Pix"))
    }

    @Test
    fun `numero do arquivo`() {
        assertEquals(0, DonationRules.subNumber("2. Donativo - Japiim.pdf"))
        assertEquals(0, DonationRules.subNumber("2 Donativo Cong Japiim.pdf"))
        assertEquals(1, DonationRules.subNumber("2.1 Donativo Cong 31 de Março.pdf"))
        assertEquals(1, DonationRules.subNumber("2. 1 DONATIVO 31 DE MARÇO.pdf"))
        assertEquals(2, DonationRules.subNumber("2.2 DONATIVO JAPIIM.pdf"))
    }

    @Test
    fun `pendencia do usuario nao e automatica`() {
        assertFalse(DonationRules.isAutomaticNote("Falta o recibo assinado pelo ancião"))
    }
}
