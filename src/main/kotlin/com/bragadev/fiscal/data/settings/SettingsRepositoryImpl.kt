package com.bragadev.fiscal.data.settings

import com.bragadev.fiscal.data.database.SettingsDao
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.DocumentSort
import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Path

class SettingsRepositoryImpl(
    private val settingsDao: SettingsDao,
    override val suggestedSourceFolder: Path = Path.of(DEFAULT_SUGGESTED_FOLDER),
) : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun load(): AppSettings {
        val values = settingsDao.getAll()
        val defaults = AppSettings()
        val loaded = AppSettings(
            sourceFolder = (values[Keys.SOURCE_FOLDER] ?: values[Keys.LEGACY_ROOT_PATH])?.let(Path::of),
            monthFolder = values[Keys.MONTH_FOLDER]?.let(Path::of),
            monthsRoot = values[Keys.MONTHS_ROOT]?.let(Path::of),
            duplicatePolicy = values[Keys.DUPLICATE_POLICY]
                ?.let { runCatching { DuplicatePolicy.valueOf(it) }.getOrNull() }
                ?: defaults.duplicatePolicy,
            confirmBeforeMove = values[Keys.CONFIRM_MOVE]?.toBooleanStrictOrNull() ?: defaults.confirmBeforeMove,
            confirmBeforeRename = values[Keys.CONFIRM_RENAME]?.toBooleanStrictOrNull() ?: defaults.confirmBeforeRename,
            documentSort = values[Keys.DOCUMENT_SORT]
                ?.let { runCatching { DocumentSort.valueOf(it) }.getOrNull() }
                ?: defaults.documentSort,
            autoCleanBackups = values[Keys.AUTO_CLEAN_BACKUPS]?.toBooleanStrictOrNull() ?: defaults.autoCleanBackups,
            backupRetentionDays = values[Keys.BACKUP_RETENTION_DAYS]?.toIntOrNull() ?: defaults.backupRetentionDays,
        )
        state.value = loaded
        return loaded
    }

    override suspend fun save(settings: AppSettings) {
        settingsDao.putAll(
            mapOf(
                Keys.SOURCE_FOLDER to settings.sourceFolder?.toString(),
                Keys.MONTH_FOLDER to settings.monthFolder?.toString(),
                Keys.MONTHS_ROOT to settings.monthsRoot?.toString(),
                Keys.LEGACY_ROOT_PATH to null,
                Keys.DUPLICATE_POLICY to settings.duplicatePolicy.name,
                Keys.CONFIRM_MOVE to settings.confirmBeforeMove.toString(),
                Keys.CONFIRM_RENAME to settings.confirmBeforeRename.toString(),
                Keys.DOCUMENT_SORT to settings.documentSort.name,
                Keys.AUTO_CLEAN_BACKUPS to settings.autoCleanBackups.toString(),
                Keys.BACKUP_RETENTION_DAYS to settings.backupRetentionDays.toString(),
            ),
        )
        state.value = settings
    }

    private object Keys {
        const val SOURCE_FOLDER = "source_folder"
        const val MONTH_FOLDER = "month_folder"
        const val MONTHS_ROOT = "months_root"
        const val LEGACY_ROOT_PATH = "root_path"
        const val DUPLICATE_POLICY = "duplicate_policy"
        const val CONFIRM_MOVE = "confirm_before_move"
        const val CONFIRM_RENAME = "confirm_before_rename"
        const val DOCUMENT_SORT = "document_sort"
        const val AUTO_CLEAN_BACKUPS = "auto_clean_backups"
        const val BACKUP_RETENTION_DAYS = "backup_retention_days"
    }

    companion object {
        /** Único caminho absoluto do projeto: apenas uma sugestão inicial, usada se existir. */
        const val DEFAULT_SUGGESTED_FOLDER = "D:\\Modelo\\jw\\pendriver"
    }
}
