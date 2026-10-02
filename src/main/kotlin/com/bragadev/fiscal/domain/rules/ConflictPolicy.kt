package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.domain.model.DuplicateResolution

sealed interface ConflictDecision {
    data object Replace : ConflictDecision
    data object NumberedCopy : ConflictDecision
    data object Cancel : ConflictDecision
    data object NeedsUserChoice : ConflictDecision
    data object Forbidden : ConflictDecision
}

/**
 * Decide como tratar um conflito de nome combinando a configuração com a escolha do usuário.
 *
 * - [DuplicatePolicy.FORBID] nunca permite substituir nem criar cópia.
 * - [DuplicatePolicy.AUTO_NUMBERED_COPY] sempre cria cópia numerada, nunca substitui.
 * - [DuplicatePolicy.ASK] exige uma escolha explícita do usuário; sem ela, nada é feito.
 */
object ConflictPolicy {
    fun decide(policy: DuplicatePolicy, userChoice: DuplicateResolution?): ConflictDecision = when (policy) {
        DuplicatePolicy.FORBID -> ConflictDecision.Forbidden
        DuplicatePolicy.AUTO_NUMBERED_COPY -> ConflictDecision.NumberedCopy
        DuplicatePolicy.ASK -> when (userChoice) {
            null -> ConflictDecision.NeedsUserChoice
            DuplicateResolution.REPLACE -> ConflictDecision.Replace
            DuplicateResolution.NUMBERED_COPY -> ConflictDecision.NumberedCopy
            DuplicateResolution.CANCEL -> ConflictDecision.Cancel
        }
    }
}
