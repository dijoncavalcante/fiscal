package com.bragadev.fiscal.presentation

import com.bragadev.fiscal.presentation.common.Strings
import org.junit.Test
import java.time.YearMonth
import kotlin.test.assertEquals

/** A confirmação do mês de corte diz exatamente quais meses travam ou destravam. */
class CutoffTextsTest {
    @Test
    fun `avancar o corte avisa os meses que vao travar`() {
        assertEquals(
            "Atenção: Junho de 2026 vai ficar somente leitura.",
            Strings.cutoffLocks(YearMonth.of(2026, 6), YearMonth.of(2026, 7)),
        )
        assertEquals(
            "Atenção: os meses de Junho de 2026 a Maio de 2027 vão ficar somente leitura.",
            Strings.cutoffLocks(YearMonth.of(2026, 6), YearMonth.of(2027, 6)),
        )
    }

    @Test
    fun `voltar o corte avisa os meses que destravam`() {
        assertEquals(
            "Atenção: os meses de Março de 2026 a Maio de 2026 voltam a poder ser alterados.",
            Strings.cutoffUnlocks(YearMonth.of(2026, 3), YearMonth.of(2026, 6)),
        )
    }
}
