package com.bragadev.fiscal.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.bragadev.fiscal.domain.model.ThemeMode

/** Cores do app que o Material não cobre: situação do mês/arquivo e o fundo atrás das páginas do preview. */
@Immutable
data class FiscalColors(
    val positive: Color,
    val positiveBackground: Color,
    val warning: Color,
    val warningBackground: Color,
    val lockedBackground: Color,
    /** Fundo cinza atrás das páginas do PDF (as páginas continuam brancas, como no papel). */
    val previewBackdrop: Color,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F5FAD),
    secondary = Color(0xFF4A6380),
    secondaryContainer = Color(0xFFD6E4F7),
    primaryContainer = Color(0xFFDCE8FA),
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DC2FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF1F4A80),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB7C8E1),
    secondaryContainer = Color(0xFF364A63),
    onSecondaryContainer = Color(0xFFD6E4F7),
    background = Color(0xFF15181D),
    surface = Color(0xFF1C2026),
    surfaceVariant = Color(0xFF2B3139),
)

private val LightColors = FiscalColors(
    positive = Color(0xFF1B7F3B),
    positiveBackground = Color(0xFFE3F4E8),
    warning = Color(0xFF8A5A00),
    warningBackground = Color(0xFFFFF4D6),
    lockedBackground = Color(0xFFFDE7E7),
    previewBackdrop = Color(0xFFE9ECEF),
)

private val DarkColors = FiscalColors(
    positive = Color(0xFF7DD99A),
    positiveBackground = Color(0xFF173A24),
    warning = Color(0xFFF2C66D),
    warningBackground = Color(0xFF3D3015),
    lockedBackground = Color(0xFF4A2224),
    previewBackdrop = Color(0xFF0F1114),
)

val LocalFiscalColors = staticCompositionLocalOf { LightColors }

@Composable
fun FiscalTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalFiscalColors provides if (dark) DarkColors else LightColors) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}
