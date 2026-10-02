package com.bragadev.fiscal.data.settings

import com.bragadev.fiscal.data.database.SettingsDao
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Path

class SettingsRepositoryImpl(
    private val settingsDao: SettingsDao,
    override val suggestedRootPath: Path = Path.of(DEFAULT_SUGGESTED_ROOT),
) : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun load(): AppSettings {
        val values = settingsDao.getAll()
        val defaults = AppSettings()
        val loaded = AppSettings(
            rootPath = values[Keys.ROOT_PATH]?.let(Path::of),
            duplicatePolicy = values[Keys.DUPLICATE_POLICY]
                ?.let { runCatching { DuplicatePolicy.valueOf(it) }.getOrNull() }
                ?: defaults.duplicatePolicy,
            confirmBeforeMove = values[Keys.CONFIRM_MOVE]?.toBooleanStrictOrNull() ?: defaults.confirmBeforeMove,
            confirmBeforeRename = values[Keys.CONFIRM_RENAME]?.toBooleanStrictOrNull() ?: defaults.confirmBeforeRename,
        )
        state.value = loaded
        return loaded
    }

    override suspend fun save(settings: AppSettings) {
        settingsDao.putAll(
            mapOf(
                Keys.ROOT_PATH to settings.rootPath?.toString(),
                Keys.DUPLICATE_POLICY to settings.duplicatePolicy.name,
                Keys.CONFIRM_MOVE to settings.confirmBeforeMove.toString(),
                Keys.CONFIRM_RENAME to settings.confirmBeforeRename.toString(),
            ),
        )
        state.value = settings
    }

    private object Keys {
        const val ROOT_PATH = "root_path"
        const val DUPLICATE_POLICY = "duplicate_policy"
        const val CONFIRM_MOVE = "confirm_before_move"
        const val CONFIRM_RENAME = "confirm_before_rename"
    }

    companion object {
        /** Único caminho absoluto do projeto: apenas uma sugestão inicial, usada se existir. */
        const val DEFAULT_SUGGESTED_ROOT = "D:\\Modelo\\jw\\pendriver"
    }
}
