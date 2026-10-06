package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.BackupStats
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.DataMaintenanceRepository
import com.bragadev.fiscal.domain.repository.SettingsRepository
import java.nio.file.Path

class GetBackupStatsUseCase(private val maintenance: DataMaintenanceRepository) {
    suspend operator fun invoke(): BackupStats = maintenance.backupStats()
}

/**
 * Limpa backups antigos de arquivos substituídos. A limpeza automática só roda se o usuário
 * tiver ligado a opção nas Configurações; a limpeza manual é pedida (e confirmada) pelo usuário.
 */
class CleanOldBackupsUseCase(
    private val maintenance: DataMaintenanceRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** Ao abrir o app: só se a opção estiver ligada. Retorna quantos arquivos foram apagados. */
    suspend fun automatic(): Int {
        val settings = settingsRepository.load()
        return if (settings.autoCleanBackups) maintenance.deleteBackupsOlderThan(settings.backupRetentionDays) else 0
    }

    /** Pedido explícito do usuário, já confirmado na tela. */
    suspend fun now(): Int = maintenance.deleteBackupsOlderThan(settingsRepository.settings.value.backupRetentionDays)
}

class ExportDataUseCase(private val maintenance: DataMaintenanceRepository) {
    suspend operator fun invoke(target: Path): Outcome<Unit> = maintenance.exportData(target)
}

class ImportDataUseCase(private val maintenance: DataMaintenanceRepository) {
    suspend operator fun invoke(source: Path): Outcome<Unit> = maintenance.stageImport(source)
}
