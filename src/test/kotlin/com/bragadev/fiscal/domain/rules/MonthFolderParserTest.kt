package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.MonthFolderStatus
import org.junit.Test
import java.nio.file.Path
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Usa nomes reais das pastas do pendrive. */
class MonthFolderParserTest {
    private val base = Path.of("D:", "Modelo", "jw", "pendriver")
    private val congregacao = base.resolve("CONTAS CONGREGAÇÃO")
    private val manutencao = base.resolve("CONTAS MANUTENÇÃO")
    private val policy = EditablePeriodPolicy(YearMonth.of(2026, 6))

    private fun month(path: Path) = MonthFolderParser.detectMonth(path)?.month

    @Test
    fun `ano de servico divide setembro a dezembro e janeiro a agosto`() {
        val year = congregacao.resolve("ANO DE SERVIÇO 2025-2026")
        assertEquals(YearMonth.of(2026, 6), month(year.resolve("4. TRIMESTRE Jun-Jul-Ago").resolve("1. JUNHO")))
        assertEquals(YearMonth.of(2025, 12), month(year.resolve("2. TRIMESTRE Dez-Jan-Fev").resolve("1.DEZEMBRO")))
        assertEquals(YearMonth.of(2026, 1), month(year.resolve("2. TRIMESTRE Dez-Jan-Fev").resolve("2.JANEIRO")))
        assertEquals(YearMonth.of(2025, 9), month(year.resolve("1. TRIMESTRE - Set-Out-Nov").resolve("SETEMBRO")))
    }

    @Test
    fun `quinto trimestre pertence ao ano de servico seguinte`() {
        val quarter = congregacao.resolve("ANO DE SERVIÇO 2025-2026").resolve("5. Trimestre Set-Out-Nov")
        assertEquals(YearMonth.of(2026, 9), month(quarter.resolve("1. Setembro")))
        assertEquals(YearMonth.of(2026, 10), month(quarter.resolve("2.  Outubro")))
        assertEquals(MonthFolderStatus.EDITABLE, policy.describe(quarter.resolve("1. Setembro")).status)
    }

    @Test
    fun `aceita variacoes de nome da pasta do mes`() {
        val year = congregacao.resolve("ANO DE SERVIÇO 2024-2025")
        assertEquals(YearMonth.of(2024, 10), month(year.resolve("1. TRIMESTRE - Set_Out_Nov").resolve("10.Outubro")))
        assertEquals(YearMonth.of(2025, 8), month(year.resolve("4. TRIMESTRE - Jun-Jul-Agos").resolve("AGOSTO")))
        assertEquals(YearMonth.of(2025, 3), month(year.resolve("3. TRIMESTRE - Mar_Abr_Mai").resolve("3. Março")))
        val manut = manutencao.resolve("ANO DE SERVIÇO 2025-2026").resolve("5. Trimestre Set-Out-Nov").resolve("2.  Outubro")
        assertEquals(YearMonth.of(2026, 10), month(manut))
    }

    @Test
    fun `ano no nome do mes tem prioridade`() {
        val folder = congregacao.resolve("ANO DE SERVIÇO 2023-2024").resolve("3. TRIMESTRE - Março, Abril, Maio").resolve("3. Março 2024")
        assertEquals(YearMonth.of(2024, 3), month(folder))
    }

    @Test
    fun `ano de servico com um unico ano`() {
        val folder = congregacao.resolve("ANO DE SERVIÇO 2023").resolve("2. TRIMESTRE Junho_Julho_Agosto 2023").resolve("1. Junho")
        assertEquals(YearMonth.of(2023, 6), month(folder))
    }

    @Test
    fun `pasta que nao e de mes nao tem mes identificado`() {
        assertNull(month(congregacao.resolve("ANO DE SERVIÇO 2025-2026")))
        assertNull(month(Path.of("C:", "Users", "DIJON", "Downloads")))
    }

    @Test
    fun `identifica a conta pelo caminho`() {
        assertEquals(AccountType.CONGREGACAO, MonthFolderParser.detectAccount(congregacao.resolve("ANO DE SERVIÇO 2025-2026")))
        assertEquals(AccountType.MANUTENCAO, MonthFolderParser.detectAccount(manutencao))
        assertNull(MonthFolderParser.detectAccount(Path.of("C:", "Downloads")))
    }

    @Test
    fun `meses anteriores a junho de 2026 ficam bloqueados`() {
        val year = congregacao.resolve("ANO DE SERVIÇO 2025-2026")
        val maio = year.resolve("3. TRIMESTRE Mar-Abr-Mai").resolve("3. MAIO")
        val junho = year.resolve("4. TRIMESTRE Jun-Jul-Ago").resolve("1. JUNHO")

        assertEquals(MonthFolderStatus.LOCKED, policy.describe(maio).status)
        assertEquals(MonthFolderStatus.EDITABLE, policy.describe(junho).status)
        assertEquals(MonthFolderStatus.UNKNOWN_MONTH, policy.describe(year).status)
        assertEquals(FileOperationError.MonthLocked(YearMonth.of(2026, 5), YearMonth.of(2026, 6)), policy.checkDestination(maio))
        assertEquals(FileOperationError.MonthNotIdentified, policy.checkDestination(year))
    }

    @Test
    fun `origem fora de pasta de mes e permitida`() {
        assertNull(policy.checkSource(Path.of("C:", "Users", "DIJON", "Downloads")))
    }
}
