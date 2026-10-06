package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.BackupStats
import com.bragadev.fiscal.domain.model.Outcome
import java.nio.file.Path

/** Cuidados com os dados locais do app: backups de substituições e cópia de segurança do banco. */
interface DataMaintenanceRepository {
    suspend fun backupStats(): BackupStats

    /** Apaga só os backups de substituições mais antigos que [days] dias. Retorna quantos foram apagados. */
    suspend fun deleteBackupsOlderThan(days: Int): Int

    /** Grava uma cópia consistente de configurações, histórico e pendências em [target] (nunca sobrescreve). */
    suspend fun exportData(target: Path): Outcome<Unit>

    /** Confere [source] e deixa pronta para substituir os dados na próxima abertura do app. */
    suspend fun stageImport(source: Path): Outcome<Unit>
}
