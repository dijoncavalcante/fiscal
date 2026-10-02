package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.AppSettings
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    /** Pasta sugerida como origem na primeira execução. Nunca deve ser assumido como existente. */
    val suggestedSourceFolder: Path

    suspend fun load(): AppSettings

    suspend fun save(settings: AppSettings)
}
