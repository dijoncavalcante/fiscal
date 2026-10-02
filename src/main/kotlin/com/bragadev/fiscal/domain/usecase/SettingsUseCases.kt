package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveSettingsUseCase(private val settingsRepository: SettingsRepository) {
    operator fun invoke(): StateFlow<AppSettings> = settingsRepository.settings
}

class UpdateSettingsUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(transform: (AppSettings) -> AppSettings) {
        settingsRepository.save(transform(settingsRepository.load()))
    }
}
