package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.domain.model.DuplicateResolution
import org.junit.Test
import kotlin.test.assertEquals

class ConflictPolicyTest {
    @Test
    fun `perguntar sempre exige escolha do usuario`() {
        assertEquals(ConflictDecision.NeedsUserChoice, ConflictPolicy.decide(DuplicatePolicy.ASK, null))
        assertEquals(ConflictDecision.Replace, ConflictPolicy.decide(DuplicatePolicy.ASK, DuplicateResolution.REPLACE))
        assertEquals(ConflictDecision.NumberedCopy, ConflictPolicy.decide(DuplicatePolicy.ASK, DuplicateResolution.NUMBERED_COPY))
        assertEquals(ConflictDecision.Cancel, ConflictPolicy.decide(DuplicatePolicy.ASK, DuplicateResolution.CANCEL))
    }

    @Test
    fun `copia automatica nunca substitui`() {
        DuplicateResolution.entries.forEach { choice ->
            assertEquals(ConflictDecision.NumberedCopy, ConflictPolicy.decide(DuplicatePolicy.AUTO_NUMBERED_COPY, choice))
        }
    }

    @Test
    fun `nao permitir duplicados bloqueia qualquer escolha`() {
        (DuplicateResolution.entries + null).forEach { choice ->
            assertEquals(ConflictDecision.Forbidden, ConflictPolicy.decide(DuplicatePolicy.FORBID, choice))
        }
    }
}
