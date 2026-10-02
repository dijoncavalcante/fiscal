package com.bragadev.fiscal.presentation.settings

import com.bragadev.fiscal.domain.model.DuplicatePolicy

data class SettingsUiState(
    val rootPath: String = "",
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.ASK,
    val confirmBeforeMove: Boolean = true,
    val confirmBeforeRename: Boolean = true,
    val error: String? = null,
)
