package com.bragadev.fiscal.presentation.onboarding

import com.bragadev.fiscal.domain.usecase.UpdateSettingsUseCase
import com.bragadev.fiscal.presentation.common.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Os três passos do assistente da primeira vez, na ordem. */
enum class OnboardingStep { MONTHS_ROOT, SOURCE_FOLDER, MONTH }

data class OnboardingUiState(val step: OnboardingStep = OnboardingStep.MONTHS_ROOT) {
    val stepNumber: Int get() = step.ordinal + 1
    val isFirst: Boolean get() = step.ordinal == 0
    val isLast: Boolean get() = step.ordinal == OnboardingStep.entries.lastIndex
}

/**
 * Assistente da primeira vez: pasta raiz das contas, pasta de origem e mês.
 * As pastas são escolhidas pelos mesmos ViewModels da tela principal; aqui ficam só os passos
 * e o registro de que o assistente foi concluído (ou pulado), para não aparecer de novo.
 */
class OnboardingViewModel(private val updateSettings: UpdateSettingsUseCase) : ViewModel() {
    private val state = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    /** Reabrir pelas Configurações começa do primeiro passo. */
    fun onStart() {
        state.value = OnboardingUiState()
    }

    fun onNext() {
        state.update { if (it.isLast) it else it.copy(step = OnboardingStep.entries[it.step.ordinal + 1]) }
    }

    fun onBack() {
        state.update { if (it.isFirst) it else it.copy(step = OnboardingStep.entries[it.step.ordinal - 1]) }
    }

    /** Concluir ou pular: o que já foi escolhido fica salvo; o assistente não abre mais sozinho. */
    fun onFinish(onDone: () -> Unit) {
        scope.launch {
            updateSettings { it.copy(onboardingDone = true) }
            onDone()
        }
    }
}
