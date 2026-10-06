package com.bragadev.fiscal.presentation.settings

import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.domain.model.ThemeMode
import java.time.YearMonth

data class SettingsUiState(
    val sourceFolder: String = "",
    val monthFolder: String = "",
    val firstEditableMonth: YearMonth = YearMonth.now(),
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.ASK,
    val confirmBeforeMove: Boolean = true,
    val confirmBeforeRename: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val error: String? = null,
)
