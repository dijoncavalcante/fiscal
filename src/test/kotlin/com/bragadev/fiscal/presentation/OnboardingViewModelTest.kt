package com.bragadev.fiscal.presentation

import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.usecase.UpdateSettingsUseCase
import com.bragadev.fiscal.fakes.FakeSettingsRepository
import com.bragadev.fiscal.presentation.onboarding.OnboardingStep
import com.bragadev.fiscal.presentation.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val settings = FakeSettingsRepository(AppSettings())
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = OnboardingViewModel(UpdateSettingsUseCase(settings))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `tres passos na ordem e voltar`() {
        assertEquals(OnboardingStep.MONTHS_ROOT, viewModel.uiState.value.step)
        viewModel.onBack()
        assertEquals(OnboardingStep.MONTHS_ROOT, viewModel.uiState.value.step)
        viewModel.onNext()
        assertEquals(OnboardingStep.SOURCE_FOLDER, viewModel.uiState.value.step)
        viewModel.onNext()
        assertEquals(OnboardingStep.MONTH, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.isLast)
        viewModel.onNext()
        assertEquals(3, viewModel.uiState.value.stepNumber)
        viewModel.onBack()
        assertEquals(OnboardingStep.SOURCE_FOLDER, viewModel.uiState.value.step)
    }

    @Test
    fun `concluir ou pular grava e nao abre mais sozinho`() {
        var done = false
        viewModel.onFinish { done = true }
        assertTrue(done)
        assertTrue(settings.settings.value.onboardingDone)
        assertEquals(false, settings.settings.value.needsOnboarding)
    }

    @Test
    fun `reabrir pelas configuracoes comeca do primeiro passo`() {
        viewModel.onNext()
        viewModel.onNext()
        viewModel.onStart()
        assertEquals(OnboardingStep.MONTHS_ROOT, viewModel.uiState.value.step)
    }
}
