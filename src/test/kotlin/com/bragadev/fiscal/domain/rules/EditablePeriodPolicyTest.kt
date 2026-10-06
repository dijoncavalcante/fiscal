package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.MonthFolderStatus
import org.junit.Test
import java.nio.file.Path
import java.time.YearMonth
import kotlin.test.assertEquals

/** Mês de corte configurável: vale na hora, sem nova versão. */
class EditablePeriodPolicyTest {
    private val year = Path.of("D:", "pendriver", "CONTAS CONGREGAÇÃO", "ANO DE SERVIÇO 2025-2026")
    private val may = year.resolve("3. TRIMESTRE Mar-Abr-Mai").resolve("3. MAIO")
    private val june = year.resolve("4. TRIMESTRE Jun-Jul-Ago").resolve("1. JUNHO")

    @Test
    fun `mudar o mes de corte trava e destrava na hora`() {
        var cutoff = YearMonth.of(2026, 6)
        val policy = EditablePeriodPolicy { cutoff }
        assertEquals(MonthFolderStatus.LOCKED, policy.describe(may).status)
        assertEquals(MonthFolderStatus.EDITABLE, policy.describe(june).status)

        // Prestação de contas de junho entregue: o corte passa para julho.
        cutoff = YearMonth.of(2026, 7)
        assertEquals(MonthFolderStatus.LOCKED, policy.describe(june).status)
        assertEquals(YearMonth.of(2026, 7), policy.firstEditableMonth)

        // Voltar atrás também funciona (com confirmação na tela).
        cutoff = YearMonth.of(2026, 5)
        assertEquals(MonthFolderStatus.EDITABLE, policy.describe(may).status)
    }

    @Test
    fun `padrao continua junho de 2026`() {
        assertEquals(YearMonth.of(2026, 6), EditablePeriodPolicy().firstEditableMonth)
    }
}
