package com.bragadev.fiscal.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.bragadev.fiscal.presentation.theme.LocalFiscalColors

/** Cores de situação usadas no mês em edição: liberado/presente, atenção/faltando, bloqueado. Acompanham o tema claro/escuro. */
object StatusColors {
    val Positive: Color @Composable @ReadOnlyComposable get() = LocalFiscalColors.current.positive
    val PositiveBackground: Color @Composable @ReadOnlyComposable get() = LocalFiscalColors.current.positiveBackground
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalFiscalColors.current.warning
    val WarningBackground: Color @Composable @ReadOnlyComposable get() = LocalFiscalColors.current.warningBackground
    val LockedBackground: Color @Composable @ReadOnlyComposable get() = LocalFiscalColors.current.lockedBackground
    val PreviewBackdrop: Color @Composable @ReadOnlyComposable get() = LocalFiscalColors.current.previewBackdrop
}
