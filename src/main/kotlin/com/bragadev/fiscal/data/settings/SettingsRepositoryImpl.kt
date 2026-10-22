package com.bragadev.fiscal.data.settings

import com.bragadev.fiscal.data.database.SettingsDao
import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Path
import java.time.YearMonth

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
            themeMode = values[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            onboardingDone = values[Keys.ONBOARDING_DONE]?.toBooleanStrictOrNull() ?: defaults.onboardingDone,
            windowBounds = values[Keys.WINDOW_BOUNDS]?.let(WindowBounds::decode),
            firstEditableMonth = values[Keys.FIRST_EDITABLE_MONTH]
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                ?: defaults.firstEditableMonth,
            expenseSigners = AppSettings.EXPENSE_ACCOUNTS.mapNotNull { account ->
                values[Keys.expenseSigner(account)]?.trim()?.takeIf { it.isNotEmpty() }?.let { account to it }
            }.toMap(),
            statementSigner = values[Keys.STATEMENT_SIGNER]?.trim()?.takeIf { it.isNotEmpty() },
            expenseExemptTerms = values[Keys.EXPENSE_EXEMPT_TERMS]
                ?.let { text -> text.lines().map(String::trim).filter(String::isNotEmpty) }
                ?: defaults.expenseExemptTerms,
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
                Keys.THEME_MODE to settings.themeMode.name,
                Keys.ONBOARDING_DONE to settings.onboardingDone.toString(),
                Keys.WINDOW_BOUNDS to settings.windowBounds?.encode(),
                Keys.FIRST_EDITABLE_MONTH to settings.firstEditableMonth.toString(),
                Keys.EXPENSE_EXEMPT_TERMS to settings.expenseExemptTerms.joinToString("\n"),
                Keys.STATEMENT_SIGNER to settings.statementSigner,
            ) + AppSettings.EXPENSE_ACCOUNTS.associate { Keys.expenseSigner(it) to settings.expenseSigners[it] },
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
        const val THEME_MODE = "theme_mode"
        const val ONBOARDING_DONE = "onboarding_done"
        const val WINDOW_BOUNDS = "window_bounds"
        const val FIRST_EDITABLE_MONTH = "first_editable_month"

        /** Termos de isenção de cupom, um por linha. */
        const val EXPENSE_EXEMPT_TERMS = "expense_exempt_terms"

        /** Quem assina os extratos bancários das duas contas. */
        const val STATEMENT_SIGNER = "statement_signer"

        /** "expense_signer_congregacao", "expense_signer_manutencao". */
        fun expenseSigner(account: AccountType) = "expense_signer_${account.name.lowercase()}"
    }

    companion object {
        /** Único caminho absoluto do projeto: apenas uma sugestão inicial, usada se existir. */
        const val DEFAULT_SUGGESTED_FOLDER = "D:\\Modelo\\jw\\pendriver"
    }
}
