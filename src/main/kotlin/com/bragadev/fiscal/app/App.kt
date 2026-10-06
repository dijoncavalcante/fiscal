package com.bragadev.fiscal.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bragadev.fiscal.domain.usecase.CleanOldBackupsUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.presentation.common.UnexpectedErrorDialog
import com.bragadev.fiscal.presentation.home.HomeScreen
import com.bragadev.fiscal.presentation.onboarding.OnboardingScreen
import com.bragadev.fiscal.presentation.onboarding.OnboardingViewModel
import com.bragadev.fiscal.presentation.settings.SettingsScreen
import com.bragadev.fiscal.presentation.theme.FiscalTheme
import org.koin.compose.koinInject

private enum class Screen { ONBOARDING, HOME, SETTINGS }

@Composable
fun App() {
    val settings by koinInject<ObserveSettingsUseCase>()().collectAsState()
    // As configurações já foram carregadas antes de abrir a janela: o assistente só abre sozinho para usuário novo.
    // A decisão é tomada uma vez, para o assistente não sumir no meio quando o usuário escolhe as pastas.
    var screen by remember { mutableStateOf(if (settings.needsOnboarding) Screen.ONBOARDING else Screen.HOME) }

    // Ao abrir: limpa backups antigos, só se o usuário ligou essa opção nas Configurações.
    val cleanOldBackups = koinInject<CleanOldBackupsUseCase>()
    LaunchedEffect(Unit) { cleanOldBackups.automatic() }

    FiscalTheme(settings.themeMode) {
        UnexpectedErrorDialog()
        Surface(color = MaterialTheme.colorScheme.background) {
            when (screen) {
                Screen.ONBOARDING -> {
                    val onboarding = koinInject<OnboardingViewModel>()
                    OnboardingScreen(
                        viewModel = onboarding,
                        settings = settings,
                        homeViewModel = koinInject(),
                        navigatorViewModel = koinInject(),
                        organizerViewModel = koinInject(),
                        onDone = { screen = Screen.HOME },
                    )
                }
                Screen.HOME -> HomeScreen(
                    homeViewModel = koinInject(),
                    previewViewModel = koinInject(),
                    organizerViewModel = koinInject(),
                    monthFilesViewModel = koinInject(),
                    navigatorViewModel = koinInject(),
                    pdfToolsViewModel = koinInject(),
                    onOpenSettings = { screen = Screen.SETTINGS },
                )
                Screen.SETTINGS -> {
                    val onboarding = koinInject<OnboardingViewModel>()
                    SettingsScreen(
                        viewModel = koinInject(),
                        onBack = { screen = Screen.HOME },
                        onOpenOnboarding = {
                            onboarding.onStart()
                            screen = Screen.ONBOARDING
                        },
                    )
                }
            }
        }
    }
}
