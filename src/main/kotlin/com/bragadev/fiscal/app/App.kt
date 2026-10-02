package com.bragadev.fiscal.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.bragadev.fiscal.presentation.home.HomeScreen
import com.bragadev.fiscal.presentation.settings.SettingsScreen
import org.koin.compose.koinInject

private enum class Screen { HOME, SETTINGS }

private val AppColors = lightColorScheme(
    primary = Color(0xFF1F5FAD),
    secondary = Color(0xFF4A6380),
    secondaryContainer = Color(0xFFD6E4F7),
    primaryContainer = Color(0xFFDCE8FA),
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun App() {
    var screen by remember { mutableStateOf(Screen.HOME) }

    MaterialTheme(colorScheme = AppColors) {
        Surface(color = MaterialTheme.colorScheme.background) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    homeViewModel = koinInject(),
                    previewViewModel = koinInject(),
                    organizerViewModel = koinInject(),
                    onOpenSettings = { screen = Screen.SETTINGS },
                )
                Screen.SETTINGS -> SettingsScreen(
                    viewModel = koinInject(),
                    onBack = { screen = Screen.HOME },
                )
            }
        }
    }
}
