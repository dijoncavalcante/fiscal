package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.AppSettings
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    /** Valor inicial sugerido para a pasta raiz. Nunca deve ser assumido como existente. */
    val suggestedRootPath: Path

    suspend fun load(): AppSettings

    suspend fun save(settings: AppSettings)
}
